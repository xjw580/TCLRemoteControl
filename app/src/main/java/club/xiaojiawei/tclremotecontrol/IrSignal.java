package club.xiaojiawei.tclremotecontrol;

/** TCL RCA: 4 address bits, 8 command bits, then their complements, LSB first. */
public final class IrSignal {
    public static final int FREQUENCY=38000;
    private IrSignal() {}
    public static int command(RemoteKey key) {
        switch(key) {
            case POWER: return 0x54;
            case MUTE: return 0xFC;
            case VOLUME_UP: return 0xF4;
            case VOLUME_DOWN: return 0x74;
            case UP: return 0x9A;
            case DOWN: return 0x1A;
            case LEFT: return 0x6A;
            case RIGHT: return 0xEA;
            case OK: return 0x2F;
            case BACK: return 0x60; // Original remote: MSC_SCAN 0xFF9 / KEY_BACK.
            case HOME: return 0x10;
            case INPUT: return 0xC5;
            case MENU: return 0x37;
            case SETTINGS: return 0xF3;
            // Captured from BT_RC802A_C1 in infrared fallback on TCL 55Q9K.
            case RED: return 0x4E;
            case GREEN: return 0x8E;
            case BLUE: return 0x0E;
            case BILIBILI: return 0x24;
            case MANGO: return 0xF1;
            default: return -1;
        }
    }
    public static boolean repeats(RemoteKey key) {
        return key==RemoteKey.UP || key==RemoteKey.DOWN || key==RemoteKey.LEFT || key==RemoteKey.RIGHT
            || key==RemoteKey.VOLUME_UP || key==RemoteKey.VOLUME_DOWN;
    }
    public static int[] pattern(int command) {
        if(command<0 || command>255) throw new IllegalArgumentException("Invalid IR command");
        int frame=0xF | (command<<4) | ((command^0xFF)<<16);
        int[] pattern=new int[51];
        pattern[0]=4000; pattern[1]=4000;
        for(int bit=0;bit<24;bit++) {
            pattern[2+bit*2]=500;
            pattern[3+bit*2]=((frame>>>bit)&1)==0 ? 1000 : 2000;
        }
        pattern[50]=500;
        return pattern;
    }
}
