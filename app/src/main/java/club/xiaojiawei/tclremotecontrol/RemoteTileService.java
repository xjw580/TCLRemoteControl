package club.xiaojiawei.tclremotecontrol;

import android.app.PendingIntent;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** A launcher shortcut, not a connection toggle or a background controller. */
public final class RemoteTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile tile=getQsTile();
        if(tile==null) return;
        tile.setLabel(getString(R.string.app_name));
        tile.setContentDescription("打开 TCL 遥控器");
        tile.setState(Tile.STATE_INACTIVE);
        if(Build.VERSION.SDK_INT>=29) tile.setSubtitle("打开遥控器");
        tile.updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        if(isLocked()) unlockAndRun(this::openRemote);
        else openRemote();
    }

    private void openRemote() {
        Intent intent=new Intent(this,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(Build.VERSION.SDK_INT>=34) {
            PendingIntent launch=PendingIntent.getActivity(this,0,intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(launch);
        } else {
            openRemoteLegacy(intent);
        }
    }

    // Only used below API 34, where the PendingIntent overload does not exist.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openRemoteLegacy(Intent intent) {
        startActivityAndCollapse(intent);
    }
}
