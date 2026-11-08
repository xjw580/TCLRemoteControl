package club.xiaojiawei.tclremotecontrol;

import org.junit.Test;
import static org.junit.Assert.*;

public final class IrSignalTest {
    @Test public void volumeFrameMatchesCapturedTclRcaWireBits() {
        int[] pattern=IrSignal.pattern(0xF4);
        assertEquals(51,pattern.length);
        assertEquals(4000,pattern[0]); assertEquals(4000,pattern[1]);
        // Independent MSB-first wire representation from RC802N capture: F2F0D0.
        int decoded=0;
        for(int bit=0;bit<24;bit++) {
            assertEquals(500,pattern[2+2*bit]);
            decoded=(decoded<<1) | (pattern[3+2*bit]==2000 ? 1 : 0);
        }
        assertEquals(0xF2F0D0,decoded);
        assertEquals(500,pattern[50]);
    }
    @Test public void allCommandsHaveBalancedComplementsAndBoundedDuration() {
        for(int command=0;command<256;command++) {
            int[] pattern=IrSignal.pattern(command);
            int duration=0,ones=0;
            for(int value:pattern) { assertTrue(value>0); duration+=value; }
            for(int i=3;i<50;i+=2) { assertTrue(pattern[i]==1000 || pattern[i]==2000); if(pattern[i]==2000) ones++; }
            assertEquals(12,ones);
            assertEquals(56500,duration);
        }
    }
    @Test public void commandsMatchOriginalRemoteInfraredCaptures() {
        RemoteKey[] keys={RemoteKey.RED,RemoteKey.GREEN,RemoteKey.BLUE,RemoteKey.BILIBILI,RemoteKey.MANGO,RemoteKey.BACK};
        int[] capturedScans={0xF8D,0xF8E,0xF8F,0xFDB,0xF70,0xFF9};
        for(int i=0;i<keys.length;i++) {
            int[] pattern=IrSignal.pattern(IrSignal.command(keys[i]));
            int wire=0;
            for(int bit=0;bit<24;bit++) wire=(wire<<1) | (pattern[3+bit*2]==2000 ? 1 : 0);
            // Receiver reports the wire address plus the complemented command byte.
            assertEquals(capturedScans[i],((wire>>>12)&0xF00) | (wire&0xFF));
            assertFalse(IrSignal.repeats(keys[i]));
        }
    }
    @Test public void repeatPolicyKeepsActionsSingleShot() {
        assertFalse(IrSignal.repeats(RemoteKey.POWER));
        assertFalse(IrSignal.repeats(RemoteKey.OK));
        assertTrue(IrSignal.repeats(RemoteKey.VOLUME_UP));
    }
    @Test public void refusesUnknownCommandValues() {
        assertThrows(IllegalArgumentException.class,()->IrSignal.pattern(-1));
        assertThrows(IllegalArgumentException.class,()->IrSignal.pattern(256));
    }
}
