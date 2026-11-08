package club.xiaojiawei.tclremotecontrol;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class HidReportsTest {
    @Test public void descriptorDefinesKeyboardAndConsumerReports() {
        byte[] descriptor = HidReports.descriptor();
        int id = 0, size = 0, count = 0, depth = 0, logicalMax = 0, usageMax = 0;
        boolean extendedKeyboard = false;
        Map<String, Integer> bits = new HashMap<>();
        for (int i = 0; i < descriptor.length;) {
            int header = descriptor[i++] & 255;
            assertNotEquals("Unexpected long HID item", 0xfe, header);
            int n = header & 3;
            if (n == 3) n = 4;
            assertTrue("Truncated HID item", i + n <= descriptor.length);
            int value = 0;
            for (int j = 0; j < n; j++) value |= (descriptor[i++] & 255) << (8 * j);
            int type = (header >> 2) & 3, tag = header >> 4;
            if (type == 1) {
                if (tag == 2) logicalMax = value;
                if (tag == 7) size = value;
                if (tag == 8) id = value;
                if (tag == 9) count = value;
            }
            if (type == 2 && tag == 2) usageMax = value;
            if (type == 0) {
                if (tag == 10) depth++;
                if (tag == 12) depth--;
                assertTrue("Unbalanced collection", depth >= 0);
                if (tag == 8 || tag == 9) {
                    String key = id + ":" + tag;
                    bits.put(key, bits.getOrDefault(key, 0) + size * count);
                    if (tag == 8 && id == 1 && size == 8 && count == 6) {
                        assertEquals(255, logicalMax);
                        assertEquals(255, usageMax);
                        extendedKeyboard = true;
                    }
                }
            }
        }
        assertEquals(0, depth);
        assertTrue("Keyboard descriptor must advertise vendor usages above 0x65", extendedKeyboard);
        assertEquals(Integer.valueOf(64), bits.get("1:8"));
        assertEquals(Integer.valueOf(8), bits.get("1:9"));
        assertEquals(Integer.valueOf(16), bits.get("2:8"));
        assertEquals(3, bits.size());
    }

    @Test public void keyboardButtonsUseStandardEightByteReports() {
        for (int usage : new int[]{0x28, 0x4f, 0x50, 0x51, 0x52}) {
            assertArrayEquals(new byte[]{0, 0, (byte) usage, 0, 0, 0, 0, 0},
                HidReports.pressed(HidReports.KEYBOARD, usage));
        }
    }

    @Test public void mediaButtonsUseLittleEndianConsumerUsages() {
        int[] usages = {0x224, 0x223, 0x40, 0xe9, 0xea, 0xe2};
        byte[][] reports = {{0x24, 2}, {0x23, 2}, {0x40, 0},
            {(byte) 0xe9, 0}, {(byte) 0xea, 0}, {(byte) 0xe2, 0}};
        for (int i = 0; i < usages.length; i++) {
            assertArrayEquals(reports[i], HidReports.pressed(HidReports.CONSUMER, usages[i]));
        }
    }

    @Test public void releasesClearEveryKeyAndDoNotShareMutableArrays() {
        assertArrayEquals(new byte[8], HidReports.released(HidReports.KEYBOARD));
        assertArrayEquals(new byte[2], HidReports.released(HidReports.CONSUMER));
        byte[] report = HidReports.released(HidReports.KEYBOARD);
        report[2] = 0x28;
        assertArrayEquals(new byte[8], HidReports.released(HidReports.KEYBOARD));
    }

    @Test public void rejectsUsagesOutsideDescriptorAndUnknownReportIds() {
        for (int usage : new int[]{-1, 256, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> HidReports.keyboard(usage));
        }
        for (int usage : new int[]{-1, 0x400, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> HidReports.consumer(usage));
        }
        for (int id : new int[]{-1, 0, 3}) {
            assertThrows(IllegalArgumentException.class, () -> HidReports.released(id));
            assertThrows(IllegalArgumentException.class, () -> HidReports.pressed(id, 0));
        }
        assertEquals(0xff, HidReports.keyboard(0xff)[2] & 255);
        assertArrayEquals(new byte[]{(byte) 0xff, 3}, HidReports.consumer(0x3ff));
    }
}
