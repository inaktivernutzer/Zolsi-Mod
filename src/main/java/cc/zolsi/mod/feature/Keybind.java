package cc.zolsi.mod.feature;

public final class Keybind {

    public static final int MOUSE_BASE = 1000;

    public static boolean isMouse(int key) {
        return key >= MOUSE_BASE;
    }

    public enum Mode {
        TOGGLE,
        HOLD
    }

    private int key = -1;
    private Mode mode = Mode.TOGGLE;

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean isBound() {
        return key != -1;
    }

    public void clear() {
        this.key = -1;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }
}
