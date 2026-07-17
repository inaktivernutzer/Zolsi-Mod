package cc.zolsi.mod.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Widgets {

    private static final float PILL_W = 40.0f;
    private static final float PILL_H = 22.0f;
    private static final float ROW_H = 32.0f;
    private static final float PAD = 16.0f;
    private static final float SUB_INDENT = 18.0f;
    private static final float GRAB_R = 6.0f;
    private static final float OPT_H = 28.0f;
    private static final float DROP_PAD = 6.0f;
    private static final float DROP_W = 176.0f;

    private static final float SP_HOVER = 16.0f;
    private static final float SP_TOGGLE = 8.0f;
    private static final float EXPAND_DUR = 0.3125f;
    private static final float SUB_DUR = 0.25f;
    private static final float SP_SEG = 8.0f;
    private static final float SP_SELECT = 64.0f;
    private static final float SP_TOOLTIP = 16.0f;
    private static final float SP_DROP = 8.0f;
    private static final float TOOLTIP_DELAY = 0.7f;

    private static final Map<String, Float> anims = new HashMap<>();
    private static final Map<String, Boolean> expandedCards = new HashMap<>();
    private static final Map<String, Float> bodyHeights = new HashMap<>();
    private static final Map<String, Float> subHeights = new HashMap<>();
    private static final Map<String, Integer> rangeGrab = new HashMap<>();
    private static final ImVec2 measure = new ImVec2();

    private static String pendingTooltip;
    private static String shownTooltip;
    private static double shownSince;
    private static float tooltipAlpha;

    private static float regionX;
    private static float regionW;
    private static float innerX;
    private static float innerW;
    private static float cardX;
    private static float cardY;
    private static float cardW;
    private static String cardId = "";
    private static ImDrawList cardDl;
    private static boolean bodyOpen;
    private static float bodyStartY;
    private static float cardHeaderBottom;
    private static boolean cardBodyExplicit;
    private static float cardBodyBottom;

    private static final float ROW_GAP = 8.0f;

    private static final java.util.ArrayDeque<SubFrame> subStack = new java.util.ArrayDeque<>();

    private static final class SubFrame {
        float topY;
        float h;
        float layerSaved = 1.0f;
        boolean open;
        float entryY;
        float baseFull;
        float e;
    }

    private static String dropOpenId;
    private static DropState drop;
    private static boolean dropActive;
    private static float dropPanelX;
    private static float dropPanelY;
    private static float dropPanelW;
    private static float dropPanelH;

    private static final int DROP_MAX_VISIBLE = 10;

    private static final class DropState {
        String id;
        float x;
        float y;
        float w;
        boolean multi;
        String[] names;
        ImBoolean[] values;
        ImInt valueInt;
        int scrollOffset;
        float rowX;
        float rowY;
        float rowW;
        float rowH;
    }

    private Widgets() {
    }

    public static void setRegion(float x, float w) {
        regionX = x;
        regionW = w;
    }

    private static ImFont body() {
        return ImGuiManager.get().fontBody();
    }

    private static ImFont medium() {
        return ImGuiManager.get().fontMedium();
    }

    private static ImFont mono() {
        return ImGuiManager.get().fontMono();
    }

    public static float anim(String id, float target, float speed) {
        Float cur = anims.get(id);
        float a = cur == null ? target : cur;
        float k = Math.min(1.0f, ImGui.getIO().getDeltaTime() * speed);
        a += (target - a) * k;
        anims.put(id, a);
        return a;
    }

    private static float tween(String id, float target, float dur) {
        Float cur = anims.get(id);
        float p = cur == null ? target : cur;
        float step = ImGui.getIO().getDeltaTime() / Math.max(dur, 0.0001f);
        if (p < target) {
            p = Math.min(target, p + step);
        } else if (p > target) {
            p = Math.max(target, p - step);
        }
        anims.put(id, p);
        return p;
    }

    private static float smoother(float t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static float smooth(float v) {
        float t = clamp01(v);
        return t * t * (3.0f - 2.0f * t);
    }

    private static boolean disabled;
    private static float disabledSavedGlobal;

    public static void pushDisabled() {
        disabled = true;
        disabledSavedGlobal = Theme.globalAlpha();
        Theme.setGlobalAlpha(disabledSavedGlobal * 0.4f);
    }

    public static void popDisabled() {
        disabled = false;
        Theme.setGlobalAlpha(disabledSavedGlobal);
    }

    private static boolean blocked() {
        if (disabled) {
            return true;
        }
        if (!dropActive) {
            return false;
        }
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        return mx >= dropPanelX && mx <= dropPanelX + dropPanelW
            && my >= dropPanelY && my <= dropPanelY + dropPanelH;
    }

    public static boolean mouseOverDropdown() {
        return blocked();
    }

    public static void clearDropdown() {
        dropOpenId = null;
        drop = null;
        dropActive = false;
    }

    public static void resetFrame() {
        subStack.clear();
        disabled = false;
    }

    public static void beginCard(String id) {
        cardId = id;
        cardBodyExplicit = false;
        cardDl = ImGui.getWindowDrawList();
        cardDl.channelsSplit(2);
        cardDl.channelsSetCurrent(1);
        cardX = regionX;
        cardW = regionW;
        cardY = ImGui.getCursorScreenPosY();
        innerX = cardX + PAD;
        innerW = cardW - PAD * 2.0f;
        ImGui.setCursorScreenPos(innerX, cardY + 14.0f);
        ImGui.beginGroup();
    }

    public static void endCard(boolean enabled) {
        ImGui.endGroup();
        float bottom = (cardBodyExplicit ? cardBodyBottom : ImGui.getItemRectMaxY()) + 14.0f;
        float on = anim(cardId + ".card", enabled ? 1.0f : 0.0f, SP_TOGGLE);
        cardDl.channelsSetCurrent(0);
        cardDl.addRectFilled(cardX, cardY, cardX + cardW, bottom, Theme.col(Theme.CARD), 10.0f);
        cardDl.addRect(cardX, cardY, cardX + cardW, bottom,
            Theme.mix(Theme.BORDER, Theme.ACCENT, 0.45f * on), 10.0f, 0, 1.0f);
        cardDl.channelsMerge();
        ImGui.setCursorScreenPos(cardX, bottom);
        ImGui.dummy(cardW, 0.0f);
        ImGui.dummy(0.0f, 4.0f);
    }

    private static float peek(String id) {
        Float f = anims.get(id);
        return f == null ? 0.0f : f;
    }

    private static boolean isExpanded(String id) {
        Boolean b = expandedCards.get(id);
        return b != null && b;
    }

    public static void collapseAllCards() {
        expandedCards.clear();
        anims.entrySet().removeIf(e -> e.getKey().endsWith(".exp"));
    }

    public static boolean cardHeader(String id, String title, ImBoolean enabled, String bindText, boolean listening) {
        return cardHeader(id, title, enabled, bindText, listening, false);
    }

    public static boolean cardHeader(String id, String title, ImBoolean enabled, String bindText,
                                     boolean listening, boolean collapsible) {
        float rowH = 36.0f;
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        boolean pillClicked = false;
        float rightX = innerX + innerW;

        float toggleX = enabled != null ? rightX - PILL_W : rightX;
        float bindW = 0.0f;
        if (bindText != null) {
            ImGui.pushFont(mono());
            ImGui.calcTextSize(measure, bindText);
            ImGui.popFont();
            bindW = measure.x + 20.0f;
        }
        float bindX = (enabled != null ? toggleX - 10.0f : rightX) - bindW;
        float hdrEnd = bindText != null ? bindX - 6.0f : (enabled != null ? toggleX - 6.0f : rightX);

        float hdrHov = 0.0f;
        if (collapsible) {
            ImGui.setCursorScreenPos(innerX, y);
            if (ImGui.invisibleButton(id + ".hdr", Math.max(hdrEnd - innerX, 1.0f), rowH) && !blocked()) {
                expandedCards.put(id, !isExpanded(id));
            }
            hdrHov = anim(id + ".hdrH", ImGui.isItemHovered() && !blocked() ? 1.0f : 0.0f, SP_HOVER);
        }

        if (enabled != null) {
            float ty = y + (rowH - PILL_H) * 0.5f;
            ImGui.setCursorScreenPos(toggleX, ty);
            if (ImGui.invisibleButton(id + ".on", PILL_W, PILL_H) && !blocked()) {
                enabled.set(!enabled.get());
            }
            float on = anim(id + ".onA", enabled.get() ? 1.0f : 0.0f, SP_TOGGLE);
            drawPill(dl, toggleX, ty, on);
        }

        if (bindText != null) {
            float ph = 22.0f;
            float py = y + (rowH - ph) * 0.5f;
            ImGui.setCursorScreenPos(bindX, py);
            pillClicked = ImGui.invisibleButton(id + ".bind", bindW, ph) && !blocked();
            ImGui.openPopupOnItemClick(id + ".bindmode");
            float hov = anim(id + ".bindH", ImGui.isItemHovered() && !blocked() ? 1.0f : 0.0f, SP_HOVER);
            float pulse = listening ? (float) (Math.sin(ImGui.getTime() * 1.8) * 0.25 + 0.75) : 1.0f;
            dl.addRectFilled(bindX, py, bindX + bindW, py + ph, Theme.mix(Theme.FIELD, Theme.FIELD_HI, hov), ph * 0.5f);
            dl.addRect(bindX, py, bindX + bindW, py + ph,
                listening ? Theme.col(Theme.ACCENT, pulse) : Theme.col(Theme.BORDER), ph * 0.5f);
            int tcol = listening ? Theme.col(Theme.ACCENT, pulse) : Theme.mix(Theme.FAINT, Theme.TEXT, hov);
            dl.addText(mono(), ImGuiManager.MONO_SIZE, bindX + 10.0f,
                py + (ph - ImGuiManager.MONO_SIZE) * 0.5f, tcol, bindText);
        }

        float textX = innerX;
        if (collapsible) {
            float exp = peek(id + ".exp");
            float cx = innerX + 6.0f;
            float cy = y + rowH * 0.5f;
            int ccol = Theme.mix(Theme.FAINT, Theme.TEXT, Math.max(exp, hdrHov));
            dl.addTriangleFilled(
                cx + lerp(-2.0f, -4.5f, exp), cy + lerp(-4.5f, -2.0f, exp),
                cx + lerp(3.5f, 4.5f, exp), cy + lerp(0.0f, -2.0f, exp),
                cx + lerp(-2.0f, 0.0f, exp), cy + lerp(4.5f, 3.5f, exp),
                ccol);
            textX = innerX + 20.0f;
        }

        dl.addText(medium(), ImGuiManager.MEDIUM_SIZE, textX,
            y + (rowH - ImGuiManager.MEDIUM_SIZE) * 0.5f,
            Theme.mix(Theme.TEXT, Theme.ACCENT, hdrHov * 0.35f), title);

        cardHeaderBottom = y + rowH;
        ImGui.setCursorScreenPos(innerX, y + rowH);
        ImGui.dummy(innerW, 0.0f);
        return pillClicked;
    }

    public static boolean cardBodyBegin(String id) {
        float p = tween(id + ".exp", isExpanded(id) ? 1.0f : 0.0f, EXPAND_DUR);
        float e = smoother(p);
        Float m = bodyHeights.get(id);
        float base = m == null ? 0.0f : m;
        float h = base * e;
        cardBodyExplicit = true;
        cardBodyBottom = cardHeaderBottom + h;
        if (p <= 0.0f || (base > 0.5f && h < 1.0f)) {
            bodyOpen = false;
            return false;
        }
        Theme.setLayerAlpha(e * e);
        ImGui.setCursorScreenPos(innerX, cardHeaderBottom);
        ImGui.beginChild(id + ".body", Math.max(innerW, 1.0f), Math.max(h, 1.0f), false,
            ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        bodyStartY = ImGui.getCursorScreenPosY();
        ImGui.setCursorScreenPos(innerX, bodyStartY + ROW_GAP);
        bodyOpen = true;
        return true;
    }

    public static void cardBodyEnd(String id) {
        if (!bodyOpen) {
            return;
        }
        bodyHeights.put(id, ImGui.getCursorScreenPosY() - bodyStartY + 2.0f);
        ImGui.endChild();
        Theme.setLayerAlpha(1.0f);
        bodyOpen = false;
    }

    public static void cardDivider() {
        float y = ImGui.getCursorScreenPosY();
        ImGui.getWindowDrawList().addLine(innerX, y, innerX + innerW, y, Theme.col(Theme.BORDER, 0.8f), 1.0f);
        ImGui.setCursorScreenPos(innerX, y + 10.0f);
    }

    public static boolean beginSub(String id, boolean visible) {
        float p = tween(id + ".sv", visible ? 1.0f : 0.0f, SUB_DUR);
        SubFrame frame = new SubFrame();
        float e = smoother(p);
        Float m = subHeights.get(id);
        float base = m == null ? 0.0f : m;
        float h = base * e;
        frame.entryY = ImGui.getCursorScreenPosY();
        frame.baseFull = base;
        frame.e = e;
        if (p <= 0.0f) {
            frame.open = false;
            subStack.push(frame);
            return false;
        }
        if (base > 0.5f && h < 1.0f) {
            frame.open = false;
            subStack.push(frame);
            return false;
        }
        innerX += SUB_INDENT;
        innerW -= SUB_INDENT;
        frame.h = Math.max(h, 1.0f);
        frame.topY = frame.entryY;
        frame.layerSaved = Theme.layerAlpha();
        frame.open = true;
        Theme.setLayerAlpha(frame.layerSaved * e * e);
        ImGui.setCursorScreenPos(innerX, frame.topY);
        ImGui.beginChild(id + ".subc", Math.max(innerW, 1.0f), frame.h, false,
            ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
        subStack.push(frame);
        return true;
    }

    public static void endSub(String id) {
        if (subStack.isEmpty()) {
            return;
        }
        SubFrame frame = subStack.pop();
        if (frame.open) {
            subHeights.put(id, ImGui.getCursorScreenPosY() - frame.topY + 4.0f);
            ImGui.endChild();
            innerX -= SUB_INDENT;
            innerW += SUB_INDENT;
            float lineBot = frame.topY + frame.h - 2.0f;
            if (lineBot > frame.topY + 4.0f) {
                ImGui.getWindowDrawList().addLine(innerX + 5.0f, frame.topY + 2.0f, innerX + 5.0f, lineBot,
                    Theme.col(Theme.ACCENT, 0.35f), 2.0f);
            }
            Theme.setLayerAlpha(frame.layerSaved);
        }
        if (frame.e > 0.0f) {
            float nextY = frame.entryY + (frame.baseFull + ROW_GAP) * frame.e;
            ImGui.setCursorScreenPos(innerX, nextY);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 10.0f, 0.0f);
            ImGui.dummy(Math.max(innerW, 1.0f), 0.0f);
            ImGui.popStyleVar();
        }
    }

    private static void drawPill(ImDrawList dl, float x, float y, float on) {
        dl.addRectFilled(x, y, x + PILL_W, y + PILL_H, Theme.mix(Theme.FIELD, Theme.ACCENT, on), PILL_H * 0.5f);
        dl.addRect(x, y, x + PILL_W, y + PILL_H, Theme.col(Theme.BORDER, 1.0f - on), PILL_H * 0.5f);
        float kr = 8.0f;
        float kx = lerp(x + 3.0f + kr, x + PILL_W - 3.0f - kr, on);
        dl.addCircleFilled(kx, y + PILL_H * 0.5f, kr, Theme.mix(Theme.KNOB_OFF, Theme.BG, on));
    }

    public static boolean toggle(String id, String label, ImBoolean value) {
        return toggle(id, label, value, null);
    }

    public static boolean toggle(String id, String label, ImBoolean value, String tooltip) {
        float y = ImGui.getCursorScreenPosY();
        ImGui.setCursorScreenPos(innerX, y);
        boolean clicked = ImGui.invisibleButton(id, Math.max(innerW, 1.0f), ROW_H) && !blocked();
        boolean hovered = ImGui.isItemHovered() && !blocked();
        if (clicked) {
            value.set(!value.get());
        }
        requestTooltip(hovered, tooltip);
        float on = anim(id + ".v", value.get() ? 1.0f : 0.0f, SP_TOGGLE);
        float hov = anim(id + ".h", hovered ? 1.0f : 0.0f, SP_HOVER);
        ImDrawList dl = ImGui.getWindowDrawList();
        int labelCol = Theme.mix(Theme.DIM, Theme.TEXT, Math.max(on, hov * 0.6f));
        dl.addText(body(), ImGuiManager.BODY_SIZE, innerX,
            y + (ROW_H - ImGuiManager.BODY_SIZE) * 0.5f, labelCol, label);
        drawPill(dl, innerX + innerW - PILL_W, y + (ROW_H - PILL_H) * 0.5f, on);
        return clicked;
    }

    public static void slider(String id, String label, float[] v, float min, float max, String fmt) {
        slider(id, label, v, min, max, fmt, null);
    }

    public static void slider(String id, String label, float[] v, float min, float max, String fmt, String tooltip) {
        float labelH = 22.0f;
        float trackH = 16.0f;
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addText(body(), ImGuiManager.BODY_SIZE, innerX, y, Theme.col(Theme.DIM), label);
        String valText = String.format(Locale.ROOT, fmt, v[0]);
        ImGui.pushFont(mono());
        ImGui.calcTextSize(measure, valText);
        ImGui.popFont();
        dl.addText(mono(), ImGuiManager.MONO_SIZE, innerX + innerW - measure.x, y + 2.0f,
            Theme.col(Theme.TEXT), valText);

        float margin = GRAB_R + 2.0f;
        float trackX = innerX + margin;
        float trackW = innerW - margin * 2.0f;
        ImGui.setCursorScreenPos(innerX, y + labelH);
        ImGui.invisibleButton(id, Math.max(innerW, 1.0f), trackH);
        boolean hovered = ImGui.isItemHovered() && !blocked();
        boolean active = ImGui.isItemActive() && !blocked();
        requestTooltip(hovered, tooltip);
        if (active) {
            float t = clamp01((ImGui.getMousePosX() - trackX) / trackW);
            v[0] = min + t * (max - min);
        }
        float t = max > min ? clamp01((v[0] - min) / (max - min)) : 0.0f;
        float hov = anim(id + ".h", hovered || active ? 1.0f : 0.0f, SP_HOVER);
        float cy = y + labelH + trackH * 0.5f;
        float th = 4.0f + 2.0f * hov;
        dl.addRectFilled(trackX, cy - th * 0.5f, trackX + trackW, cy + th * 0.5f, Theme.col(Theme.FIELD), th * 0.5f);
        if (t > 0.0f) {
            dl.addRectFilled(trackX, cy - th * 0.5f, trackX + trackW * t, cy + th * 0.5f,
                Theme.col(Theme.ACCENT, 0.85f + 0.15f * hov), th * 0.5f);
        }
        dl.addCircleFilled(trackX + trackW * t, cy, GRAB_R + 1.5f * hov, Theme.col(Theme.TEXT));
    }

    public static void sliderInt(String id, String label, int[] v, int min, int max, String fmt) {
        float[] tmp = {v[0]};
        slider(id, label, tmp, min, max, fmt);
        v[0] = Math.round(tmp[0]);
    }

    public static void rangeSlider(String id, String label, float[] v, float min, float max, String fmt) {
        rangeSlider(id, label, v, min, max, fmt, null);
    }

    public static void rangeSlider(String id, String label, float[] v, float min, float max, String fmt, String tooltip) {
        float labelH = 22.0f;
        float trackH = 16.0f;
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addText(body(), ImGuiManager.BODY_SIZE, innerX, y, Theme.col(Theme.DIM), label);
        String valText = String.format(Locale.ROOT, fmt, v[0]) + " - " + String.format(Locale.ROOT, fmt, v[1]);
        ImGui.pushFont(mono());
        ImGui.calcTextSize(measure, valText);
        ImGui.popFont();
        dl.addText(mono(), ImGuiManager.MONO_SIZE, innerX + innerW - measure.x, y + 2.0f,
            Theme.col(Theme.TEXT), valText);

        float margin = GRAB_R + 2.0f;
        float trackX = innerX + margin;
        float trackW = innerW - margin * 2.0f;
        ImGui.setCursorScreenPos(innerX, y + labelH);
        ImGui.invisibleButton(id, Math.max(innerW, 1.0f), trackH);
        boolean hovered = ImGui.isItemHovered() && !blocked();
        boolean active = ImGui.isItemActive() && !blocked();
        requestTooltip(hovered, tooltip);
        float mt = trackW > 0.0f ? clamp01((ImGui.getMousePosX() - trackX) / trackW) : 0.0f;
        float tLow = max > min ? clamp01((v[0] - min) / (max - min)) : 0.0f;
        float tHigh = max > min ? clamp01((v[1] - min) / (max - min)) : 0.0f;
        if (ImGui.isItemActivated() && !blocked()) {
            rangeGrab.put(id, Math.abs(mt - tLow) <= Math.abs(mt - tHigh) ? 0 : 1);
        }
        if (active) {
            Integer g = rangeGrab.get(id);
            float val = min + mt * (max - min);
            if (g != null && g == 1) {
                v[1] = Math.max(val, v[0]);
            } else {
                v[0] = Math.min(val, v[1]);
            }
            tLow = max > min ? clamp01((v[0] - min) / (max - min)) : 0.0f;
            tHigh = max > min ? clamp01((v[1] - min) / (max - min)) : 0.0f;
        }
        float hov = anim(id + ".h", hovered || active ? 1.0f : 0.0f, SP_HOVER);
        float cy = y + labelH + trackH * 0.5f;
        float th = 4.0f + 2.0f * hov;
        dl.addRectFilled(trackX, cy - th * 0.5f, trackX + trackW, cy + th * 0.5f, Theme.col(Theme.FIELD), th * 0.5f);
        dl.addRectFilled(trackX + trackW * tLow, cy - th * 0.5f, trackX + trackW * tHigh, cy + th * 0.5f,
            Theme.col(Theme.ACCENT, 0.85f + 0.15f * hov), th * 0.5f);
        dl.addCircleFilled(trackX + trackW * tLow, cy, GRAB_R + 1.5f * hov, Theme.col(Theme.TEXT));
        dl.addCircleFilled(trackX + trackW * tHigh, cy, GRAB_R + 1.5f * hov, Theme.col(Theme.TEXT));
    }

    public static void segmented(String id, String label, ImInt value, String[] options, String tooltip) {
        float labelH = label != null ? 22.0f : 0.0f;
        float barH = 30.0f;
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        if (label != null) {
            dl.addText(body(), ImGuiManager.BODY_SIZE, innerX, y, Theme.col(Theme.DIM), label);
        }
        float barY = y + labelH;
        ImGui.setCursorScreenPos(innerX, barY);
        boolean clicked = ImGui.invisibleButton(id, Math.max(innerW, 1.0f), barH) && !blocked();
        boolean hovered = ImGui.isItemHovered() && !blocked();
        requestTooltip(hovered, tooltip);
        int n = options.length;
        float segW = innerW / n;
        if (clicked) {
            int idx = (int) ((ImGui.getMousePosX() - innerX) / segW);
            value.set(Math.max(0, Math.min(n - 1, idx)));
        }
        float pos = anim(id + ".i", value.get(), SP_SEG);
        dl.addRectFilled(innerX, barY, innerX + innerW, barY + barH, Theme.col(Theme.FIELD), 8.0f);
        float ax = innerX + pos * segW;
        dl.addRectFilled(ax + 2.0f, barY + 2.0f, ax + segW - 2.0f, barY + barH - 2.0f, Theme.col(Theme.ACCENT), 6.0f);
        for (int i = 0; i < n; i++) {
            ImGui.pushFont(body());
            ImGui.calcTextSize(measure, options[i]);
            ImGui.popFont();
            float tx = innerX + i * segW + (segW - measure.x) * 0.5f;
            float closeness = clamp01(1.0f - Math.abs(pos - i));
            int tcol = Theme.mix(Theme.DIM, Theme.BG, closeness);
            dl.addText(body(), ImGuiManager.BODY_SIZE, tx, barY + (barH - ImGuiManager.BODY_SIZE) * 0.5f, tcol, options[i]);
        }
    }

    private static String preview(String[] names, ImBoolean[] values) {
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (values[i].get()) {
                count++;
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(names[i]);
            }
        }
        if (count == 0) {
            return "None";
        }
        if (count == names.length) {
            return "All";
        }
        return sb.toString();
    }

    public static void multiSelect(String id, String label, String[] names, ImBoolean[] values) {
        float y = ImGui.getCursorScreenPosY();
        ImGui.setCursorScreenPos(innerX, y);
        boolean clicked = ImGui.invisibleButton(id, Math.max(innerW, 1.0f), ROW_H) && !blocked();
        boolean hovered = ImGui.isItemHovered() && !blocked();
        float hov = anim(id + ".h", hovered ? 1.0f : 0.0f, SP_HOVER);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addText(body(), ImGuiManager.BODY_SIZE, innerX,
            y + (ROW_H - ImGuiManager.BODY_SIZE) * 0.5f,
            Theme.mix(Theme.DIM, Theme.TEXT, hov * 0.6f), label);
        String prev = preview(names, values);
        ImGui.pushFont(body());
        ImGui.calcTextSize(measure, prev);
        ImGui.popFont();
        float chevX = innerX + innerW - 8.0f;
        float midY = y + ROW_H * 0.5f;
        boolean isOpen = id.equals(dropOpenId);
        float ar = anim(id + ".ar", isOpen ? 1.0f : 0.0f, SP_DROP);
        drawArrow(dl, chevX, midY, ar, Theme.mix(Theme.FAINT, Theme.TEXT, Math.max(hov, ar)));
        dl.addText(body(), ImGuiManager.BODY_SIZE, chevX - 14.0f - measure.x,
            y + (ROW_H - ImGuiManager.BODY_SIZE) * 0.5f,
            Theme.mix(Theme.TEXT, Theme.ACCENT, hov * 0.5f), prev);
        if (clicked) {
            dropOpenId = isOpen ? null : id;
        }
        if (id.equals(dropOpenId)) {
            setDrop(id, true, names, values, null, innerX, y, innerW, ROW_H);
        }
    }

    public static void selectOne(String id, String label, ImInt value, String[] options) {
        float y = ImGui.getCursorScreenPosY();
        ImGui.setCursorScreenPos(innerX, y);
        boolean clicked = ImGui.invisibleButton(id, Math.max(innerW, 1.0f), ROW_H) && !blocked();
        boolean hovered = ImGui.isItemHovered() && !blocked();
        float hov = anim(id + ".h", hovered ? 1.0f : 0.0f, SP_HOVER);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addText(body(), ImGuiManager.BODY_SIZE, innerX,
            y + (ROW_H - ImGuiManager.BODY_SIZE) * 0.5f,
            Theme.mix(Theme.DIM, Theme.TEXT, hov * 0.6f), label);
        String prev = options[Math.max(0, Math.min(options.length - 1, value.get()))];
        ImGui.pushFont(body());
        ImGui.calcTextSize(measure, prev);
        ImGui.popFont();
        float chevX = innerX + innerW - 8.0f;
        boolean isOpen = id.equals(dropOpenId);
        float ar = anim(id + ".ar", isOpen ? 1.0f : 0.0f, SP_DROP);
        drawArrow(dl, chevX, y + ROW_H * 0.5f, ar, Theme.mix(Theme.FAINT, Theme.TEXT, Math.max(hov, ar)));
        dl.addText(body(), ImGuiManager.BODY_SIZE, chevX - 14.0f - measure.x,
            y + (ROW_H - ImGuiManager.BODY_SIZE) * 0.5f,
            Theme.mix(Theme.TEXT, Theme.ACCENT, hov * 0.5f), prev);
        if (clicked) {
            dropOpenId = isOpen ? null : id;
        }
        if (id.equals(dropOpenId)) {
            setDrop(id, false, options, null, value, innerX, y, innerW, ROW_H);
        }
    }

    private static void setDrop(String id, boolean multi, String[] names, ImBoolean[] values, ImInt valueInt,
                                float rx, float ry, float rw, float rh) {
        if (drop == null || !id.equals(drop.id)) {
            drop = new DropState();
        }
        float dw = Math.min(DROP_W, rw);
        drop.id = id;
        drop.multi = multi;
        drop.names = names;
        drop.values = values;
        drop.valueInt = valueInt;
        drop.x = rx + rw - dw;
        drop.y = ry + rh + 4.0f;
        drop.w = dw;
        drop.rowX = rx;
        drop.rowY = ry;
        drop.rowW = rw;
        drop.rowH = rh;
    }

    public static void renderDropdowns() {
        if (drop == null) {
            dropActive = false;
            return;
        }
        boolean open = drop.id.equals(dropOpenId);
        float a = anim(drop.id + ".d", open ? 1.0f : 0.0f, SP_DROP);
        float e = smooth(a);
        int n = drop.names.length;
        int maxVis = Math.min(n, DROP_MAX_VISIBLE);
        float fullH = n * OPT_H + DROP_PAD * 2.0f;
        float scrollH = maxVis * OPT_H + DROP_PAD * 2.0f;
        float h = scrollH * e;
        if (!open && h < 2.0f) {
            drop = null;
            dropActive = false;
            return;
        }
        if (n > maxVis) {
            drop.scrollOffset = Math.max(0, Math.min(drop.scrollOffset, n - maxVis));
        } else {
            drop.scrollOffset = 0;
        }
        float x = drop.x;
        float y = drop.y;
        float w = drop.w;
        float savedLayer = Theme.layerAlpha();
        Theme.setLayerAlpha(savedLayer * clamp01(e * 2.5f));

        ImDrawList dl = ImGui.getForegroundDrawList();
        dl.pushClipRect(x - 2.0f, y - 2.0f, x + w + 2.0f, y + h + 2.0f, true);
        dl.addRectFilled(x, y, x + w, y + scrollH, Theme.col(Theme.CARD), 8.0f);
        dl.addRect(x, y, x + w, y + h, Theme.col(Theme.BORDER), 8.0f);

        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        boolean fullyOpen = open && a > 0.85f;
        boolean click = ImGui.isMouseClicked(0);

        boolean inPanel = mx >= x && mx <= x + w && my >= y && my <= y + scrollH;
        if (fullyOpen && inPanel && n > maxVis) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (wheel != 0) {
                drop.scrollOffset = Math.max(0, Math.min(drop.scrollOffset - (int) Math.signum(wheel), n - maxVis));
            }
        }
        int scrollStart = drop.scrollOffset;
        int scrollEnd = Math.min(n, scrollStart + maxVis);
        dl.pushClipRect(x, y + DROP_PAD, x + w, y + scrollH - DROP_PAD, true);
        for (int i = scrollStart; i < scrollEnd; i++) {
            float oy = y + DROP_PAD + (i - scrollStart) * OPT_H;
            boolean selected = drop.multi ? drop.values[i].get() : drop.valueInt.get() == i;
            boolean rowHov = fullyOpen && mx >= x && mx <= x + w && my >= oy && my <= oy + OPT_H;
            float hov = anim(drop.id + ".o" + i + ".h", rowHov ? 1.0f : 0.0f, SP_HOVER);
            float sel = anim(drop.id + ".o" + i + ".s", selected ? 1.0f : 0.0f, SP_SELECT);
            if (hov > 0.01f) {
                dl.addRectFilled(x + 4.0f, oy + 1.0f, x + w - 4.0f, oy + OPT_H - 1.0f,
                    Theme.col(Theme.TEXT, 0.05f * hov), 6.0f);
            }
            float dotX = x + 14.0f;
            float dotY = oy + OPT_H * 0.5f;
            if (sel > 0.01f) {
                dl.addCircleFilled(dotX, dotY, 4.0f * Math.min(1.0f, sel + 0.35f), Theme.col(Theme.ACCENT, sel));
            }
            if (sel < 0.99f) {
                dl.addCircle(dotX, dotY, 4.0f, Theme.col(Theme.FAINT, 1.0f - sel));
            }
            dl.addText(body(), ImGuiManager.BODY_SIZE, x + 27.0f,
                oy + (OPT_H - ImGuiManager.BODY_SIZE) * 0.5f,
                Theme.mix(Theme.DIM, Theme.TEXT, Math.max(sel, hov)), drop.names[i]);
            if (rowHov && click) {
                if (drop.multi) {
                    drop.values[i].set(!drop.values[i].get());
                } else {
                    drop.valueInt.set(i);
                    dropOpenId = null;
                }
            }
        }
        dl.popClipRect();
        if (n > maxVis) {
            float sbX = x + w - 10.0f;
            float sbY = y + DROP_PAD;
            float sbH = scrollH - DROP_PAD * 2.0f;
            float thumbH = sbH * maxVis / n;
            float thumbY = sbY + (sbH - thumbH) * drop.scrollOffset / (n - maxVis);
            dl.addRectFilled(sbX, sbY, sbX + 4.0f, sbY + sbH, Theme.col(Theme.FIELD, 0.6f), 2.0f);
            dl.addRectFilled(sbX, thumbY, sbX + 4.0f, thumbY + thumbH, Theme.col(Theme.TEXT, 0.5f), 2.0f);
        }
        dl.popClipRect();
        Theme.setLayerAlpha(savedLayer);

        dropActive = true;
        dropPanelX = x;
        dropPanelY = y;
        dropPanelW = w;
        dropPanelH = scrollH;

        if (open && click) {
            boolean inRow = mx >= drop.rowX && mx <= drop.rowX + drop.rowW
                && my >= drop.rowY && my <= drop.rowY + drop.rowH;
            if (!inPanel && !inRow) {
                dropOpenId = null;
            }
        }
    }

    private static void drawArrow(ImDrawList dl, float cx, float cy, float t, int col) {
        float size = 14.0f;
        Icons.drawRotated(dl, "arrow-right", cx - size * 0.5f, cy - size * 0.5f, size, col,
            smooth(t) * (float) (Math.PI * 0.5));
    }

    public static boolean popupOption(String id, String label, boolean selected) {
        float w = 176.0f;
        float h = 28.0f;
        ImVec2 p = ImGui.getCursorScreenPos();
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        float hov = anim(id + ".h", hovered ? 1.0f : 0.0f, SP_HOVER);
        float sel = anim(id + ".s", selected ? 1.0f : 0.0f, SP_SELECT);
        ImDrawList dl = ImGui.getWindowDrawList();
        if (hov > 0.01f) {
            dl.addRectFilled(p.x, p.y, p.x + w, p.y + h, Theme.col(Theme.TEXT, 0.05f * hov), 6.0f);
        }
        float dotX = p.x + 13.0f;
        float dotY = p.y + h * 0.5f;
        if (sel > 0.01f) {
            dl.addCircleFilled(dotX, dotY, 4.0f * Math.min(1.0f, sel + 0.35f), Theme.col(Theme.ACCENT, sel));
        }
        if (sel < 0.99f) {
            dl.addCircle(dotX, dotY, 4.0f, Theme.col(Theme.FAINT, 1.0f - sel));
        }
        dl.addText(body(), ImGuiManager.BODY_SIZE, p.x + 26.0f,
            p.y + (h - ImGuiManager.BODY_SIZE) * 0.5f,
            Theme.mix(Theme.DIM, Theme.TEXT, Math.max(sel, hov)), label);
        return clicked;
    }

    public static boolean button(String id, String label, float w, float h, int kind) {
        return button(id, label, null, w, h, kind);
    }

    public static boolean button(String id, String label, String icon, float w, float h, int kind) {
        ImVec2 p = ImGui.getCursorScreenPos();
        boolean clicked = ImGui.invisibleButton(id, w, h) && !blocked();
        boolean hovered = ImGui.isItemHovered() && !blocked();
        float hov = anim(id + ".h", hovered ? 1.0f : 0.0f, SP_HOVER);
        ImDrawList dl = ImGui.getWindowDrawList();
        int bg;
        int txt;
        if (kind == 1) {
            bg = Theme.mix(Theme.ACCENT, Theme.TEXT, 0.25f * hov);
            txt = Theme.col(Theme.BG);
        } else if (kind == 2) {
            bg = Theme.mix(Theme.FIELD, Theme.DANGER, 0.15f + 0.30f * hov);
            txt = Theme.mix(Theme.DANGER, Theme.TEXT, hov * 0.7f);
        } else {
            bg = Theme.mix(Theme.FIELD, Theme.FIELD_HI, hov);
            txt = Theme.mix(Theme.DIM, Theme.TEXT, 0.4f + 0.6f * hov);
        }
        dl.addRectFilled(p.x, p.y, p.x + w, p.y + h, bg, 8.0f);
        if (kind == 0) {
            dl.addRect(p.x, p.y, p.x + w, p.y + h, Theme.col(Theme.BORDER), 8.0f);
        }
        ImGui.pushFont(body());
        ImGui.calcTextSize(measure, label);
        ImGui.popFont();
        float iconSize = icon != null ? ImGuiManager.BODY_SIZE : 0.0f;
        float gap = icon != null ? 8.0f : 0.0f;
        float startX = p.x + (w - (iconSize + gap + measure.x)) * 0.5f;
        if (icon != null) {
            Icons.draw(dl, icon, startX, p.y + (h - iconSize) * 0.5f, iconSize, txt);
        }
        dl.addText(body(), ImGuiManager.BODY_SIZE, startX + iconSize + gap,
            p.y + (h - ImGuiManager.BODY_SIZE) * 0.5f, txt, label);
        return clicked;
    }

    public static boolean listRow(String id, String label, boolean selected) {
        float w = ImGui.getContentRegionAvailX();
        float h = 28.0f;
        ImVec2 p = ImGui.getCursorScreenPos();
        boolean clicked = ImGui.invisibleButton(id, Math.max(w, 1.0f), h) && !blocked();
        boolean hovered = ImGui.isItemHovered() && !blocked();
        float hov = anim(id + ".h", hovered ? 1.0f : 0.0f, SP_HOVER);
        float sel = anim(id + ".s", selected ? 1.0f : 0.0f, SP_SELECT);
        ImDrawList dl = ImGui.getWindowDrawList();
        if (hov > 0.01f) {
            dl.addRectFilled(p.x, p.y, p.x + w, p.y + h, Theme.col(Theme.TEXT, 0.04f * hov), 6.0f);
        }
        if (sel > 0.01f) {
            dl.addRectFilled(p.x, p.y, p.x + w, p.y + h, Theme.col(Theme.ACCENT, 0.13f * sel), 6.0f);
        }
        int tcol = sel > 0.5f ? Theme.mix(Theme.TEXT, Theme.ACCENT, sel) : Theme.mix(Theme.DIM, Theme.TEXT, hov);
        dl.addText(body(), ImGuiManager.BODY_SIZE, p.x + 10.0f,
            p.y + (h - ImGuiManager.BODY_SIZE) * 0.5f, tcol, label);
        return clicked;
    }

    public static void colorRow(String id, String label, float[] color, int flags) {
        float y = ImGui.getCursorScreenPosY();
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addText(body(), ImGuiManager.BODY_SIZE, innerX,
            y + (ROW_H - ImGuiManager.BODY_SIZE) * 0.5f, Theme.col(Theme.DIM), label);
        float sw = 25.0f;
        ImGui.setCursorScreenPos(innerX + innerW - sw, y + (ROW_H - sw) * 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 6.0f, 4.0f);
        ImGui.colorEdit4(id, color, flags);
        ImGui.popStyleVar();
        ImGui.setCursorScreenPos(innerX, y + ROW_H);
        ImGui.dummy(innerW, 0.0f);
    }

    public static void textRow(String text) {
        float y = ImGui.getCursorScreenPosY();
        ImGui.getWindowDrawList().addText(body(), ImGuiManager.BODY_SIZE, innerX, y, Theme.col(Theme.DIM), text);
        ImGui.setCursorScreenPos(innerX, y + ImGuiManager.BODY_SIZE + 6.0f);
        ImGui.dummy(innerW, 0.0f);
    }

    public static void statusText(String text) {
        float y = ImGui.getCursorScreenPosY();
        ImGui.getWindowDrawList().addText(mono(), ImGuiManager.MONO_SIZE, innerX, y, Theme.col(Theme.FAINT), text);
        ImGui.setCursorScreenPos(innerX, y + ImGuiManager.MONO_SIZE + 4.0f);
        ImGui.dummy(innerW, 0.0f);
    }

    public static float innerX() {
        return innerX;
    }

    public static float innerW() {
        return innerW;
    }

    public static void hint(boolean hovered, String tip) {
        requestTooltip(hovered && !blocked(), tip);
    }

    private static void requestTooltip(boolean hovered, String tip) {
        if (hovered && tip != null) {
            pendingTooltip = tip;
        }
    }

    public static void renderTooltips() {
        double now = ImGui.getTime();
        float target;
        if (pendingTooltip != null) {
            if (!pendingTooltip.equals(shownTooltip)) {
                shownTooltip = pendingTooltip;
                shownSince = now;
            }
            target = (now - shownSince) >= TOOLTIP_DELAY ? 1.0f : 0.0f;
        } else {
            target = 0.0f;
        }
        float dt = ImGui.getIO().getDeltaTime();
        tooltipAlpha += (target - tooltipAlpha) * Math.min(1.0f, dt * SP_TOOLTIP);
        if (tooltipAlpha > 0.02f && shownTooltip != null) {
            drawTooltipBox(shownTooltip, clamp01(tooltipAlpha));
        } else if (pendingTooltip == null && tooltipAlpha <= 0.02f) {
            shownTooltip = null;
        }
        pendingTooltip = null;
    }

    private static void drawTooltipBox(String text, float alpha) {
        float maxW = 300.0f;
        float padX = 11.0f;
        float padY = 8.0f;
        float lineH = ImGuiManager.BODY_SIZE + 4.0f;
        java.util.List<String> lines = wrapText(text, maxW);
        float boxW = 0.0f;
        for (String line : lines) {
            ImGui.pushFont(body());
            ImGui.calcTextSize(measure, line);
            ImGui.popFont();
            if (measure.x > boxW) {
                boxW = measure.x;
            }
        }
        boxW += padX * 2.0f;
        float boxH = lines.size() * lineH - 4.0f + padY * 2.0f;

        float mx = ImGui.getMousePosX() + 16.0f;
        float my = ImGui.getMousePosY() + 20.0f;
        float dispW = ImGui.getIO().getDisplaySizeX();
        float dispH = ImGui.getIO().getDisplaySizeY();
        if (mx + boxW > dispW - 6.0f) {
            mx = dispW - 6.0f - boxW;
        }
        if (my + boxH > dispH - 6.0f) {
            my = ImGui.getMousePosY() - boxH - 12.0f;
        }
        my += (1.0f - alpha) * 6.0f;

        ImDrawList dl = ImGui.getForegroundDrawList();
        dl.addRectFilled(mx, my, mx + boxW, my + boxH, Theme.col(Theme.CARD, alpha), 8.0f);
        dl.addRectFilled(mx, my + 8.0f, mx + 2.5f, my + boxH - 8.0f, Theme.col(Theme.ACCENT, alpha), 2.0f);
        dl.addRect(mx, my, mx + boxW, my + boxH, Theme.col(Theme.BORDER, alpha), 8.0f);
        float ty = my + padY;
        for (String line : lines) {
            dl.addText(body(), ImGuiManager.BODY_SIZE, mx + padX + 4.0f, ty, Theme.col(Theme.TEXT, alpha), line);
            ty += lineH;
        }
    }

    private static java.util.List<String> wrapText(String text, float maxW) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            ImGui.pushFont(body());
            ImGui.calcTextSize(measure, candidate);
            ImGui.popFont();
            if (measure.x > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
