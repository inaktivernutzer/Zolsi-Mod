package cc.zolsi.mod;

public final class ZolsiCore {

    public static final String MOD_ID = "zolsi";

    private static final ZolsiCore INSTANCE = new ZolsiCore();

    private boolean menuOpen;

    public static ZolsiCore get() {
        return INSTANCE;
    }

    public boolean isMenuOpen() {
        return menuOpen;
    }

    public void setMenuOpen(boolean value) {
        this.menuOpen = value;
    }

    private ZolsiCore() {
    }
}
