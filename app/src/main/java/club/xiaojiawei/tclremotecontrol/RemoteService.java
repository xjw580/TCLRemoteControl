package club.xiaojiawei.tclremotecontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** One HID owner shared by the visible Activity and optional connected-device foreground service. */
@SuppressLint("MissingPermission") // Checked at entry; HidController also handles revocation.
public final class RemoteService extends Service implements HidController.Listener {
    private static final String CHANNEL="remote_connection";
    private static final int NOTIFICATION_ID=1;
    private static final String ENABLE="club.xiaojiawei.tclremotecontrol.KEEP_CONNECTION";
    private static final String DISCONNECT="club.xiaojiawei.tclremotecontrol.DISCONNECT";
    public interface Listener {
        void onState(String message,boolean ready,boolean connected,boolean keepBackground);
        void onHostChanged();
    }
    public final class LocalBinder extends Binder {
        public RemoteService service() { return RemoteService.this; }
    }
    private final IBinder binder=new LocalBinder();
    private final Set<Listener> listeners=new LinkedHashSet<>();
    private BluetoothAdapter adapter;
    private HidController hid;
    private SharedPreferences preferences;
    private boolean foregroundService, autoAttempted, ready, connected, destroyed;
    private String message="正在准备…", lastNotification="";

    @Override public void onCreate() {
        super.onCreate();
        preferences=getSharedPreferences("MainActivity",MODE_PRIVATE);
        BluetoothManager manager=getSystemService(BluetoothManager.class);
        adapter=manager==null ? null : manager.getAdapter();
        NotificationChannel channel=new NotificationChannel(CHANNEL,"电视连接",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("后台保持电视蓝牙连接，可随时断开");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(bluetoothReceiver,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(bluetoothReceiver,filter);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && DISCONNECT.equals(intent.getAction())) {
            disconnect();
        } else if(intent!=null && ENABLE.equals(intent.getAction())) {
            if(!permissions() || adapter==null || !adapter.isEnabled()
                || !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                setBackgroundEnabled(false);
                onStatus("无法保持后台连接，请检查蓝牙和通知权限。",ready,connected);
            } else {
                try {
                    if(Build.VERSION.SDK_INT>=29)
                        startForeground(NOTIFICATION_ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                    else startForeground(NOTIFICATION_ID,notification());
                    foregroundService=true;
                    preferences.edit().putBoolean("keepBackground",true).apply();
                    lastNotification="";
                    ensureHid();
                    notifyState();
                } catch(SecurityException | IllegalStateException error) {
                    setBackgroundEnabled(false);
                    onStatus("后台服务启动失败，请保持页面开启后重试。",ready,connected);
                }
            }
        }
        // Never restart invisibly after process death, force-stop, or reboot.
        return START_NOT_STICKY;
    }
    public void attach(Listener listener) {
        boolean wasHidden=listeners.isEmpty();
        listeners.add(listener);
        if(wasHidden && !isConnected() && (hid==null || !hid.isConnecting())) autoAttempted=false;
        notifyState();
        if(isBackgroundEnabled() && preferences.getBoolean("autoConnect",true)) setBackgroundEnabled(true);
        ensureHid();
        autoConnect();
    }
    public void detach(Listener listener) {
        listeners.remove(listener);
        release();
        if(listeners.isEmpty() && !foregroundService) closeHid();
    }
    public boolean isBackgroundEnabled() { return preferences.getBoolean("keepBackground",false); }
    public void setBackgroundEnabled(boolean enabled) {
        if(enabled) {
            if(foregroundService) { notifyState(); return; }
            try {
                ContextCompat.startForegroundService(this,new Intent(this,RemoteService.class).setAction(ENABLE));
            } catch(SecurityException | IllegalStateException error) {
                preferences.edit().putBoolean("keepBackground",false).apply();
                onStatus("无法启动后台保持，请回到应用重试。",ready,connected);
            }
        } else {
            preferences.edit().putBoolean("keepBackground",false).apply();
            foregroundService=false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            if(listeners.isEmpty()) closeHid();
            notifyState();
        }
    }
    private boolean permissions() {
        return Build.VERSION.SDK_INT<31 ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);
    }
    public void retry() {
        closeHid();
        ensureHid();
    }
    private void ensureHid() {
        if(destroyed || hid!=null || (listeners.isEmpty() && !foregroundService)) return;
        if(adapter==null || !permissions() || !adapter.isEnabled()) {
            onStatus("请开启蓝牙并允许附近设备权限。",false,false); return;
        }
        autoAttempted=false;
        hid=new HidController(this,adapter,this);
        hid.start();
    }
    private void closeHid() {
        if(hid!=null) { hid.close(); hid=null; }
        onStatus("连接已暂停，返回页面后可自动重连。",false,false);
    }
    private final BroadcastReceiver bluetoothReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR)!=BluetoothAdapter.STATE_ON) {
                setBackgroundEnabled(false);
                closeHid();
            } else ensureHid();
        }
    };
    @Override public void onStatus(String text,boolean registered,boolean isConnected) {
        message=text; ready=registered; connected=isConnected;
        notifyState();
    }
    @Override public void onHost(BluetoothDevice host) {
        preferences.edit().putString("host",host.getAddress()).apply();
        for(Listener listener:new ArrayList<>(listeners)) listener.onHostChanged();
    }
    @Override public void onReady() { autoConnect(); }
    private void autoConnect() {
        if(hid==null || !ready || autoAttempted || (listeners.isEmpty() && !foregroundService)
            || !preferences.getBoolean("autoConnect",true)) return;
        if(hid.isConnected() || hid.isConnecting()) { autoAttempted=true; return; }
        String address=preferences.getString("host","");
        if(!BluetoothAdapter.checkBluetoothAddress(address) || !permissions()) return;
        try {
            for(BluetoothDevice device:adapter.getBondedDevices()) {
                if(!address.equals(device.getAddress())) continue;
                autoAttempted=true;
                Log.i("LightRemote","Auto-connecting saved TV from remote service");
                hid.connect(device);
                return;
            }
        } catch(SecurityException error) { onStatus("请重新授予附近设备权限。",false,false); }
    }
    public void connect(BluetoothDevice device) {
        autoAttempted=true;
        preferences.edit().putBoolean("autoConnect",true).apply();
        if(isBackgroundEnabled()) setBackgroundEnabled(true);
        if(hid!=null) hid.connect(device);
    }
    public void disconnect() {
        autoAttempted=true;
        preferences.edit().putBoolean("autoConnect",false).apply();
        if(hid!=null) hid.disconnect();
        setBackgroundEnabled(false);
    }
    // Switching transport stops Bluetooth work but keeps the user's background preference.
    public void suspendForInfrared() {
        foregroundService=false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        closeHid();
    }
    public boolean isConnecting() { return hid!=null && hid.isConnecting(); }
    public boolean isConnected() { return hid!=null && hid.isConnected(); }
    public boolean press(int report,int usage) { return hid!=null && hid.press(report,usage); }
    public boolean release() { return hid==null || hid.release(); }
    private void notifyState() {
        for(Listener listener:new ArrayList<>(listeners))
            listener.onState(message,ready,connected,isBackgroundEnabled());
        if(foregroundService && !message.equals(lastNotification)) {
            lastNotification=message;
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification());
        }
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,
            new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent disconnect=PendingIntent.getService(this,1,
            new Intent(this,RemoteService.class).setAction(DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle(connected ? "已连接 · "+getString(R.string.tv_model) : getString(R.string.app_name))
            .setContentText(connected ? "后台保持连接 · 点此返回遥控器" : message)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_stat_remote,"断开",disconnect).build();
    }
    @Override public boolean onUnbind(Intent intent) {
        // The Activity removes its listener before unbinding; no listener is retained while the Activity is stopped.
        if(!foregroundService) { closeHid(); stopSelf(); }
        return false;
    }
    @Override public void onDestroy() {
        destroyed=true;
        unregisterReceiver(bluetoothReceiver);
        listeners.clear();
        foregroundService=false;
        closeHid();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
