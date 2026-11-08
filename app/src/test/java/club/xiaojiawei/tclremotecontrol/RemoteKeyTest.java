package club.xiaojiawei.tclremotecontrol;

import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;

public final class RemoteKeyTest {
    @Test public void extendedKeysMatchOriginalTclInputKeymap() {
        // EVIOCGKEYCODE_V2 on BT_RC802A_C1: usage -> Linux keycode.
        check(RemoteKey.POWER, 0x66);     // 116 POWER
        check(RemoteKey.INPUT, 0x97);     // 417 TV_INPUT
        check(RemoteKey.RED, 0x99);       // 699 TCL_CUSTOMIZE_1
        check(RemoteKey.GREEN, 0x9a);     // 700 TCL_CUSTOMIZE_2
        check(RemoteKey.BLUE, 0x9b);      // 701 TCL_CUSTOMIZE_3
        check(RemoteKey.SETTINGS, 0xa8);  // 233 TCL_SET
        check(RemoteKey.BILIBILI, 0xd1);  // 551 KEY_XIAODIANSHI
        check(RemoteKey.MANGO, 0xd3);     // 549 KEY_MGTV

    }
    private void check(RemoteKey key,int usage) {
        assertEquals(HidReports.KEYBOARD,key.report);
        assertArrayEquals(new byte[]{0,0,(byte)usage,0,0,0,0,0},HidReports.pressed(key.report,key.usage));
    }
    @Test public void everyPhysicalButtonHasUniqueIdentityAndValidReport() {
        HashSet<String> names=new HashSet<>();
        assertEquals(19,RemoteKey.values().length);
        for(RemoteKey key:RemoteKey.values()) {
            assertTrue(names.add(key.id));
            assertSame(key,RemoteKey.fromId(key.id));
            assertEquals(key.report==1 ? 8 : 2,HidReports.pressed(key.report,key.usage).length);
        }
    }
}
