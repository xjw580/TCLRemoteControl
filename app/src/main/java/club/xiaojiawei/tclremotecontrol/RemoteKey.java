package club.xiaojiawei.tclremotecontrol;

/** Extended keyboard usages were read from this TCL firmware's original remote keymap. */
public enum RemoteKey {
    MUTE("mute", "静音", 2, 0xe2),
    POWER("power", "电源", 1, 0x66),
    INPUT("input", "信号源", 1, 0x97),
    RED("red", "红色快捷键", 1, 0x99),
    GREEN("green", "绿色快捷键", 1, 0x9a),
    BLUE("blue", "蓝色快捷键", 1, 0x9b),
    UP("up", "上", 1, 0x52),
    DOWN("down", "下", 1, 0x51),
    LEFT("left", "左", 1, 0x50),
    RIGHT("right", "右", 1, 0x4f),
    OK("ok", "确定", 1, 0x28),
    BACK("back", "返回", 2, 0x224),
    HOME("home", "主页", 2, 0x223),
    MENU("menu", "菜单", 2, 0x40),
    VOLUME_UP("volume_up", "音量加", 2, 0xe9),
    VOLUME_DOWN("volume_down", "音量减", 2, 0xea),
    SETTINGS("settings", "电视设置", 1, 0xa8),
    BILIBILI("bilibili", "哔哩哔哩", 1, 0xd1),
    MANGO("mango", "芒果 TV", 1, 0xd3);

    public final String id, label;
    public final int report, usage;
    RemoteKey(String id,String label,int report,int usage) {
        this.id=id; this.label=label; this.report=report; this.usage=usage;
    }
    public static RemoteKey fromId(String id) {
        for(RemoteKey key:values()) if(key.id.equals(id)) return key;
        throw new IllegalArgumentException("Unknown remote key: "+id);
    }
}
