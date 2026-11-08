package club.xiaojiawei.tclremotecontrol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.widget.Button;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class RemoteLayoutTest {
    @Test public void controlsHaveUsableTouchTargetsAndRenderWithoutOverlap() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            List<Button> buttons=new ArrayList<>();
            RemoteLayout panel=new RemoteLayout(context,id->{
                RemoteKey key=RemoteKey.fromId(id);
                Button button=new RemoteButton(context,id,key.label);
                buttons.add(button); return button;
            });
            float density=context.getResources().getDisplayMetrics().density;
            for(int dpWidth:new int[]{280,360,600}) {
                panel.measure(View.MeasureSpec.makeMeasureSpec(Math.round(dpWidth*density),View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                panel.layout(0,0,panel.getMeasuredWidth(),panel.getMeasuredHeight());
                assertEquals(19,buttons.size());
                for(Button button:buttons) {
                    assertTrue(button.getContentDescription().toString(),button.getWidth()>=Math.floor(48*density));
                    assertTrue(button.getContentDescription().toString(),button.getHeight()>=Math.floor(48*density));
                    Rect bounds=new Rect(); button.getHitRect(bounds);
                    assertTrue(new Rect(0,0,panel.getWidth(),panel.getHeight()).contains(bounds));
                    for(Button other:buttons) if(button!=other) {
                        Rect otherBounds=new Rect(); other.getHitRect(otherBounds);
                        assertFalse("Touch targets overlap",Rect.intersects(bounds,otherBounds));
                    }
                }
            }
            Bitmap image=Bitmap.createBitmap(panel.getWidth(),panel.getHeight(),Bitmap.Config.ARGB_8888);
            panel.draw(new Canvas(image));
            try(FileOutputStream output=new FileOutputStream(new File(context.getCacheDir(),"remote-preview.png"))) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));
            } catch(Exception error) { throw new AssertionError(error); }
            image.recycle();
        });
    }
}
