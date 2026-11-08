package club.xiaojiawei.tclremotecontrol;

/** Report protocol only: standard keyboard + 16-bit Consumer Control array. */
public final class HidReports {
    public static final int KEYBOARD = 1;
    public static final int CONSUMER = 2;
    private HidReports() {}

    public static byte[] descriptor() {
        int[] items = {
            0x05,0x01, 0x09,0x06, 0xA1,0x01, 0x85,0x01,
            0x05,0x07, 0x19,0xE0, 0x29,0xE7, 0x15,0x00, 0x25,0x01,
            0x75,0x01, 0x95,0x08, 0x81,0x02,
            0x75,0x08, 0x95,0x01, 0x81,0x01,
            0x05,0x08, 0x19,0x01, 0x29,0x05, 0x75,0x01, 0x95,0x05, 0x91,0x02,
            0x75,0x03, 0x95,0x01, 0x91,0x01,
            0x05,0x07, 0x19,0x00, 0x29,0xFF, 0x15,0x00, 0x26,0xFF,0x00,
            0x75,0x08, 0x95,0x06, 0x81,0x00, 0xC0,
            0x05,0x0C, 0x09,0x01, 0xA1,0x01, 0x85,0x02,
            0x15,0x00, 0x26,0xFF,0x03, 0x19,0x00, 0x2A,0xFF,0x03,
            0x75,0x10, 0x95,0x01, 0x81,0x00, 0xC0
        };
        byte[] data = new byte[items.length];
        for (int i=0; i<items.length; i++) data[i]=(byte)items[i];
        return data;
    }

    public static byte[] keyboard(int usage) {
        if (usage < 0 || usage > 0xFF) throw new IllegalArgumentException("Keyboard usage");
        return new byte[]{0,0,(byte)usage,0,0,0,0,0};
    }
    public static byte[] consumer(int usage) {
        if (usage < 0 || usage > 0x3FF) throw new IllegalArgumentException("Consumer usage");
        return new byte[]{(byte)usage,(byte)(usage >>> 8)};
    }
    public static byte[] released(int id) {
        if (id == KEYBOARD) return keyboard(0);
        if (id == CONSUMER) return consumer(0);
        throw new IllegalArgumentException("Report ID");
    }
    public static byte[] pressed(int id, int usage) {
        if (id == KEYBOARD) return keyboard(usage);
        if (id == CONSUMER) return consumer(usage);
        throw new IllegalArgumentException("Report ID");
    }
}
