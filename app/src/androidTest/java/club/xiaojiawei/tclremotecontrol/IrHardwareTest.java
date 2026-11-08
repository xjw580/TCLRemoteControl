package club.xiaojiawei.tclremotecontrol;

import android.hardware.ConsumerIrManager;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Hardware check. Transmission is opt-in and restricted to a volume-up probe. */
public final class IrHardwareTest {
    @Test public void inspectEmitterAndOptionallyProbeVolume() {
        ConsumerIrManager ir=InstrumentationRegistry.getInstrumentation().getTargetContext()
            .getSystemService(ConsumerIrManager.class);
        assumeTrue(ir!=null && ir.hasIrEmitter());
        ConsumerIrManager.CarrierFrequencyRange[] ranges=ir.getCarrierFrequencies();
        assertNotNull(ranges);
        boolean supported=false;
        for(ConsumerIrManager.CarrierFrequencyRange range:ranges) {
            Log.i("RemoteIR","carrier="+range.getMinFrequency()+".."+range.getMaxFrequency());
            supported |= range.getMinFrequency()<=IrSignal.FREQUENCY && range.getMaxFrequency()>=IrSignal.FREQUENCY;
        }
        assertTrue("38 kHz carrier is unavailable",supported);
        if("true".equals(InstrumentationRegistry.getArguments().getString("irProbeVolume"))) {
            int frequency=Integer.parseInt(InstrumentationRegistry.getArguments().getString("irFrequency","38000"));
            assertTrue(frequency==38000 || frequency==56000);
            int[] frame=IrSignal.pattern(0xF4);
            int[] repeated=new int[frame.length*3+2];
            for(int copy=0;copy<3;copy++) {
                System.arraycopy(frame,0,repeated,copy*(frame.length+1),frame.length);
                if(copy<2) repeated[copy*(frame.length+1)+frame.length]=8000;
            }
            ir.transmit(frequency,repeated);
            Log.i("RemoteIR","Volume-up RCA probe sent");
        }
    }
}
