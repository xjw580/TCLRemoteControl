package club.xiaojiawei.tclremotecontrol;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;

/** Vector icons drawn locally; each control remains a normal accessible Android Button. */
@SuppressLint("ViewConstructor") // Created programmatically with a required icon and accessible label.
public final class RemoteButton extends Button {
    private final String icon;
    private final Drawable appLogo;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path outline = new Path();

    public RemoteButton(Context context,String icon,String label) {
        super(context);
        this.icon=icon;
        appLogo=icon.equals("bilibili") ? context.getDrawable(R.drawable.ic_bilibili)
            : icon.equals("mango") ? context.getDrawable(R.drawable.ic_mango) : null;
        setContentDescription(label);
        setAllCaps(false); setTextColor(0xffd6dfe3); setTextSize(12);
        setPadding(0,0,0,0); setMinimumWidth(0); setMinimumHeight(0);
        setMinWidth(0); setMinHeight(0); setStateListAnimator(null);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed},shape(0xff607986));
        background.addState(new int[]{},shape(0xff465762));
        if (icon.equals("up") || icon.equals("down") || icon.equals("left") || icon.equals("right")
            || icon.startsWith("volume")) {
            background = new StateListDrawable();
            background.addState(new int[]{android.R.attr.state_pressed},shape(0xff607986));
            background.addState(new int[]{},shape(0x0020272b));
        }
        setBackground(background);
    }
    private GradientDrawable shape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(200);
        if ((color>>>24)!=0) shape.setStroke(Math.max(1,Math.round(getResources().getDisplayMetrics().density)),0xff657986);
        return shape;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (appLogo!=null) {
            float fit=Math.min(getWidth()*.74f/appLogo.getIntrinsicWidth(),
                getHeight()*.63f/appLogo.getIntrinsicHeight());
            int width=Math.round(appLogo.getIntrinsicWidth()*fit);
            int height=Math.round(appLogo.getIntrinsicHeight()*fit);
            int left=(getWidth()-width)/2, top=(getHeight()-height)/2;
            appLogo.setBounds(left,top,left+width,top+height);
            appLogo.draw(canvas);
            return;
        }
        canvas.save();
        float size=Math.min(getWidth(),getHeight())*(icon.equals("ok") ? .38f : .45f);
        canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);
        canvas.scale(size/24,size/24);
        ink.setColor(0xffd6dfe3); ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(1.6f); ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
        switch(icon) {
            case "power":
                rect.set(4,4,20,21); canvas.drawArc(rect,-55,290,false,ink); line(canvas,12,2,12,11); break;
            case "mute":
                path(canvas,3,9,7,9,12,5,12,19,7,15,3,15,3,9);
                line(canvas,16,9,22,15); line(canvas,22,9,16,15); break;
            case "input":
                path(canvas,10,4,21,4,21,20,10,20); line(canvas,2,12,16,12);
                path(canvas,12,8,16,12,12,16); break;
            case "red": case "green": case "blue":
                ink.setStyle(Paint.Style.FILL);
                ink.setColor(icon.equals("red") ? 0xffef6468 : icon.equals("green") ? 0xff48be83 : 0xff54a6e9);
                canvas.drawCircle(12,12,5,ink); break;
            case "up": path(canvas,6,15,12,9,18,15); break;
            case "down": path(canvas,6,9,12,15,18,9); break;
            case "left": path(canvas,15,6,9,12,15,18); break;
            case "right": path(canvas,9,6,15,12,9,18); break;
            case "ok":
                ink.setStyle(Paint.Style.FILL); ink.setTextSize(9); ink.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("OK",12,15.5f,ink); break;
            case "back":
                path(canvas,8,5,3,10,8,15); path(canvas,3,10,15,10);
                rect.set(10,10,21,20); canvas.drawArc(rect,-90,180,false,ink); line(canvas,15,20,5,20); break;
            case "home": path(canvas,3,11,12,3,21,11,21,21,15,21,15,14,9,14,9,21,3,21,3,11); break;
            case "menu": line(canvas,4,5,20,5); line(canvas,4,12,20,12); line(canvas,4,19,20,19); break;
            case "volume_up": line(canvas,12,4,12,20); line(canvas,4,12,20,12); break;
            case "volume_down": line(canvas,4,12,20,12); break;
            case "settings":
                canvas.drawCircle(12,12,7,ink); canvas.drawCircle(12,12,3,ink);
                for(int i=0;i<8;i++) { canvas.save(); canvas.rotate(i*45,12,12); line(canvas,12,2,12,5); canvas.restore(); } break;

            default: break;
        }
        canvas.restore();
    }
    private void line(Canvas canvas,float x,float y,float endX,float endY) { canvas.drawLine(x,y,endX,endY,ink); }
    private void path(Canvas canvas,float... points) {
        outline.reset(); outline.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2) outline.lineTo(points[i],points[i+1]);
        canvas.drawPath(outline,ink);
    }
    @Override public boolean performClick() { return super.performClick(); }
}
