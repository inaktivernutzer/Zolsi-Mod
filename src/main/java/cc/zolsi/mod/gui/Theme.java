package cc.zolsi.mod.gui;

public final class Theme {

    public static final float[] BG = rgb(0x0C, 0x0E, 0x12);
    public static final float[] RAIL = rgb(0x0C, 0x10, 0x18);
    public static final float[] CARD = rgb(0x12, 0x15, 0x1B);
    public static final float[] FIELD = rgb(0x1A, 0x20, 0x2B);
    public static final float[] FIELD_HI = rgb(0x23, 0x2B, 0x39);
    public static final float[] TEXT = rgb(0xE6, 0xEA, 0xF0);
    public static final float[] DIM = rgb(0x8B, 0x95, 0xA3);
    public static final float[] FAINT = rgb(0x5F, 0x68, 0x74);
    public static final float[] ACCENT = rgb(0x8F, 0xB8, 0xDE);
    public static final float[] BORDER = rgb(0x22, 0x28, 0x36);
    public static final float[] KNOB_OFF = rgb(0x99, 0xA2, 0xB0);
    public static final float[] DANGER = rgb(0xD0, 0x6A, 0x75);

    private static float globalAlpha = 1.0f;
    private static float layerAlpha = 1.0f;

    private Theme() {
    }

    public static void setGlobalAlpha(float a) {
        globalAlpha = clamp(a);
    }

    public static float globalAlpha() {
        return globalAlpha;
    }

    public static void setLayerAlpha(float a) {
        layerAlpha = clamp(a);
    }

    public static float layerAlpha() {
        return layerAlpha;
    }

    private static float alpha() {
        return globalAlpha * layerAlpha;
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255.0f, g / 255.0f, b / 255.0f, 1.0f};
    }

    public static int col(float[] c) {
        return col(c, 1.0f);
    }

    public static int col(float[] c, float alphaMul) {
        int r = (int) (c[0] * 255.0f);
        int g = (int) (c[1] * 255.0f);
        int b = (int) (c[2] * 255.0f);
        int a = (int) (clamp(c[3] * alphaMul * alpha()) * 255.0f);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    public static int mix(float[] a, float[] b, float t) {
        return mix(a, b, t, 1.0f);
    }

    public static int mix(float[] a, float[] b, float t, float alphaMul) {
        float u = clamp(t);
        float r = a[0] + (b[0] - a[0]) * u;
        float g = a[1] + (b[1] - a[1]) * u;
        float bl = a[2] + (b[2] - a[2]) * u;
        float al = (a[3] + (b[3] - a[3]) * u) * alphaMul * alpha();
        return ((int) (clamp(al) * 255.0f) << 24)
            | ((int) (bl * 255.0f) << 16)
            | ((int) (g * 255.0f) << 8)
            | (int) (r * 255.0f);
    }

    private static float clamp(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
