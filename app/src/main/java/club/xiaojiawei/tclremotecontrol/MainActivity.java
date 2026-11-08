package club.xiaojiawei.tclremotecontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.StatusBarManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Icon;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.core.app.NotificationManagerCompat;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Bluetooth entry points are guarded by permissions(); revocation is handled by the controller.
@SuppressLint("MissingPermission")
public final class MainActivity extends Activity {
    private static final int BG=0xff101518, CARD=0xff222a2f;
    private static final int TEXT=0xfff0f5fa, MUTED=0xffadc0d1, MINT=0xff8be0c1;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> devices=new ArrayList<>();
    private final List<Button> keys=new ArrayList<>();
    private BluetoothAdapter adapter;
    private RemoteService controller;
    private IrController infrared;
    private boolean infraredMode;
    private RemoteKey heldInfraredKey;
    private LinearLayout bluetoothPanel;
    private Button bluetoothModeButton, infraredModeButton, settingsButton, moreSettings, infraredHelp;
    private TextView settingsTitle;
    private final Runnable repeatInfrared=new Runnable() {
        @Override public void run() {
            if(!foreground || !infraredMode || heldInfraredKey==null) return;
            infrared.send(heldInfraredKey);
            main.postDelayed(this,160);
        }
    };
    private Switch backgroundSwitch;
    private boolean binding, updatingBackgroundSwitch;
    private View statusLight;
    private Dialog settingsDialog;
    private Context dialogContext;
    private Toast feedback;
    private String statusMessage="正在准备…";
    private boolean waitingForConnection;
    private Spinner paired;
    private Button connect, powerKey;
    private LinearLayout connectionPanel;
    private boolean wasConnected;
    private boolean touchClick, receiverRegistered, hidReady, foreground;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=manager==null ? null : manager.getAdapter();
        dialogContext=new ContextThemeWrapper(this,R.style.Theme_TCLRemoteControl_Dialog);
        infrared=new IrController(this,this::setHint);
        infraredMode=getPreferences(MODE_PRIVATE).getBoolean("infraredMode",false) && infrared.isAvailable();
        createUi();
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, filter);
        receiverRegistered=true;
    }
    @Override protected void onResume() {
        super.onResume(); foreground=true; refreshDevices(); ensureController();
        if(infraredMode) showInfraredState();
    }
    @Override protected void onPause() {
        main.removeCallbacksAndMessages(null);
        stopInfraredPress();
        if (controller != null) controller.release();
        super.onPause();
    }
    @Override protected void onStop() {
        foreground=false; closeController(); super.onStop();
    }
    @Override protected void onDestroy() {
        if (receiverRegistered) unregisterReceiver(receiver);
        if(settingsDialog!=null) settingsDialog.dismiss();
        if(infrared!=null) infrared.close();
        if(feedback!=null) feedback.cancel();
        closeController(); super.onDestroy();
    }
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state != BluetoothAdapter.STATE_ON) closeController();
                else if (foreground) ensureController();
            }
            refreshDevices();
        }
    };
    private boolean permissions() {
        return Build.VERSION.SDK_INT < 31 ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED &&
             checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);
    }
    private void enable() {
        if (adapter==null) { showStatus("此设备没有可用蓝牙适配器。",false,false); return; }
        if(infraredMode) return;
        if (!permissions()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE}, 10);
            return;
        }
        if (!adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),11); return;
        }
        if(controller!=null) controller.retry();
        else ensureController();
        refreshDevices();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] names, int[] grants) {
        super.onRequestPermissionsResult(requestCode,names,grants);
        if (requestCode==10) {
            if (permissions()) enable();
            else showStatus("需要“附近设备”权限才能配对和发送按键。",false,false);
        }
        if(requestCode==20) {
            if(Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
                requestBackgroundConnection();
            else setHint("通知权限未授予，后台保持未开启；前台遥控仍可用。");
        }
    }
    private final RemoteService.Listener serviceListener=new RemoteService.Listener() {
        @Override public void onState(String message,boolean ready,boolean connected,boolean keepBackground) {
            updateBackgroundSwitch(keepBackground);
            showStatus(message,ready,connected);
        }
        @Override public void onHostChanged() { refreshDevices(); }
    };
    private final ServiceConnection serviceConnection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            if(!foreground || infraredMode) return;
            controller=((RemoteService.LocalBinder)binder).service();
            controller.attach(serviceListener);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            controller=null;
            showStatus("连接服务已结束，请重新打开应用。",false,false);
        }
        @Override public void onBindingDied(ComponentName name) {
            closeController();
            if(foreground) ensureController();
        }
    };
    private void ensureController() {
        if (!foreground || infraredMode || controller!=null || binding) return;
        if (adapter==null) { showStatus("此设备没有可用蓝牙适配器。",false,false); return; }
        if (!permissions()) { showStatus("请允许附近设备权限。",false,false); return; }
        if (!adapter.isEnabled()) { showStatus("蓝牙未开启，请在连接设置中开启。",false,false); return; }
        binding=bindService(new Intent(this,RemoteService.class),serviceConnection,Context.BIND_AUTO_CREATE);
        if(!binding) showStatus("无法启动连接服务，请重试。",false,false);
    }
    private void closeController() {
        if(controller!=null) controller.detach(serviceListener);
        controller=null;
        if(binding) { unbindService(serviceConnection); binding=false; }
        showStatus("页面连接已暂停。",false,false);
    }
    private void updateBackgroundSwitch(boolean checked) {
        if(backgroundSwitch==null) return;
        updatingBackgroundSwitch=true;
        backgroundSwitch.setChecked(checked);
        updatingBackgroundSwitch=false;
    }
    private void requestBackgroundConnection() {
        if(controller==null || !hidReady) {
            updateBackgroundSwitch(false);
            setHint("请先等待蓝牙就绪，再开启后台保持。"); return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            updateBackgroundSwitch(false);
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20); return;
        }
        if(!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            updateBackgroundSwitch(false);
            new AlertDialog.Builder(dialogContext).setTitle("允许通知")
                .setMessage("后台保持连接需要显示常驻通知，方便随时断开。请在系统设置中允许通知。")
                .setNegativeButton("取消",null)
                .setPositiveButton("去设置",(dialog,which)->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()))).show();
            return;
        }
        controller.setBackgroundEnabled(true);
    }
    private void showStatus(String message,boolean ready,boolean connected) {
        if(infraredMode) { showInfraredState(); return; }
        hidReady=ready;
        statusMessage=message;
        GradientDrawable light=new GradientDrawable();
        light.setShape(GradientDrawable.OVAL);
        light.setColor(connected ? 0xff56d994 : ready ? 0xffefba52 : 0xff68747d);
        statusLight.setBackground(light);
        statusLight.setContentDescription("连接状态："+message);
        statusLight.setTooltipText(message);

        for (Button key:keys) { key.setEnabled(connected); key.setAlpha(connected ? 1f : .6f); key.setTooltipText(((RemoteKey)key.getTag()).label); }
        updateConnectButton();
        if (connected && !wasConnected) {
            if(settingsDialog!=null) settingsDialog.dismiss();
            if(waitingForConnection) setHint("已连接电视");
        }
        if(waitingForConnection && controller!=null && !controller.isConnecting()) {
            waitingForConnection=false;
            if(!connected) setHint(message);
        }
        wasConnected=connected;
        if (connected) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private String deviceName(BluetoothDevice device) {
        String name=device.getName(); return name==null || name.trim().isEmpty() ? "未命名设备" : name;
    }
    private void updateConnectButton() {
        if(infraredMode) return;
        int index=paired.getSelectedItemPosition()-1;
        boolean selected=index>=0 && index<devices.size();
        boolean connected=controller!=null && controller.isConnected();
        boolean connecting=controller!=null && controller.isConnecting();
        String action;
        if(adapter==null) action="设备不支持蓝牙";
        else if(!permissions()) action="允许附近设备";
        else if(!adapter.isEnabled()) action="开启蓝牙";
        else if(connected) action="断开连接";
        else if(connecting) action="连接中…";
        else if(!hidReady) action="重试连接";
        else if(devices.isEmpty()) action="配对电视";
        else action=selected ? "连接电视" : "选择电视";
        connect.setText(action);
        connect.setEnabled(adapter!=null && !connecting);
        connect.setAlpha(connect.isEnabled() ? 1f : .55f);
        paired.setEnabled(!connected && !connecting);
        paired.setAlpha(paired.isEnabled() ? 1f : .7f);
        backgroundSwitch.setEnabled(hidReady || backgroundSwitch.isChecked());
        backgroundSwitch.setAlpha(backgroundSwitch.isEnabled() ? 1f : .55f);
        if(powerKey!=null) {
            powerKey.setEnabled(hidReady && !connecting && (connected || selected));
            powerKey.setAlpha(powerKey.isEnabled() ? 1f : .6f);
        }
    }
    private void refreshDevices() {
        if (paired==null || adapter==null || !permissions()) return;
        try {
            String selected=getPreferences(MODE_PRIVATE).getString("host","");
            devices.clear(); devices.addAll(adapter.getBondedDevices());
            Collections.sort(devices,(a,b)->deviceName(a).compareToIgnoreCase(deviceName(b)));
            List<String> labels=new ArrayList<>(); int pick=0;
            labels.add(devices.isEmpty() ? "暂无已配对电视" : "选择已配对电视");
            for (int i=0;i<devices.size();i++) {
                BluetoothDevice device=devices.get(i);
                String address=device.getAddress();
                labels.add(deviceName(device)+" · "+address.substring(Math.max(0,address.length()-5)));
                if (address.equals(selected)) pick=i+1;
            }
            ArrayAdapter<String> options=new ArrayAdapter<String>(dialogContext,android.R.layout.simple_spinner_item,labels) {
                @Override public View getView(int position,View recycled,android.view.ViewGroup parent) {
                    TextView view=(TextView)super.getView(position,recycled,parent);
                    view.setTextColor(TEXT); view.setTextSize(15); view.setSingleLine(true);
                    view.setEllipsize(android.text.TextUtils.TruncateAt.END); return view;
                }
            };
            options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            paired.setAdapter(options); paired.setSelection(pick);
            updateConnectButton();

        } catch (SecurityException e) { showStatus("请重新授予附近设备权限。",false,false); }
    }
    private void discoverable() {
        if (!permissions() || adapter==null || !adapter.isEnabled()) { enable(); return; }
        Intent intent=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
        startActivityForResult(intent,12);
    }
    private void createUi() {
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root=column(); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18),dp(8),dp(18),dp(16)); scroll.addView(root);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, windowInsets) -> {
            Insets safe=windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        LinearLayout header=row(); root.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        statusLight=new View(this);
        statusLight.setFocusable(true);
        statusLight.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout.LayoutParams indicator=new LinearLayout.LayoutParams(dp(12),dp(12));
        indicator.rightMargin=dp(10); header.addView(statusLight,indicator);
        TextView title=text(getString(R.string.tv_model),23,TEXT); title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        settingsButton=button("连接设置",CARD,TEXT); header.addView(settingsButton,new LinearLayout.LayoutParams(dp(94),dp(48)));
        createConnectionSettings();
        settingsButton.setOnClickListener(v->openConnectionSettings());
        RemoteLayout body=new RemoteLayout(this,id->{
            RemoteKey binding=RemoteKey.fromId(id);
            Button key=remote(binding);
            if(binding==RemoteKey.POWER) powerKey=key;
            return key;
        });
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2); bp.topMargin=dp(12); root.addView(body,bp);
        setContentView(scroll); ViewCompat.requestApplyInsets(scroll);
        updateModeControls();
        showStatus("正在准备…",false,false);
    }
    private void createConnectionSettings() {
        settingsDialog=new Dialog(this);
        settingsDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        ScrollView sheet=new ScrollView(this);
        sheet.setFillViewport(true);
        connectionPanel=column();
        connectionPanel.setPadding(dp(20),dp(12),dp(20),dp(20));
        connectionPanel.setBackground(shape(CARD,24));
        sheet.addView(connectionPanel);
        settingsDialog.setContentView(sheet);
        android.view.Window window=settingsDialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.5f);
        }
        LinearLayout heading=row(); connectionPanel.addView(heading);
        settingsTitle=text("遥控设置",20,TEXT); settingsTitle.setTypeface(null,Typeface.BOLD);
        heading.addView(settingsTitle,new LinearLayout.LayoutParams(0,-2,1));
        moreSettings=button("更多",CARD,MUTED); heading.addView(moreSettings,new LinearLayout.LayoutParams(dp(56),dp(48)));
        moreSettings.setContentDescription("更多连接选项"); moreSettings.setOnClickListener(this::showConnectionMenu);
        Button close=button("×",CARD,TEXT); close.setTextSize(26);
        close.setContentDescription("关闭连接设置"); heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        close.setOnClickListener(v->settingsDialog.dismiss());

        LinearLayout modes=row();
        LinearLayout.LayoutParams modeLayout=new LinearLayout.LayoutParams(-1,dp(56)); modeLayout.topMargin=dp(8);
        connectionPanel.addView(modes,modeLayout);
        bluetoothModeButton=button("蓝牙",BG,TEXT);
        infraredModeButton=button("红外",BG,TEXT);
        for(Button mode:new Button[]{bluetoothModeButton,infraredModeButton}) {
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(48),1); cell.setMargins(dp(3),0,dp(3),0); modes.addView(mode,cell);
        }
        bluetoothModeButton.setOnClickListener(v->selectInfrared(false));
        infraredModeButton.setOnClickListener(v->selectInfrared(true));
        bluetoothPanel=column(); connectionPanel.addView(bluetoothPanel,new LinearLayout.LayoutParams(-1,-2));
        infraredHelp=button("红外使用说明  ⓘ",CARD,MUTED);
        connectionPanel.addView(infraredHelp,new LinearLayout.LayoutParams(-1,dp(56)));
        infraredHelp.setOnClickListener(v->new AlertDialog.Builder(dialogContext).setTitle("红外遥控")
            .setMessage("将手机红外发射口朝向电视，保持无遮挡。本机测试需将手机背面朝向电视。\n\n红外无需蓝牙连接；指示灯表示手机红外可用，不代表电视已收到信号。\n\n支持全部遥控按键，包括彩色快捷键、哔哩哔哩和芒果 TV。待机开机需电视保持通电。")
            .setPositiveButton("知道了",null).show());
        paired=new Spinner(dialogContext,Spinner.MODE_DIALOG);
        paired.setPrompt("选择电视"); paired.setContentDescription("选择已配对电视");
        paired.setBackgroundTintList(ColorStateList.valueOf(MUTED));
        LinearLayout.LayoutParams deviceLayout=new LinearLayout.LayoutParams(-1,dp(56));
        deviceLayout.setMargins(0,dp(8),0,dp(12)); bluetoothPanel.addView(paired,deviceLayout);
        paired.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                int index=position-1;
                if(index>=0 && index<devices.size()) getPreferences(MODE_PRIVATE).edit().putString("host",devices.get(index).getAddress()).apply();
                updateConnectButton();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateConnectButton(); }
        });
        connect=button("连接电视",MINT,BG);
        bluetoothPanel.addView(connect,new LinearLayout.LayoutParams(-1,dp(52)));
        connect.setOnClickListener(v->{
            if(!permissions() || adapter==null || !adapter.isEnabled() || !hidReady || controller==null) {
                enable(); return;
            }
            if(controller.isConnected()) { controller.disconnect(); setHint("已断开连接"); }
            else if(devices.isEmpty()) showPairingHelp();
            else if(paired.getSelectedItemPosition()==0) paired.performClick();
            else connectSelected();
        });
        LinearLayout background=row();
        LinearLayout.LayoutParams backgroundLayout=new LinearLayout.LayoutParams(-1,dp(64));
        backgroundLayout.topMargin=dp(8); bluetoothPanel.addView(background,backgroundLayout);
        backgroundSwitch=new Switch(this);
        backgroundSwitch.setText("后台保持连接"); backgroundSwitch.setTextColor(TEXT); backgroundSwitch.setTextSize(15);
        backgroundSwitch.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{MINT,MUTED}));
        backgroundSwitch.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff426e63,0xff465159}));
        updateBackgroundSwitch(getPreferences(MODE_PRIVATE).getBoolean("keepBackground",false));
        background.addView(backgroundSwitch,new LinearLayout.LayoutParams(0,dp(48),1));
        backgroundSwitch.setOnCheckedChangeListener((button,checked)->{
            if(updatingBackgroundSwitch) return;
            if(checked) requestBackgroundConnection();
            else if(controller!=null) controller.setBackgroundEnabled(false);
            else getPreferences(MODE_PRIVATE).edit().putBoolean("keepBackground",false).apply();
        });
        Button info=button("ⓘ",CARD,MUTED); info.setTextSize(21);
        info.setContentDescription("后台保持连接说明"); info.setTooltipText("后台保持连接说明");
        background.addView(info,new LinearLayout.LayoutParams(dp(48),dp(48)));
        info.setOnClickListener(v->new AlertDialog.Builder(dialogContext).setTitle("后台保持连接")
            .setMessage("开启后，切换应用或锁屏仍保持蓝牙连接。通知栏可随时断开。\n\n关闭后，离开页面会断开，返回时自动尝试重连。后台保持会占用少量系统资源，实际耗电取决于手机系统。")
            .setPositiveButton("知道了",null).show());
        View divider=new View(this); divider.setBackgroundColor(0xff3b464e);
        bluetoothPanel.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        Button pair=button("＋  配对新电视",CARD,MINT);
        LinearLayout.LayoutParams pairLayout=new LinearLayout.LayoutParams(-1,dp(52)); pairLayout.topMargin=dp(8);
        bluetoothPanel.addView(pair,pairLayout); pair.setOnClickListener(v->showPairingHelp());
        Button addTile=button("添加快捷磁贴",CARD,MUTED);
        LinearLayout.LayoutParams tileLayout=new LinearLayout.LayoutParams(-1,dp(52)); tileLayout.topMargin=dp(8);
        connectionPanel.addView(addTile,tileLayout);
        addTile.setOnClickListener(v->requestRemoteTile());
    }
    private void requestRemoteTile() {
        if(Build.VERSION.SDK_INT<33) { showTileHelp(); return; }
        StatusBarManager statusBar=getSystemService(StatusBarManager.class);
        if(statusBar==null) { showTileHelp(); return; }
        settingsDialog.dismiss();
        try {
            statusBar.requestAddTileService(new ComponentName(this,RemoteTileService.class),
                getString(R.string.app_name),Icon.createWithResource(this,R.drawable.ic_qs_remote),
                getMainExecutor(),result->{
                    if(isFinishing() || isDestroyed()) return;
                    if(result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)
                        setHint("已添加到快捷设置");
                    else if(result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                        setHint("快捷磁贴已添加，可在下拉面板中调整位置");
                    else if(result!=StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED)
                        showTileHelp();
                });
        } catch(IllegalArgumentException | IllegalStateException | SecurityException exception) {
            showTileHelp();
        }
    }
    private void showTileHelp() {
        new AlertDialog.Builder(dialogContext).setTitle("添加快捷磁贴")
            .setMessage("下拉并展开手机快捷设置（控制中心），点击编辑或“＋”，找到“TCL遥控器”并添加。\n\n以后点击磁贴即可打开遥控器，继续使用上次选择的蓝牙或红外模式。")
            .setPositiveButton("知道了",null).show();
    }
    private void openConnectionSettings() {
        refreshDevices();
        updateConnectButton();
        updateModeControls();
        settingsDialog.show();
        sizeSettingsDialog();
    }
    private void sizeSettingsDialog() {
        android.view.Window window=settingsDialog.getWindow();
        if(window!=null) {
            // Keep the sheet scrollable on small screens, landscape, and large font settings.
            android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
            int width=Math.min(metrics.widthPixels,dp(480));
            connectionPanel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            window.setLayout(width,Math.min(connectionPanel.getMeasuredHeight(),(int)(metrics.heightPixels*.8f)));
        }
    }
    private void updateModeControls() {
        settingsButton.setText(infraredMode ? "红外设置" : "连接设置");
        bluetoothPanel.setVisibility(infraredMode ? View.GONE : View.VISIBLE);
        moreSettings.setVisibility(infraredMode ? View.GONE : View.VISIBLE);
        infraredHelp.setVisibility(infraredMode ? View.VISIBLE : View.GONE);
        bluetoothModeButton.setSelected(!infraredMode); infraredModeButton.setSelected(infraredMode);
        bluetoothModeButton.setBackgroundTintList(ColorStateList.valueOf(infraredMode ? BG : MINT));
        infraredModeButton.setBackgroundTintList(ColorStateList.valueOf(infraredMode ? MINT : BG));
        bluetoothModeButton.setTextColor(infraredMode ? TEXT : BG);
        infraredModeButton.setTextColor(infraredMode ? BG : TEXT);
    }
    private void selectInfrared(boolean enabled) {
        if(enabled && !infrared.isAvailable()) { setHint("此手机没有可用的红外发射器"); return; }
        if(infraredMode==enabled) return;
        stopInfraredPress();
        if(controller!=null) controller.release();
        infraredMode=enabled;
        getPreferences(MODE_PRIVATE).edit().putBoolean("infraredMode",enabled).apply();
        waitingForConnection=false; wasConnected=false;
        if(enabled) {
            if(controller!=null) controller.suspendForInfrared();
            closeController();
            showInfraredState();
            setHint("已切换红外，请将发射口对准电视");
        } else {
            showStatus("正在准备蓝牙…",false,false);
            refreshDevices(); ensureController();
        }
        updateModeControls(); sizeSettingsDialog();
    }
    private void showInfraredState() {
        boolean ready=infrared.isAvailable();
        GradientDrawable light=new GradientDrawable(); light.setShape(GradientDrawable.OVAL);
        light.setColor(ready ? 0xffefba52 : 0xff68747d); statusLight.setBackground(light);
        statusLight.setContentDescription(ready ? "红外已就绪，需对准电视" : "红外不可用");
        statusLight.setTooltipText(ready ? "红外已就绪，需对准电视" : "红外不可用");
        for(int i=0;i<keys.size();i++) {
            Button key=keys.get(i);
            RemoteKey binding=(RemoteKey)key.getTag();
            boolean supported=IrSignal.command(binding)>=0;
            key.setEnabled(ready && supported); key.setAlpha(ready && supported ? 1f : .35f);
            key.setTooltipText(supported ? binding.label : "红外未适配，请切换蓝牙");
        }
        if(ready && foreground) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void stopInfraredPress() {
        heldInfraredKey=null; main.removeCallbacks(repeatInfrared);
        if(infrared!=null) infrared.cancel();
    }
    private void beginInfraredPress(RemoteKey key,View view,boolean repeat) {
        stopInfraredPress();
        if(infrared.send(key)) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if(repeat && IrSignal.repeats(key)) {
            heldInfraredKey=key; main.postDelayed(repeatInfrared,450);
        }
    }
    private void showConnectionMenu(View anchor) {
        PopupMenu menu=new PopupMenu(dialogContext,anchor);
        menu.getMenu().add(0,1,0,"重试连接");
        menu.getMenu().add(0,2,1,"重新配对上次电视");
        menu.getMenu().add(0,3,2,"手机蓝牙设置");
        menu.getMenu().add(0,4,3,"连接帮助");
        menu.setOnMenuItemClickListener(item->{
            switch(item.getItemId()) {
                case 1: enable(); setHint("正在重新准备连接"); return true;
                case 2: repairLastHost(); return true;
                case 3: startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return true;
                case 4: new AlertDialog.Builder(dialogContext).setTitle("连接帮助")
                    .setMessage("绿灯表示已连接，黄灯表示可连接，灰灯表示未就绪。长按状态灯可查看详情。\n\n连接失败时，请确认电视开机、两端蓝牙开启，并选择正确的电视。待机后可能需要用原装遥控器开机。\n\n当前状态："+statusMessage)
                    .setPositiveButton("知道了",null).show(); return true;
                default: return false;
            }
        });
        menu.show();
    }
    private void showPairingHelp() {
        if(!permissions() || adapter==null || !adapter.isEnabled()) { enable(); return; }
        String name=adapter.getName();
        new AlertDialog.Builder(dialogContext).setTitle("配对新电视")
            .setMessage("1. 点击下方按钮，允许手机被发现。\n\n2. 在电视的蓝牙设置中搜索并选择手机「"+(name==null ? Build.MODEL : name)+"」。\n\n3. 核对两端配对码并确认，回到这里选择电视连接。")
            .setNegativeButton("取消",null).setPositiveButton("让电视发现手机",(dialog,which)->discoverable()).show();
    }
    private void setHint(String message) {
        if(message.isEmpty() || isFinishing() || isDestroyed()) return;
        if(feedback!=null) feedback.cancel();
        feedback=Toast.makeText(this,message,Toast.LENGTH_LONG);
        feedback.show();
    }
    private void connectSelected() {
        if(controller==null) { enable(); return; }
        int index=paired.getSelectedItemPosition()-1;
        if(index>=0 && index<devices.size()) {
            waitingForConnection=true;
            controller.connect(devices.get(index));
        }
        else openConnectionSettings();
    }
    private void repairLastHost() {
        String address=getPreferences(MODE_PRIVATE).getString("host", "");
        if(!BluetoothAdapter.checkBluetoothAddress(address)) {
            setHint("还没有保存过电视，请先使用“配对新电视”。"); return;
        }
        if(!hidReady || !permissions()) { enable(); setHint("蓝牙正在准备，请稍后再次点击重新配对。"); return; }
        try {
            BluetoothDevice device=adapter.getRemoteDevice(address);
            if(device.getBondState()==BluetoothDevice.BOND_BONDED) {
                new AlertDialog.Builder(dialogContext).setTitle("电视已经配对")
                    .setMessage("如果只有新增按键无响应，请先在手机和电视的蓝牙设置中取消双方旧配对，再回来重新配对。请保留原装遥控器的配对。")
                    .setNegativeButton("关闭",null)
                    .setPositiveButton("手机蓝牙设置",(dialog,which)->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))).show();
            } else if(device.getBondState()==BluetoothDevice.BOND_BONDING) {
                setHint("正在配对，请确认两端显示的配对码。");
            } else {
                setHint(device.createBond() ? "已请求配对，请确认两端显示的配对码。" : "无法发起配对，请检查电视蓝牙是否开启。");
            }
        } catch(SecurityException | IllegalArgumentException error) {
            setHint("无法发起配对，请检查蓝牙权限或重新选择电视。");
        }
    }
    // Native Button already implements performClick. Touch calls it on ACTION_UP and the
    // click listener separately sends short presses for accessibility/keyboard activation.
    @SuppressLint("ClickableViewAccessibility")
    private Button remote(RemoteKey binding) {
        int report=binding.report, usage=binding.usage;
        Button key=new RemoteButton(this,binding.id,binding.label);
        keys.add(key); key.setTag(binding);
        key.setOnTouchListener((v,event)->{
            if (!v.isEnabled()) return false;
            if(infraredMode) {
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.getParent().requestDisallowInterceptTouchEvent(true); v.setPressed(true);
                        beginInfraredPress(binding,v,true); return true;
                    case MotionEvent.ACTION_MOVE:
                        if(event.getX()<0 || event.getY()<0 || event.getX()>v.getWidth() || event.getY()>v.getHeight()) {
                            v.setPressed(false); stopInfraredPress();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_POINTER_DOWN:
                        v.setPressed(false); stopInfraredPress(); v.getParent().requestDisallowInterceptTouchEvent(false);
                        if(event.getActionMasked()==MotionEvent.ACTION_UP) { touchClick=true; v.performClick(); touchClick=false; }
                        return true;
                    default: return true;
                }
            }
            if(binding==RemoteKey.POWER && (controller==null || !controller.isConnected())) return false;
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    main.removeCallbacksAndMessages(null);
                    v.getParent().requestDisallowInterceptTouchEvent(true); v.setPressed(true);
                    if (controller!=null && controller.press(report,usage)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getX()<0 || event.getY()<0 || event.getX()>v.getWidth() || event.getY()>v.getHeight()) {
                        v.setPressed(false); if (controller!=null) controller.release();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_POINTER_DOWN:
                    v.setPressed(false); if (controller!=null) controller.release();
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (event.getActionMasked()==MotionEvent.ACTION_UP) { touchClick=true; v.performClick(); touchClick=false; }
                    return true;
                default: return true;
            }
        });
        key.setOnClickListener(v->{
            if(infraredMode) { if(!touchClick) beginInfraredPress(binding,v,false); return; }
            if(binding==RemoteKey.POWER && (controller==null || !controller.isConnected())) {
                connectSelected();
                setHint("连接后请再按一次电源键。");
                return;
            }
            if (touchClick || controller==null) {
                return;
            }
            main.removeCallbacksAndMessages(null);
            if (controller.press(report,usage)) main.postDelayed(()->{ if(controller!=null) controller.release(); },90);
        });
        return key;
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER); l.setMotionEventSplittingEnabled(false); return l; }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(3),1f); return t; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable s=new GradientDrawable(); s.setColor(color); s.setCornerRadius(dp(radius)); return s; }
    private Button button(String title,int background,int foreground) {
        Button b=new Button(this); b.setText(title); b.setTextColor(foreground); b.setTextSize(14); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(6),0,dp(6),0);
        StateListDrawable states=new StateListDrawable(); states.addState(new int[]{android.R.attr.state_pressed},shape(0xff4b756e,16)); states.addState(new int[]{},shape(background,16));
        b.setBackground(states); b.setStateListAnimator(null); b.setElevation(0); return b;
    }
}
