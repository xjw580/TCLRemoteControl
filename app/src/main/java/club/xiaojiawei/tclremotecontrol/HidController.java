package club.xiaojiawei.tclremotecontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.Arrays;

/** Owns one HID registration; RemoteService controls its lifetime. No network access. */
// MainActivity checks runtime permissions before creating this controller. Binder calls also
// handle SecurityException because permission can be revoked while the app is connected.
@SuppressLint("MissingPermission")
public final class HidController implements AutoCloseable {
    public interface Listener {
        void onStatus(String message, boolean registered, boolean connected);
        void onHost(BluetoothDevice host);
        void onReady();
    }
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private BluetoothDevice pending;
    private boolean registered, connected, closed;
    private byte[] keyboard = HidReports.released(HidReports.KEYBOARD);
    private byte[] consumer = HidReports.released(HidReports.CONSUMER);

    public HidController(Context context, BluetoothAdapter adapter, Listener listener) {
        this.context=context.getApplicationContext(); this.adapter=adapter; this.listener=listener;
    }
    private void status(String text) {
        if (!closed) {
            Log.i("LightRemote", "registered="+registered+" connected="+connected+" "+text);
            listener.onStatus(text, registered, connected);
        }
    }
    private final Runnable profileTimeout = () -> {
        if (!registered) status("手机未提供可用的 HID 服务。可重试；若仍失败，需要更换连接方案。");
    };
    private final Runnable connectTimeout = () -> {
        if (!connected && pending != null) {
            BluetoothDevice old=pending; pending=null;
            try { if (hid != null) hid.disconnect(old); } catch (SecurityException ignored) {}
            status("连接超时。请确认电视开启，或在电视蓝牙设置中主动连接本手机。");
        }
    };
    public void start() {
        if (closed) return;
        status("正在检测手机的蓝牙 HID 能力…");
        try {
            if (!adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)) {
                status("手机系统未开放 HID Device 功能。");
            } else main.postDelayed(profileTimeout, 10000);
        } catch (SecurityException e) { status("缺少附近设备权限，请授权后重试。"); }
    }
    private final BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            main.post(() -> {
                if (closed) { adapter.closeProfileProxy(profile, proxy); return; }
                hid=(BluetoothHidDevice)proxy;
                BluetoothHidDeviceAppSdpSettings settings = new BluetoothHidDeviceAppSdpSettings(
                    context.getString(R.string.app_name), "Keyboard and media remote", "Local Remote", (byte)0,
                    HidReports.descriptor());
                try {
                    boolean accepted=hid.registerApp(settings, null, null, command -> main.post(command), callbacks);
                    if (!accepted) {
                        main.removeCallbacks(profileTimeout);
                        status("HID 注册被系统拒绝。请关闭其他蓝牙键鼠模拟 App 后重试。");
                    }
                } catch (SecurityException | IllegalStateException e) {
                    main.removeCallbacks(profileTimeout);
                    status("无法注册 HID，请检查蓝牙和附近设备权限。");
                }
            });
        }
        @Override public void onServiceDisconnected(int profile) {
            main.post(() -> {
                if (closed) return;
                hid=null; host=null; pending=null; registered=false; connected=false;
                main.removeCallbacks(connectTimeout);
                main.removeCallbacks(profileTimeout);
                status("手机 HID 服务已断开，请重试连接。");
            });
        }
    };
    private final BluetoothHidDevice.Callback callbacks = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean ok) {
            if (closed || hid == null) return;
            registered=ok;
            main.removeCallbacks(profileTimeout);
            if (!ok) {
                connected=false; host=null; pending=null;
                main.removeCallbacks(connectTimeout);
                status("HID 注册已结束。请保持 App 在前台，点“重试 HID”。");
                return;
            }
            status("HID 已注册 · 请配对电视，再点击连接。");
            // A host can reconnect by itself after registration; trust connection callback only.
            try {
                for (BluetoothDevice device: hid.getConnectedDevices()) {
                    onConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTED);
                    break;
                }
            } catch (SecurityException ignored) {}
            // Notify after checking host-initiated reconnection to avoid a duplicate connect request.
            listener.onReady();
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (closed || hid == null || !registered) return;
            if (state == BluetoothProfile.STATE_CONNECTED) {
                if (host != null && !host.equals(device)) {
                    try { hid.disconnect(device); } catch (SecurityException ignored) {}
                    return;
                }
                main.removeCallbacks(connectTimeout);
                host=device; pending=null; connected=true;
                keyboard=HidReports.released(1); consumer=HidReports.released(2);
                release();
                listener.onHost(device);
                status("已连接 · 可以使用下方按键");
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (host != null && !host.equals(device)) return;
                if (pending != null && !pending.equals(device)) return;
                host=null; pending=null; connected=false;
                main.removeCallbacks(connectTimeout);
                status("电视已断开。保持电视开启后重新连接。");
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                status("正在建立 HID 连接…");
            }
        }
        @Override public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            if (closed || hid == null) return;
            try {
                byte[] report=null;
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                    if (id == 1) report=keyboard;
                    if (id == 2) report=consumer;
                } else if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id == 1) {
                    report=new byte[]{0};
                }
                if (report == null) {
                    hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
                } else {
                    if (bufferSize > 0 && bufferSize < report.length) report=Arrays.copyOf(report, bufferSize);
                    hid.replyReport(device, type, id, report);
                }
            } catch (SecurityException ignored) {}
        }
        @Override public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            if (closed || hid == null) return;
            try {
                boolean leds=type == BluetoothHidDevice.REPORT_TYPE_OUTPUT && id == 1;
                hid.reportError(device, leds ? BluetoothHidDevice.ERROR_RSP_SUCCESS
                    : BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);
            } catch (SecurityException ignored) {}
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice device) {
            if (closed) return;
            if (host != null && !host.equals(device)) return;
            if (pending != null && !pending.equals(device)) return;
            main.removeCallbacks(connectTimeout);
            host=null; pending=null; connected=false;
            keyboard=HidReports.released(1); consumer=HidReports.released(2);
            status("电视已解除 HID 连接，请重新配对或连接。");
        }
    };
    public void connect(BluetoothDevice device) {
        if (!registered || hid == null) { status("HID 尚未就绪，请先重试。"); return; }
        if (connected) { status("请先断开当前电视，再切换设备。"); return; }
        if (pending != null) { status("正在连接，请稍候。"); return; }
        try {
            pending=device;
            if (!hid.connect(device)) { pending=null; status("系统拒绝连接，请在电视蓝牙设置中主动连接手机。"); }
            else { status("正在连接电视…"); main.postDelayed(connectTimeout, 15000); }
        } catch (SecurityException e) { pending=null; status("缺少附近设备权限。"); }
    }
    public void disconnect() {
        release();
        try {
            if (hid != null && host != null) hid.disconnect(host);
            else if (hid != null && pending != null) hid.disconnect(pending);
        } catch (SecurityException ignored) {}
        host=null; pending=null; connected=false;
        main.removeCallbacks(connectTimeout);
        status("已断开，可重新选择电视。");
    }
    public boolean isConnecting() { return !closed && pending!=null; }
    public boolean isConnected() { return !closed && registered && connected && host != null; }
    public boolean press(int reportId, int usage) {
        if (!isConnected() || hid == null) return false;
        if (!release()) return false;
        byte[] report=HidReports.pressed(reportId, usage);
        if (reportId == 1) keyboard=report; else consumer=report;
        return send(reportId, report);
    }
    private boolean send(int id, byte[] data) {
        try {
            if (hid != null && host != null && hid.sendReport(host, id, data)) return true;
        } catch (SecurityException | IllegalStateException ignored) {}
        status("按键未发送成功，请重新连接电视。");
        return false;
    }
    public boolean release() {
        keyboard=HidReports.released(1); consumer=HidReports.released(2);
        if (!isConnected() || hid == null) return true;
        boolean a=send(1, keyboard); boolean b=send(2, consumer);
        return a && b;
    }
    @Override public void close() {
        if (closed) return;
        release(); closed=true;
        Log.i("LightRemote", "Closing foreground HID registration; releasing keys and disconnecting host");
        main.removeCallbacks(profileTimeout); main.removeCallbacks(connectTimeout);
        if (hid != null) {
            try {
                if (host != null) hid.disconnect(host);
                else if (pending != null) hid.disconnect(pending);
                hid.unregisterApp();
            } catch (SecurityException | IllegalStateException ignored) {}
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        }
        hid=null; host=null; pending=null; connected=false; registered=false;
    }
}
