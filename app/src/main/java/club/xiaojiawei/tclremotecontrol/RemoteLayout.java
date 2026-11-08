package club.xiaojiawei.tclremotecontrol;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

/** Native, accessible buttons placed in the same order as the physical TCL remote. */
@SuppressLint("ViewConstructor") // Created programmatically with a required key factory.
public final class RemoteLayout extends FrameLayout {
    public interface KeyFactory { Button create(String id); }
    private final List<Placement> placements = new ArrayList<>();
    private static final class Placement {
        final View view;
        final float x, y, width, height;
        Placement(View view, float x, float y, float width, float height) {
            this.view=view; this.x=x; this.y=y; this.width=width; this.height=height;
        }
    }

    public RemoteLayout(Context context, KeyFactory keys) {
        super(context);
        setMotionEventSplittingEnabled(false);
        GradientDrawable shell = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff191e21, 0xff22282b, 0xff171c1f});
        shell.setCornerRadius(dp(30));
        shell.setStroke(dp(1), 0xff333a3e);
        setBackground(shell);
        setElevation(dp(8));

        place(keys.create("mute"), 28, 26, 62, 56);
        place(keys.create("power"), 119, 26, 62, 56);
        place(keys.create("input"), 210, 26, 62, 56);
        place(keys.create("red"), 28, 104, 62, 56);
        place(keys.create("green"), 119, 104, 62, 56);
        place(keys.create("blue"), 210, 104, 62, 56);

        View ring = new View(context);
        GradientDrawable ringShape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xff384752, 0xff293740});
        ringShape.setShape(GradientDrawable.OVAL);
        ringShape.setStroke(dp(1), 0xff586a78);
        ring.setBackground(ringShape);
        place(ring, 33, 190, 234, 234);
        place(keys.create("up"), 119, 194, 62, 62);
        place(keys.create("left"), 37, 276, 62, 62);
        place(keys.create("ok"), 101, 258, 98, 98);
        place(keys.create("right"), 201, 276, 62, 62);
        place(keys.create("down"), 119, 358, 62, 62);

        View volumeTrack = new View(context);
        GradientDrawable volumeShape = new GradientDrawable();
        volumeShape.setColor(0xff465762);
        volumeShape.setCornerRadius(dp(28));
        volumeShape.setStroke(dp(1), 0xff657986);
        volumeTrack.setBackground(volumeShape);
        place(volumeTrack, 119, 452, 62, 136);
        place(keys.create("back"), 28, 452, 62, 58);
        place(keys.create("volume_up"), 119, 452, 62, 58);
        place(keys.create("home"), 210, 452, 62, 58);
        place(keys.create("menu"), 28, 530, 62, 58);
        place(keys.create("volume_down"), 119, 530, 62, 58);
        place(keys.create("settings"), 210, 530, 62, 58);
        place(keys.create("bilibili"), 24, 614, 120, 56);
        place(keys.create("mango"), 156, 614, 120, 56);

    }

    private void place(View view, float x, float y, float width, float height) {
        placements.add(new Placement(view,x,y,width,height));
        addView(view);
    }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int availableWidth = MeasureSpec.getSize(widthSpec);
        int usableHeight = Math.max(dp(610), getResources().getDisplayMetrics().heightPixels - dp(165));
        int width = Math.min(availableWidth, Math.max(dp(258),Math.min(dp(330), Math.round(usableHeight*300f/696f))));
        float scale = width/300f;
        int height = Math.round(696*scale);
        setMeasuredDimension(width,height);
        for (Placement p:placements) {
            p.view.measure(MeasureSpec.makeMeasureSpec(Math.round(p.width*scale),MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(p.height*scale),MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        float scale = (right-left)/300f;
        for (Placement p:placements) {
            int x=Math.round(p.x*scale), y=Math.round(p.y*scale);
            p.view.layout(x,y,x+p.view.getMeasuredWidth(),y+p.view.getMeasuredHeight());
        }
    }
}
