package cc.zolsi.mod.feature.visuals;
import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.feature.Modules;

import cc.zolsi.mod.gui.Theme;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImDrawFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArrayListFeature {

    public static final float BASE_PX = 13.0f;
    public static final float MIN_SCALE = 1.0f;
    public static final float MAX_SCALE = 3.0f;
    public static final float FONT_BAKE_PX = BASE_PX * MAX_SCALE;

    private static final float SP_PRESENT = 15.0f;
    private static final float SP_MOVE = 16.0f;

    private static final float GRAD_WAVES = 1.2f;
    private static final float GRAD_SPEED = 0.35f;
    private static final float BAR_LOW = 0.12f;
    private static final float BAR_HIGH = 1.0f;
    private static final float TEXT_LOW = 0.45f;
    private static final float TEXT_HIGH = 1.0f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private static final ArrayListFeature INSTANCE = new ArrayListFeature();

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImInt position = new ImInt(0);
    public final float[] opacity = {0.97f};
    public final float[] cornerRadius = {5.0f};
    public final float[] scale = {MIN_SCALE};
    public final float[] color = {0.561f, 0.722f, 0.871f, 1.0f};
    public final ImBoolean catCombat = new ImBoolean(true);
    public final ImBoolean catUtility = new ImBoolean(true);
    public final ImBoolean catVisuals = new ImBoolean(true);

    private final ImVec2 measure = new ImVec2();
    private final LinkedHashMap<String, RowAnim> anims = new LinkedHashMap<>();

    public static ArrayListFeature get() {
        return INSTANCE;
    }

    public void render(float displayW, float displayH, ImFont font, float bakeSize) {
        ImFont drawFont = font != null ? font : ImGui.getFont();
        float bake = bakeSize > 0.0f ? bakeSize : BASE_PX;

        float dt = ImGui.getIO().getDeltaTime();
        if (dt <= 0.0f) {
            dt = 1.0f / 60.0f;
        } else if (dt > 0.25f) {
            dt = 0.25f;
        }

        for (RowAnim st : this.anims.values()) {
            st.target = 0.0f;
        }
        for (Modules.Entry entry : Modules.all()) {
            if (categorySelected(entry.category) && entry.enabled.get()) {
                RowAnim st = this.anims.get(entry.name);
                if (st == null) {
                    st = new RowAnim();
                    this.anims.put(entry.name, st);
                }
                st.target = 1.0f;
            }
        }

        float s = Math.max(MIN_SCALE, Math.min(MAX_SCALE, this.scale[0]));
        float renderPx = BASE_PX * s;
        float ratio = renderPx / bake;

        float lineHeightAtBake = bake;
        List<RowAnim> rows = new ArrayList<>();
        for (Map.Entry<String, RowAnim> e : this.anims.entrySet()) {
            RowAnim st = e.getValue();
            if (st.target < 0.5f && st.present < 0.002f) {
                continue;
            }
            drawFont.calcTextSizeA(this.measure, bake, Float.MAX_VALUE, 0.0f, e.getKey());
            st.name = e.getKey();
            st.width = this.measure.x;
            lineHeightAtBake = this.measure.y;
            rows.add(st);
        }

        float padX = 8.0f * s;
        float padY = 3.0f * s;
        float gap = 3.0f * s;
        float margin = 4.0f;
        float barW = 3.0f * s;
        float slide = 22.0f * s;
        float entryHeight = lineHeightAtBake * ratio + padY * 2.0f;
        float step = entryHeight + gap;
        float radius = Math.min(this.cornerRadius[0], entryHeight * 0.5f);

        int pos = this.position.get();
        boolean right = pos == 0 || pos == 2;
        boolean top = pos == 0 || pos == 1;

        rows.sort((left, rgt) -> Float.compare(rgt.width, left.width));

        for (int i = 0; i < rows.size(); i++) {
            RowAnim st = rows.get(i);
            float targetY = top ? margin + i * step : displayH - margin - entryHeight - i * step;
            if (!st.yInit) {
                st.y = targetY;
                st.yInit = true;
            } else {
                st.y += (targetY - st.y) * approach(SP_MOVE, dt);
            }
            st.present += (st.target - st.present) * approach(SP_PRESENT, dt);
        }

        float stackTop = Float.MAX_VALUE;
        float stackBot = -Float.MAX_VALUE;
        boolean any = false;
        for (RowAnim st : rows) {
            if (smoother(clamp01(st.present)) < 0.002f) {
                continue;
            }
            any = true;
            if (st.y < stackTop) {
                stackTop = st.y;
            }
            if (st.y + entryHeight > stackBot) {
                stackBot = st.y + entryHeight;
            }
        }
        if (!any) {
            pruneDead();
            return;
        }
        float stackH = Math.max(1.0f, stackBot - stackTop);
        float time = (float) ImGui.getTime();

        int fontSizePx = Math.max(1, Math.round(renderPx));
        ImDrawList dl = ImGui.getForegroundDrawList();

        for (RowAnim st : rows) {
            float e = smoother(clamp01(st.present));
            if (e < 0.002f) {
                continue;
            }
            float off = (1.0f - e) * slide;
            float textW = st.width * ratio;
            float entryWidth = textW + padX * 2.0f + barW;
            float y1 = st.y;
            float y2 = y1 + entryHeight;

            float bgX1;
            float bgX2;
            float lx1;
            float textX;
            int bgFlags;
            if (right) {
                float outer = displayW - margin + off;
                lx1 = outer - barW;
                bgX2 = lx1;
                bgX1 = outer - entryWidth;
                textX = bgX1 + padX;
                bgFlags = ImDrawFlags.RoundCornersLeft;
            } else {
                float outer = margin - off;
                lx1 = outer;
                bgX1 = outer + barW;
                bgX2 = outer + entryWidth;
                textX = bgX1 + padX;
                bgFlags = ImDrawFlags.RoundCornersRight;
            }

            float pTop = (y1 - stackTop) / stackH;
            float pBot = (y2 - stackTop) / stackH;
            float pMid = (pTop + pBot) * 0.5f;

            int bg = packArr(Theme.CARD, this.opacity[0] * e);
            int border = packArr(Theme.BORDER, 0.55f * e);
            int barTop = packColor(this.color[0], this.color[1], this.color[2],
                lerp(BAR_LOW, BAR_HIGH, wave(pTop, time)) * e);
            int barBot = packColor(this.color[0], this.color[1], this.color[2],
                lerp(BAR_LOW, BAR_HIGH, wave(pBot, time)) * e);
            int textCol = packColor(this.color[0], this.color[1], this.color[2],
                lerp(TEXT_LOW, TEXT_HIGH, wave(pMid, time)) * e);

            dl.addRectFilled(bgX1, y1, bgX2, y2, bg, radius, bgFlags);
            dl.addRect(bgX1, y1, bgX2, y2, border, radius, bgFlags, 1.0f);
            dl.addRectFilledMultiColor(lx1, y1, lx1 + barW, y2, barTop, barTop, barBot, barBot);
            dl.addText(drawFont, fontSizePx, textX, y1 + padY, textCol, st.name);
        }

        pruneDead();
    }

    private void pruneDead() {
        Iterator<Map.Entry<String, RowAnim>> it = this.anims.entrySet().iterator();
        while (it.hasNext()) {
            RowAnim st = it.next().getValue();
            if (st.target < 0.5f && st.present < 0.002f) {
                it.remove();
            }
        }
    }

    private boolean categorySelected(int category) {
        switch (category) {
            case Modules.COMBAT:
                return this.catCombat.get();
            case Modules.UTILITY:
                return this.catUtility.get();
            case Modules.VISUALS:
                return this.catVisuals.get();
            default:
                return false;
        }
    }

    private static float wave(float p, float time) {
        return 0.5f + 0.5f * (float) Math.sin(TWO_PI * (p * GRAD_WAVES - time * GRAD_SPEED));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float approach(float speed, float dt) {
        return 1.0f - (float) Math.exp(-speed * dt);
    }

    private static float smoother(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static int packArr(float[] c, float alpha) {
        return packColor(c[0], c[1], c[2], alpha);
    }

    private static int packColor(float r, float g, float b, float alpha) {
        int ri = channel(r);
        int gi = channel(g);
        int bi = channel(b);
        int ai = channel(alpha);
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }

    private static int channel(float value) {
        return (int) (Math.max(0.0f, Math.min(1.0f, value)) * 255.0f + 0.5f);
    }

    private static final class RowAnim {
        String name;
        float width;
        float present;
        float target;
        float y;
        boolean yInit;
    }

    private ArrayListFeature() {
    }
}
