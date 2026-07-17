package cc.zolsi.mod.gui;

import cc.zolsi.mod.ZolsiCore;
import cc.zolsi.mod.config.LocalConfig;
import cc.zolsi.mod.feature.combat.AimAssistFeature;
import cc.zolsi.mod.feature.visuals.ArrayListFeature;
import cc.zolsi.mod.feature.utility.AntiBotFeature;
import cc.zolsi.mod.feature.utility.AutoJumpResetFeature;
import cc.zolsi.mod.feature.combat.CritAssistFeature;
import cc.zolsi.mod.feature.visuals.EspFeature;
import cc.zolsi.mod.feature.visuals.HitmarkerFeature;
import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.feature.visuals.NametagsFeature;
import cc.zolsi.mod.feature.utility.NoJumpDelayFeature;
import cc.zolsi.mod.feature.utility.SprintFeature;
import cc.zolsi.mod.feature.combat.TriggerbotFeature;
import com.google.gson.Gson;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

public final class ClickGui {

    private static final String[] CATEGORIES = {"Combat", "Utility", "Visuals", "Settings"};
    private static final String[] CATEGORY_ICONS = {"combat", "utility", "visuals", "settings"};
    private static final String[] TB_CRIT_MODES = {"Normal", "Prioritize", "Prefer"};
    private static final String[] AA_INPUT_MODES = {"Always", "Mouse", "Silent"};
    private static final String[] AA_HITBOXES = {"Head", "Body", "Nearest"};
    private static final String[] ARRAY_POSITIONS = {"Top Right", "Top Left", "Bottom Right", "Bottom Left"};
    private static final String[] TARGETS_PMI = {"Players", "Mobs", "Invisibles"};
    private static final String[] TARGETS_PMF = {"Players", "Mobs", "Friendly"};
    private static final String[] AL_CATS = {"Combat", "Utility", "Visuals"};

    private static final float HEADER_H = 54.0f;
    private static final float RAIL_W = 150.0f;
    private static final float FULL_W = 720.0f;
    private static final float FULL_H = 540.0f;
    private static final float TAB_SLIDE_DUR = 0.3125f;
    private static final float TAB_SLIDE_TRAVEL = 1.0f;
    private static final float UNHOOK_COVER_DUR = 1.0f;
    private static final float UNHOOK_HOLD_DUR = 5.0f;
    private static final float UNHOOK_DOT_INTERVAL = 0.90f;
    private static final int UNHOOK_TEXT_SIZE = 34;
    private static final int UNHOOK_NONE = 0;
    private static final int UNHOOK_COVER = 1;
    private static final int UNHOOK_HOLD = 2;
    private static final int UNHOOK_CLOSING = 3;

    private final ImVec2 textSize = new ImVec2();
    private final ImString configName = new ImString(64);
    private final Gson gson = new Gson();
    private final ImBoolean[] tbTargets;
    private final ImBoolean[] caTargets;
    private final ImBoolean[] aaTargets;
    private final ImBoolean[] espTargets;
    private final ImBoolean[] nametagsTargets;
    private final ImBoolean[] alCats;
    private Keybind listening;
    private boolean configListLoaded;
    private int selectedTab;
    private int prevTab;
    private float tabSlide = 1.0f;
    private int tabDir;
    private float scrollCurrent;
    private float scrollTarget;
    private float lastAppliedScroll;
    private float menuAnim;
    private float winPosX = 80.0f;
    private float winPosY = 80.0f;
    private boolean posInit;
    private float scrollbarW = 8.0f;
    private int unhookPhase;
    private float unhookTimer;
    private float unhookOx;
    private float unhookOy;

    public ClickGui() {
        TriggerbotFeature tb = TriggerbotFeature.get();
        tbTargets = new ImBoolean[]{tb.targetPlayers, tb.targetMobs, tb.targetInvisibles};
        CritAssistFeature ca = CritAssistFeature.get();
        caTargets = new ImBoolean[]{ca.targetPlayers, ca.targetMobs, ca.targetInvisibles};
        AimAssistFeature aa = AimAssistFeature.get();
        aaTargets = new ImBoolean[]{aa.targetPlayers, aa.targetMobs, aa.targetInvisibles};
        EspFeature esp = EspFeature.get();
        espTargets = new ImBoolean[]{esp.targetPlayers, esp.targetMobs, esp.targetFriendly};
        NametagsFeature nt = NametagsFeature.get();
        nametagsTargets = new ImBoolean[]{nt.targetPlayers, nt.targetMobs, nt.targetInvisibles};
        ArrayListFeature al = ArrayListFeature.get();
        alCats = new ImBoolean[]{al.catCombat, al.catUtility, al.catVisuals};
    }

    public void draw() {
        Widgets.resetFrame();
        captureListening();

        boolean open = ZolsiCore.get().isMenuOpen();
        float dt = ImGui.getIO().getDeltaTime();
        if (unhookPhase == UNHOOK_COVER || unhookPhase == UNHOOK_HOLD) {
            open = true;
        }
        menuAnim += ((open ? 1.0f : 0.0f) - menuAnim) * Math.min(1.0f, dt * 8.0f);
        if (menuAnim < 0.01f && !open) {
            menuAnim = 0.0f;
            Widgets.clearDropdown();
            if (unhookPhase == UNHOOK_CLOSING) {
                unhookPhase = UNHOOK_NONE;
                ImGuiManager.get().finishUnhook();
            }
            return;
        }
        if (!open) {
            Widgets.clearDropdown();
        }

        String pending = LocalConfig.get().consumePendingApply();
        if (pending != null) {
            applyControls(pending);
        }

        if (!posInit) {
            float dispW = ImGui.getIO().getDisplaySizeX();
            float dispH = ImGui.getIO().getDisplaySizeY();
            if (dispW > 0.0f && dispH > 0.0f) {
                winPosX = (dispW - FULL_W) * 0.5f;
                winPosY = (dispH - FULL_H) * 0.5f;
                posInit = true;
            }
        }

        float ease = menuAnim * menuAnim * (3.0f - 2.0f * menuAnim);
        float scale = 0.60f + 0.40f * ease;
        Theme.setGlobalAlpha(ease);

        float cx = winPosX + FULL_W * 0.5f;
        float cy = winPosY + FULL_H * 0.5f;
        float sw = FULL_W * scale;
        float sh = FULL_H * scale;
        ImGui.setNextWindowSize(sw, sh, ImGuiCond.Always);
        ImGui.setNextWindowPos(cx - sw * 0.5f, cy - sh * 0.5f, ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, ease);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        boolean visible = ImGui.begin("##zolsi",
            ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoMove);
        ImGui.popStyleVar();
        if (visible) {
            drawHeader();
            drawBody();
        }
        ImGui.end();
        ImGui.popStyleVar();

        Widgets.renderDropdowns();
        Widgets.renderTooltips();
        drawUnhookOverlay(dt, ease);
        Theme.setGlobalAlpha(1.0f);
    }

    private void drawUnhookOverlay(float dt, float ease) {
        if (unhookPhase == UNHOOK_NONE) {
            return;
        }
        float dw = ImGui.getIO().getDisplaySizeX();
        float dh = ImGui.getIO().getDisplaySizeY();
        ImDrawList dl = ImGui.getForegroundDrawList();

        if (unhookPhase == UNHOOK_COVER) {
            unhookTimer += dt;
            float p = unhookTimer / UNHOOK_COVER_DUR;
            float e = smootherStep(p);
            float dx = Math.max(unhookOx, dw - unhookOx);
            float dy = Math.max(unhookOy, dh - unhookOy);
            float maxR = (float) Math.sqrt(dx * dx + dy * dy);
            float r = e * maxR;
            dl.addCircleFilled(unhookOx, unhookOy, r, packed(Theme.BG, 1.0f), 96);
            float ringA = (1.0f - clamp01(p)) * 0.85f;
            if (ringA > 0.01f && r > 1.0f) {
                dl.addCircle(unhookOx, unhookOy, r, packed(Theme.ACCENT, ringA), 96, 3.0f);
            }
            if (p >= 1.0f) {
                unhookPhase = UNHOOK_HOLD;
                unhookTimer = 0.0f;
                ImGuiManager.get().detach();
            }
        } else if (unhookPhase == UNHOOK_HOLD) {
            unhookTimer += dt;
            dl.addRectFilled(0.0f, 0.0f, dw, dh, packed(Theme.BG, 1.0f));
            drawUnhookingText(dl, dw, dh, 1.0f);
            if (unhookTimer >= UNHOOK_HOLD_DUR) {
                unhookPhase = UNHOOK_CLOSING;
                ImGuiManager.get().closeMenu();
            }
        } else {
            dl.addRectFilled(0.0f, 0.0f, dw, dh, packed(Theme.BG, ease));
            drawUnhookingText(dl, dw, dh, ease);
        }
    }

    private void drawUnhookingText(ImDrawList dl, float dw, float dh, float alpha) {
        ImFont font = ImGuiManager.get().arraylistFont();
        int dots = (int) (ImGui.getTime() / UNHOOK_DOT_INTERVAL) % 3 + 1;
        font.calcTextSizeA(textSize, UNHOOK_TEXT_SIZE, Float.MAX_VALUE, 0.0f, "Unhooking...");
        float tx = (dw - textSize.x) * 0.5f;
        float ty = (dh - UNHOOK_TEXT_SIZE) * 0.5f;
        StringBuilder shown = new StringBuilder("Unhooking");
        for (int i = 0; i < dots; i++) {
            shown.append('.');
        }
        dl.addText(font, UNHOOK_TEXT_SIZE, tx, ty, packed(Theme.TEXT, alpha), shown.toString());
    }

    private void beginUnhook() {
        if (unhookPhase != UNHOOK_NONE) {
            return;
        }
        unhookPhase = UNHOOK_COVER;
        unhookTimer = 0.0f;
        unhookOx = ImGui.getMousePosX();
        unhookOy = ImGui.getMousePosY();
        Widgets.clearDropdown();
    }

    private static int packed(float[] c, float a) {
        int alpha = (int) (clamp01(a) * 255.0f);
        int r = (int) (c[0] * 255.0f);
        int g = (int) (c[1] * 255.0f);
        int b = (int) (c[2] * 255.0f);
        return (alpha << 24) | (b << 16) | (g << 8) | r;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private void drawHeader() {
        ImGuiManager mgr = ImGuiManager.get();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        float ww = ImGui.getWindowSizeX();
        ImDrawList dl = ImGui.getWindowDrawList();

        float pulse = (float) (Math.sin(ImGui.getTime() * 3.0) * 0.15 + 0.85);
        dl.addCircleFilled(wx + 24.0f, wy + HEADER_H * 0.5f, 4.0f, Theme.col(Theme.ACCENT, pulse));
        dl.addText(mgr.fontTitle(), ImGuiManager.TITLE_SIZE, wx + 38.0f,
            wy + (HEADER_H - ImGuiManager.TITLE_SIZE) * 0.5f, Theme.col(Theme.TEXT), "zolsi");
        ImGui.pushFont(mgr.fontTitle());
        ImGui.calcTextSize(textSize, "zolsi");
        ImGui.popFont();
        dl.addText(mgr.fontMono(), ImGuiManager.MONO_SIZE, wx + 38.0f + textSize.x + 6.0f,
            wy + (HEADER_H - ImGuiManager.MONO_SIZE) * 0.5f + 2.0f, Theme.col(Theme.FAINT), ".cc");

        float bx = wx + ww - 42.0f;
        float by = wy + (HEADER_H - 28.0f) * 0.5f;
        ImGui.setCursorScreenPos(bx, by);
        boolean closeClicked = ImGui.invisibleButton("##close", 28.0f, 28.0f);
        boolean closeHovered = ImGui.isItemHovered();
        if (closeHovered) {
            dl.addCircleFilled(bx + 14.0f, by + 14.0f, 13.0f, Theme.col(Theme.TEXT, 0.06f));
        }
        int xcol = closeHovered ? Theme.col(Theme.TEXT) : Theme.col(Theme.FAINT);
        dl.addLine(bx + 9.5f, by + 9.5f, bx + 18.5f, by + 18.5f, xcol, 1.5f);
        dl.addLine(bx + 18.5f, by + 9.5f, bx + 9.5f, by + 18.5f, xcol, 1.5f);

        ImGui.setCursorScreenPos(wx, wy);
        ImGui.invisibleButton("##drag", ww - 52.0f, HEADER_H);
        if (ImGui.isItemActive()) {
            winPosX += ImGui.getIO().getMouseDeltaX();
            winPosY += ImGui.getIO().getMouseDeltaY();
        }

        dl.addLine(wx, wy + HEADER_H, wx + ww, wy + HEADER_H, Theme.col(Theme.BORDER), 1.0f);

        if (closeClicked) {
            mgr.closeMenu();
        }
    }

    private void drawBody() {
        ImGuiManager mgr = ImGuiManager.get();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        float ww = ImGui.getWindowSizeX();
        float wh = ImGui.getWindowSizeY();
        ImDrawList dl = ImGui.getWindowDrawList();

        dl.addRectFilled(wx, wy + HEADER_H, wx + RAIL_W, wy + wh, Theme.col(Theme.RAIL),
            12.0f, ImDrawFlags.RoundCornersBottomLeft);
        dl.addLine(wx + RAIL_W, wy + HEADER_H, wx + RAIL_W, wy + wh, Theme.col(Theme.BORDER), 1.0f);

        for (int c = 0; c < CATEGORIES.length; c++) {
            float iy = wy + HEADER_H + 14.0f + c * 46.0f;
            ImGui.setCursorScreenPos(wx + 10.0f, iy);
            if (ImGui.invisibleButton("##nav" + c, RAIL_W - 20.0f, 40.0f) && selectedTab != c) {
                tabDir = c > selectedTab ? 1 : -1;
                tabSlide = 0.0f;
                prevTab = selectedTab;
                selectedTab = c;
                Widgets.collapseAllCards();
                scrollTarget = 0.0f;
                scrollCurrent = 0.0f;
                lastAppliedScroll = 0.0f;
                Widgets.clearDropdown();
            }
            boolean hovered = ImGui.isItemHovered();
            float act = Widgets.anim("nav." + c, selectedTab == c ? 1.0f : 0.0f, 8.0f);
            float hov = Widgets.anim("nav.h" + c, hovered ? 1.0f : 0.0f, 8.0f);
            if (hov > 0.01f || act > 0.01f) {
                dl.addRectFilled(wx + 10.0f, iy, wx + RAIL_W - 10.0f, iy + 40.0f,
                    Theme.col(Theme.TEXT, 0.03f * hov + 0.03f * act), 8.0f);
            }
            float barH = 18.0f * act;
            if (barH > 0.5f) {
                dl.addRectFilled(wx + 16.0f, iy + 20.0f - barH * 0.5f, wx + 19.0f, iy + 20.0f + barH * 0.5f,
                    Theme.col(Theme.ACCENT, act), 2.0f);
            }
            int tcol = Theme.mix(Theme.DIM, Theme.TEXT, Math.max(act, hov * 0.6f));
            float iconSize = 18.0f;
            Icons.draw(dl, CATEGORY_ICONS[c], wx + 30.0f, iy + (40.0f - iconSize) * 0.5f, iconSize, tcol);
            dl.addText(mgr.fontMedium(), ImGuiManager.MEDIUM_SIZE, wx + 30.0f + iconSize + 10.0f,
                iy + (40.0f - ImGuiManager.MEDIUM_SIZE) * 0.5f, tcol, CATEGORIES[c]);
        }

        dl.addText(mgr.fontMono(), ImGuiManager.MONO_SIZE, wx + 18.0f, wy + wh - 28.0f,
            Theme.col(Theme.FAINT), "v1.0");

        float sbRight = wx + ww - 9.0f;
        float mxp = ImGui.getMousePosX();
        float myp = ImGui.getMousePosY();
        boolean nearSb = mxp >= sbRight - 26.0f && mxp <= wx + ww
            && myp >= wy + HEADER_H && myp <= wy + wh;
        scrollbarW += ((nearSb ? 13.0f : 8.0f) - scrollbarW) * Math.min(1.0f, ImGui.getIO().getDeltaTime() * 32.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, scrollbarW);

        float contentH = wh - HEADER_H - 14.0f;
        int contentFlags = ImGuiWindowFlags.NoScrollWithMouse;
        if (tabSlide < 1.0f) {
            contentFlags |= ImGuiWindowFlags.NoScrollbar;
        }
        ImGui.setCursorScreenPos(wx + RAIL_W + 1.0f, wy + HEADER_H + 7.0f);
        ImGui.beginChild("##content", ww - RAIL_W - 9.0f, contentH, false, contentFlags);

        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel != 0.0f && ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows) && !Widgets.mouseOverDropdown()) {
            scrollTarget -= wheel * 30.0f;
        }
        float maxScroll = ImGui.getScrollMaxY();
        if (scrollTarget > maxScroll) {
            scrollTarget = maxScroll;
        }
        if (scrollTarget < 0.0f) {
            scrollTarget = 0.0f;
        }
        float actual = ImGui.getScrollY();
        if (Math.abs(actual - lastAppliedScroll) > 1.5f) {
            scrollCurrent = actual;
            scrollTarget = actual;
        }
        scrollCurrent += (scrollTarget - scrollCurrent) * Math.min(1.0f, ImGui.getIO().getDeltaTime() * 24.0f);
        ImGui.setScrollY(scrollCurrent);
        lastAppliedScroll = scrollCurrent;

        float availW = ImGui.getContentRegionAvailX();
        Widgets.setRegion(ImGui.getCursorScreenPosX() + 18.0f, availW - 36.0f);
        ImGui.dummy(0.0f, 8.0f);

        if (tabSlide < 1.0f) {
            tabSlide = Math.min(1.0f, tabSlide + ImGui.getIO().getDeltaTime() / TAB_SLIDE_DUR);
        }
        float slideEase = smootherStep(tabSlide);
        float baseX = ImGui.getCursorScreenPosX();
        float baseY = ImGui.getCursorScreenPosY();

        if (tabSlide < 1.0f) {
            float travel = contentH * TAB_SLIDE_TRAVEL;
            float outOffset = -tabDir * travel * slideEase;
            ImGui.setCursorScreenPos(baseX, baseY + outOffset);
            drawTabCards(prevTab);
            float inOffset = tabDir * travel * (1.0f - slideEase);
            ImGui.setCursorScreenPos(baseX, baseY + inOffset);
            drawTabCards(selectedTab);
        } else {
            drawTabCards(selectedTab);
        }

        ImGui.dummy(0.0f, 6.0f);
        ImGui.endChild();
        ImGui.popStyleVar();
    }

    private void captureListening() {
        if (listening == null) {
            return;
        }
        int key = ImGuiManager.get().consumeKeyPress();
        if (key == -1) {
            return;
        }
        if (key == GLFW.GLFW_KEY_INSERT) {
            listening = null;
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            listening.clear();
        } else {
            listening.setKey(key);
        }
        listening = null;
    }

    private void startListening(Keybind kb) {
        listening = kb;
        ImGuiManager.get().consumeKeyPress();
    }

    private String bindLabel(Keybind kb) {
        if (listening == kb) {
            return "press key";
        }
        if (kb.isBound()) {
            return keyName(kb.getKey());
        }
        return "bind";
    }

    private void drawBindPopup(String id, Keybind kb) {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 8.0f);
        boolean open = ImGui.beginPopup(id + ".bindmode");
        ImGui.popStyleVar();
        if (open) {
            if (Widgets.popupOption(id + ".modeT", "Toggle", kb.getMode() == Keybind.Mode.TOGGLE)) {
                kb.setMode(Keybind.Mode.TOGGLE);
                ImGui.closeCurrentPopup();
            }
            if (Widgets.popupOption(id + ".modeH", "Hold", kb.getMode() == Keybind.Mode.HOLD)) {
                kb.setMode(Keybind.Mode.HOLD);
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void drawTriggerbot() {
        TriggerbotFeature tb = TriggerbotFeature.get();
        Widgets.beginCard("tb");
        if (Widgets.cardHeader("tb", "Triggerbot", tb.enabled, bindLabel(tb.bind), listening == tb.bind, true)) {
            startListening(tb.bind);
        }
        drawBindPopup("tb", tb.bind);
        if (Widgets.cardBodyBegin("tb")) {
            drawTriggerbotBody(tb);
        }
        Widgets.cardBodyEnd("tb");
        Widgets.endCard(tb.enabled.get());
    }

    private void drawTriggerbotBody(TriggerbotFeature tb) {
        Widgets.cardDivider();
        Widgets.multiSelect("tb.targets", "targets", TARGETS_PMI, tbTargets);
        Widgets.toggle("tb.lock", "target lock", tb.targetLock,
            "Keep hitting the first target you land on until it dies/leaves or you toggle Triggerbot off");
        if (Widgets.beginSub("tb.lockSub", tb.targetLock.get())) {
            Widgets.toggle("tb.pierce", "player piercing", tb.playerPiercing,
                "Hit through entities blocking your crosshair to reach the locked target (walls still block)");
        }
        Widgets.endSub("tb.lockSub");
        Widgets.slider("tb.cdg", "ground cooldown", tb.cooldownGround, 0.0f, 1.0f, "%.2f");
        Widgets.slider("tb.cda", "air cooldown", tb.cooldownAir, 0.0f, 1.0f, "%.2f");
        Widgets.toggle("tb.weapon", "holding weapon", tb.holdingWeapon);
        Widgets.toggle("tb.nodelay", "disable delay", tb.disableDelay);
        if (Widgets.beginSub("tb.delaySub", !tb.disableDelay.get())) {
            Widgets.slider("tb.jitter", "delay jitter", tb.delayJitter, 0.0f, 500.0f, "%.0f ms");
        }
        Widgets.endSub("tb.delaySub");
        Widgets.toggle("tb.clicksim", "click simulation", tb.clickSim,
            "Hit via a real attack click so CPS/Keystrokes mods register it (off = silent gameMode hit)");
        Widgets.segmented("tb.crit", "crit mode", tb.critMode, TB_CRIT_MODES, null);
    }

    private void drawCritAssist() {
        CritAssistFeature ca = CritAssistFeature.get();
        Widgets.beginCard("crit");
        if (Widgets.cardHeader("crit", "Crit Assist", ca.enabled, bindLabel(ca.bind), listening == ca.bind, true)) {
            startListening(ca.bind);
        }
        drawBindPopup("crit", ca.bind);
        if (Widgets.cardBodyBegin("crit")) {
            Widgets.cardDivider();
            Widgets.multiSelect("crit.targets", "targets", TARGETS_PMI, caTargets);
            Widgets.toggle("crit.weapon", "holding weapon", ca.holdingWeapon);
        }
        Widgets.cardBodyEnd("crit");
        Widgets.endCard(ca.enabled.get());
    }

    private void drawAimAssist() {
        AimAssistFeature aa = AimAssistFeature.get();
        Widgets.beginCard("aa");
        if (Widgets.cardHeader("aa", "Aim Assist", aa.enabled, bindLabel(aa.bind), listening == aa.bind, true)) {
            startListening(aa.bind);
        }
        drawBindPopup("aa", aa.bind);
        if (Widgets.cardBodyBegin("aa")) {
            drawAimAssistBody(aa);
        }
        Widgets.cardBodyEnd("aa");
        Widgets.endCard(aa.enabled.get());
    }

    private void drawAimAssistBody(AimAssistFeature aa) {
        Widgets.cardDivider();
        Widgets.multiSelect("aa.targets", "targets", TARGETS_PMI, aaTargets);
        Widgets.toggle("aa.lock", "target lock", aa.targetLock);
        Widgets.segmented("aa.mode", "assist mode", aa.inputMode, AA_INPUT_MODES,
            "Silent: your body aims at the target while the camera stays free");
        Widgets.toggle("aa.vert", "vertical aim", aa.verticalAim);
        Widgets.slider("aa.dist", "distance", aa.distance, 0.0f, 20.0f, "%.1f");
        Widgets.slider("aa.fov", "fov", aa.fov, 0.0f, 360.0f, "%.0f");
        Widgets.segmented("aa.hitbox", "hitbox", aa.hitbox, AA_HITBOXES, null);
        Widgets.slider("aa.multi", "multipoint", aa.multipoint, 0.0f, 100.0f, "%.0f%%");
        Widgets.slider("aa.yaw", "yaw speed", aa.yawSpeed, 0.0f, 0.05f, "%.3f");
        Widgets.slider("aa.pitch", "pitch speed", aa.pitchSpeed, 0.0f, 0.05f, "%.3f");
        Widgets.slider("aa.sj", "speed jitter", aa.speedJitter, 0.0f, 100.0f, "%.0f%%",
            "Randomizes the aim speed each frame so it isn't a robotic constant");
        Widgets.toggle("aa.gcd", "gcd fix", aa.gcdFix,
            "Snaps rotation deltas to the sensitivity step real mouse input produces (anticheat rotation check)");
    }

    private void drawSprint() {
        SprintFeature sprint = SprintFeature.get();
        Widgets.beginCard("sprint");
        if (Widgets.cardHeader("sprint", "Sprint", sprint.enabled, bindLabel(sprint.bind), listening == sprint.bind)) {
            startListening(sprint.bind);
        }
        drawBindPopup("sprint", sprint.bind);
        Widgets.endCard(sprint.enabled.get());
    }

    private void drawNoJumpDelay() {
        NoJumpDelayFeature njd = NoJumpDelayFeature.get();
        Widgets.beginCard("njd");
        if (Widgets.cardHeader("njd", "NoJumpDelay", njd.enabled, bindLabel(njd.bind), listening == njd.bind, true)) {
            startListening(njd.bind);
        }
        drawBindPopup("njd", njd.bind);
        if (Widgets.cardBodyBegin("njd")) {
            Widgets.cardDivider();
            Widgets.sliderInt("njd.delay", "jump delay", njd.delay, 0, 10, "%.0f ticks");
        }
        Widgets.cardBodyEnd("njd");
        Widgets.endCard(njd.enabled.get());
    }

    private void drawAutoJumpReset() {
        AutoJumpResetFeature ajr = AutoJumpResetFeature.get();
        Widgets.beginCard("ajr");
        if (Widgets.cardHeader("ajr", "AutoJumpReset", ajr.enabled, bindLabel(ajr.bind), listening == ajr.bind, true)) {
            startListening(ajr.bind);
        }
        drawBindPopup("ajr", ajr.bind);
        if (Widgets.cardBodyBegin("ajr")) {
            Widgets.cardDivider();
            Widgets.slider("ajr.chance", "chance", ajr.chance, 0.0f, 100.0f, "%.0f%%");
            Widgets.rangeSlider("ajr.accuracy", "accuracy", ajr.accuracy, 0.0f, 1.0f, "%.2f");
            Widgets.toggle("ajr.fov", "only in fov", ajr.onlyInFov);
            Widgets.toggle("ajr.liquid", "liquid check", ajr.liquidCheck);
        }
        Widgets.cardBodyEnd("ajr");
        Widgets.endCard(ajr.enabled.get());
    }

    private void drawAntiBot() {
        AntiBotFeature ab = AntiBotFeature.get();
        Widgets.beginCard("antibot");
        if (Widgets.cardHeader("antibot", "AntiBot", ab.enabled, bindLabel(ab.bind), listening == ab.bind, true)) {
            startListening(ab.bind);
        }
        drawBindPopup("antibot", ab.bind);
        if (Widgets.cardBodyBegin("antibot")) {
            Widgets.cardDivider();
            Widgets.toggle("antibot.movement", "check movement", ab.checkMovement);
            Widgets.toggle("antibot.ping", "check ping", ab.checkPing);
            Widgets.toggle("antibot.name", "check name", ab.checkName);
            Widgets.toggle("antibot.userid", "check userid", ab.checkUserId);
        }
        Widgets.cardBodyEnd("antibot");
        Widgets.endCard(ab.enabled.get());
    }

    private void drawEsp() {
        EspFeature esp = EspFeature.get();
        Widgets.beginCard("esp");
        if (Widgets.cardHeader("esp", "2D ESP", esp.enabled, bindLabel(esp.bind), listening == esp.bind, true)) {
            startListening(esp.bind);
        }
        drawBindPopup("esp", esp.bind);
        if (Widgets.cardBodyBegin("esp")) {
            drawEspBody(esp);
        }
        Widgets.cardBodyEnd("esp");
        Widgets.endCard(esp.enabled.get());
    }

    private void drawEspBody(EspFeature esp) {
        Widgets.cardDivider();
        Widgets.multiSelect("esp.targets", "targets", TARGETS_PMF, espTargets);
        Widgets.colorRow("##espcolor", "color", esp.color,
            ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoAlpha | ImGuiColorEditFlags.PickerHueWheel);
        Widgets.slider("esp.outop", "esp opacity", esp.outlineOpacity, 0.0f, 1.0f, "%.2f");
        Widgets.slider("esp.thick", "esp thickness", esp.thickness, 1.0f, 5.0f, "%.1f");
        Widgets.slider("esp.radius", "corner radius", esp.cornerRadius, 0.0f, 12.0f, "%.0f");
        Widgets.toggle("esp.outline", "box outline", esp.boxOutline);
        Widgets.toggle("esp.autoscale", "auto-scale", esp.autoScale);
        if (Widgets.beginSub("esp.sizeSub", !esp.autoScale.get())) {
            Widgets.slider("esp.size", "box size", esp.boxSize, 6.0f, 90.0f, "%.0f");
        }
        Widgets.endSub("esp.sizeSub");
        Widgets.toggle("esp.names", "names", esp.names);
        if (Widgets.beginSub("esp.namesSub", esp.names.get())) {
            Widgets.toggle("esp.nameshadow", "shadow", esp.namesShadow);
            Widgets.slider("esp.namesize", "text size", esp.namesSize, 6.0f, 24.0f, "%.0f");
            Widgets.toggle("esp.namesbg", "background", esp.namesBackground);
            if (Widgets.beginSub("esp.namesbgSub", esp.namesBackground.get())) {
                Widgets.colorRow("##espnamesbg", "bg color", esp.namesBgColor,
                    ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoAlpha | ImGuiColorEditFlags.PickerHueWheel);
                Widgets.slider("esp.namesbgop", "bg opacity", esp.namesBgOpacity, 0.0f, 1.0f, "%.2f");
            }
            Widgets.endSub("esp.namesbgSub");
        }
        Widgets.endSub("esp.namesSub");
        Widgets.toggle("esp.hb", "healthbar", esp.healthbar);
        if (Widgets.beginSub("esp.hbSub", esp.healthbar.get())) {
            Widgets.toggle("esp.hbgrad", "gradient", esp.healthbarGradient);
            Widgets.toggle("esp.hbout", "outline", esp.healthbarOutline);
            Widgets.slider("esp.hbop", "healthbar opacity", esp.healthbarOpacity, 0.0f, 1.0f, "%.2f");
            Widgets.slider("esp.hbthick", "healthbar thickness", esp.healthbarThickness, 1.0f, 8.0f, "%.1f");
        }
        Widgets.endSub("esp.hbSub");
    }

    private void drawNametags() {
        NametagsFeature nt = NametagsFeature.get();
        boolean streamproof = StreamproofOverlay.get().enabled.get();
        if (streamproof) {
            Widgets.pushDisabled();
        }
        Widgets.beginCard("nametags");
        if (Widgets.cardHeader("nametags", "Nametags", nt.enabled, bindLabel(nt.bind), listening == nt.bind, true)) {
            startListening(nt.bind);
        }
        drawBindPopup("nametags", nt.bind);
        if (Widgets.cardBodyBegin("nametags")) {
            drawNametagsBody(nt);
        }
        Widgets.cardBodyEnd("nametags");
        Widgets.endCard(nt.enabled.get());
        if (streamproof) {
            Widgets.popDisabled();
        }
    }

    private void drawNametagsBody(NametagsFeature nt) {
        Widgets.cardDivider();
        Widgets.multiSelect("nametags.targets", "targets", TARGETS_PMI, nametagsTargets);
        Widgets.toggle("nametags.autoscale", "auto-scale", nt.autoScale);
        if (Widgets.beginSub("nametags.scaleSub", !nt.autoScale.get())) {
            Widgets.slider("nametags.scale", "scale", nt.scale, 0.5f, 3.0f, "%.2f");
        }
        Widgets.endSub("nametags.scaleSub");
        Widgets.toggle("nametags.health", "health", nt.showHealth);
        Widgets.toggle("nametags.distance", "distance", nt.showDistance);
        Widgets.toggle("nametags.effects", "effects", nt.showEffects);
        if (Widgets.beginSub("nametags.effectsSub", nt.showEffects.get())) {
            Widgets.slider("nametags.effectscale", "effect size", nt.effectScale, 0.3f, 1.5f, "%.2f");
        }
        Widgets.endSub("nametags.effectsSub");
        Widgets.toggle("nametags.equipment", "equipment", nt.showEquipment);
        Widgets.toggle("nametags.background", "background", nt.background);
        if (Widgets.beginSub("nametags.bgSub", nt.background.get())) {
            Widgets.slider("nametags.bgop", "bg opacity", nt.bgOpacity, 0.0f, 1.0f, "%.2f");
        }
        Widgets.endSub("nametags.bgSub");
    }

    private void drawArrayList() {
        ArrayListFeature al = ArrayListFeature.get();
        Widgets.beginCard("al");
        if (Widgets.cardHeader("al", "ArrayList", al.enabled, bindLabel(al.bind), listening == al.bind, true)) {
            startListening(al.bind);
        }
        drawBindPopup("al", al.bind);
        if (Widgets.cardBodyBegin("al")) {
            Widgets.cardDivider();
            Widgets.selectOne("al.pos", "position", al.position, ARRAY_POSITIONS);
            Widgets.multiSelect("al.cats", "categories", AL_CATS, alCats);
            Widgets.colorRow("##alcolor", "color", al.color,
                ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoAlpha | ImGuiColorEditFlags.PickerHueWheel);
            Widgets.slider("al.op", "background opacity", al.opacity, 0.0f, 1.0f, "%.2f");
            Widgets.slider("al.radius", "corner radius", al.cornerRadius, 0.0f, 12.0f, "%.1f");
            Widgets.slider("al.scale", "scale", al.scale, ArrayListFeature.MIN_SCALE, ArrayListFeature.MAX_SCALE, "%.2f");
        }
        Widgets.cardBodyEnd("al");
        Widgets.endCard(al.enabled.get());
    }

    private void drawHitmarkers() {
        HitmarkerFeature hm = HitmarkerFeature.get();
        Widgets.beginCard("hm");
        if (Widgets.cardHeader("hm", "Hitmarkers", hm.enabled, bindLabel(hm.bind), listening == hm.bind, true)) {
            startListening(hm.bind);
        }
        drawBindPopup("hm", hm.bind);
        if (Widgets.cardBodyBegin("hm")) {
            Widgets.cardDivider();
            Widgets.selectOne("hm.style", "style", hm.style, HitmarkerFeature.STYLES);
            Widgets.colorRow("##hmcolor", "color", hm.color,
                ImGuiColorEditFlags.NoInputs | ImGuiColorEditFlags.NoAlpha | ImGuiColorEditFlags.PickerHueWheel);
            Widgets.slider("hm.size", "size", hm.size, 2.0f, 18.0f, "%.0f");
            Widgets.slider("hm.gap", "gap", hm.gap, 0.0f, 8.0f, "%.1f");
            Widgets.slider("hm.thick", "thickness", hm.thickness, 0.5f, 5.0f, "%.1f");
            Widgets.slider("hm.duration", "duration", hm.duration, 0.1f, 1.5f, "%.2fs");
            Widgets.cardDivider();
            Widgets.toggle("hm.sound", "hit sound", hm.soundEnabled);
            if (Widgets.beginSub("hm.soundSub", hm.soundEnabled.get())) {
                Widgets.slider("hm.vol", "volume", hm.soundVolume, 0.0f, 1.0f, "%.2f");
                Widgets.selectOne("hm.snd", "sound", hm.soundIndex, HitmarkerFeature.SOUND_NAMES);
            }
            Widgets.endSub("hm.soundSub");
        }
        Widgets.cardBodyEnd("hm");
        Widgets.endCard(hm.enabled.get());
    }

    private void drawConfigs() {
        LocalConfig local = LocalConfig.get();
        if (!configListLoaded) {
            configListLoaded = true;
            local.refreshNow();
        }

        Widgets.beginCard("cfg");
        Widgets.cardHeader("cfg", "Local configs", null, null, false);
        Widgets.cardDivider();

        ImGui.setCursorScreenPos(Widgets.innerX(), ImGui.getCursorScreenPosY());
        ImGui.pushStyleColor(ImGuiCol.ChildBg, Theme.FIELD[0], Theme.FIELD[1], Theme.FIELD[2], 0.45f);
        ImGui.beginChild("##configList", Widgets.innerW(), 160.0f, false);
        ImGui.dummy(0.0f, 2.0f);
        for (String name : local.getNames()) {
            ImGui.setCursorPosX(4.0f);
            if (Widgets.listRow("cfg.row." + name, name, name.equals(configName.get()))) {
                configName.set(name);
            }
        }
        ImGui.endChild();
        ImGui.popStyleColor();

        ImGui.setCursorScreenPos(Widgets.innerX(), ImGui.getCursorScreenPosY());
        ImGui.setNextItemWidth(Widgets.innerW());
        ImGui.inputTextWithHint("##configName", "config name", configName);

        float bw = (Widgets.innerW() - 30.0f) / 4.0f;
        ImGui.setCursorScreenPos(Widgets.innerX(), ImGui.getCursorScreenPosY());
        if (Widgets.button("cfg.save", "Save", bw, 32.0f, 1)) {
            local.saveNow(configName.get(), serializeControls());
        }
        ImGui.sameLine();
        if (Widgets.button("cfg.load", "Load", bw, 32.0f, 0)) {
            local.loadNow(configName.get());
        }
        ImGui.sameLine();
        if (Widgets.button("cfg.delete", "Delete", bw, 32.0f, 2)) {
            local.deleteNow(configName.get());
        }
        ImGui.sameLine();
        if (Widgets.button("cfg.refresh", "Refresh", bw, 32.0f, 0)) {
            local.refreshNow();
        }

        Widgets.statusText(local.getStatus());
        Widgets.endCard(false);
    }

    private void drawSession() {
        Widgets.beginCard("session");
        Widgets.cardHeader("session", "Session", null, null, false);
        Widgets.cardDivider();
        Widgets.toggle("session.streamproof", "streamproof", StreamproofOverlay.get().enabled,
            "Draws the menu, ESP and ArrayList on a hidden overlay that screen capture cannot see. Disables Nametags while active.");
        ImGui.setCursorScreenPos(Widgets.innerX(), ImGui.getCursorScreenPosY() + 4.0f);
        Widgets.textRow("Detach zolsi client from this Minecraft session.");
        ImGui.setCursorScreenPos(Widgets.innerX(), ImGui.getCursorScreenPosY() + 2.0f);
        boolean unhookClicked = Widgets.button("session.unhook", "Unhook", "unhook", Widgets.innerW(), 34.0f, 2);
        Widgets.hint(ImGui.isItemHovered(), "The menu and all hooks shut down until you re-inject");
        if (unhookClicked) {
            beginUnhook();
        }
        Widgets.endCard(false);
    }

    private String serializeControls() {
        Map<String, Object> data = new LinkedHashMap<>();
        EspFeature esp = EspFeature.get();
        data.put("esp.enabled", esp.enabled.get());
        data.put("esp.bind.key", esp.bind.getKey());
        data.put("esp.bind.mode", esp.bind.getMode().name());
        data.put("esp.targetPlayers", esp.targetPlayers.get());
        data.put("esp.targetMobs", esp.targetMobs.get());
        data.put("esp.targetFriendly", esp.targetFriendly.get());
        data.put("esp.color.r", esp.color[0]);
        data.put("esp.color.g", esp.color[1]);
        data.put("esp.color.b", esp.color[2]);
        data.put("esp.color.a", esp.color[3]);
        data.put("esp.outlineOpacity", esp.outlineOpacity[0]);
        data.put("esp.thickness", esp.thickness[0]);
        data.put("esp.cornerRadius", esp.cornerRadius[0]);
        data.put("esp.boxOutline", esp.boxOutline.get());
        data.put("esp.autoScale", esp.autoScale.get());
        data.put("esp.boxSize", esp.boxSize[0]);
        data.put("esp.names", esp.names.get());
        data.put("esp.namesShadow", esp.namesShadow.get());
        data.put("esp.namesSize", esp.namesSize[0]);
        data.put("esp.namesBackground", esp.namesBackground.get());
        data.put("esp.namesBgColor.r", esp.namesBgColor[0]);
        data.put("esp.namesBgColor.g", esp.namesBgColor[1]);
        data.put("esp.namesBgColor.b", esp.namesBgColor[2]);
        data.put("esp.namesBgOpacity", esp.namesBgOpacity[0]);
        data.put("esp.healthbar", esp.healthbar.get());
        data.put("esp.healthbarGradient", esp.healthbarGradient.get());
        data.put("esp.healthbarOpacity", esp.healthbarOpacity[0]);
        data.put("esp.healthbarThickness", esp.healthbarThickness[0]);
        data.put("esp.healthbarOutline", esp.healthbarOutline.get());
        NametagsFeature nt = NametagsFeature.get();
        data.put("nametags.enabled", nt.enabled.get());
        data.put("nametags.bind.key", nt.bind.getKey());
        data.put("nametags.bind.mode", nt.bind.getMode().name());
        data.put("nametags.targetPlayers", nt.targetPlayers.get());
        data.put("nametags.targetMobs", nt.targetMobs.get());
        data.put("nametags.targetInvisibles", nt.targetInvisibles.get());
        data.put("nametags.autoScale", nt.autoScale.get());
        data.put("nametags.scale", nt.scale[0]);
        data.put("nametags.showHealth", nt.showHealth.get());
        data.put("nametags.showDistance", nt.showDistance.get());
        data.put("nametags.showEffects", nt.showEffects.get());
        data.put("nametags.effectScale", nt.effectScale[0]);
        data.put("nametags.showEquipment", nt.showEquipment.get());
        data.put("nametags.background", nt.background.get());
        data.put("nametags.bgOpacity", nt.bgOpacity[0]);
        ArrayListFeature al = ArrayListFeature.get();
        data.put("arraylist.enabled", al.enabled.get());
        data.put("arraylist.bind.key", al.bind.getKey());
        data.put("arraylist.bind.mode", al.bind.getMode().name());
        data.put("arraylist.position", al.position.get());
        data.put("arraylist.opacity", al.opacity[0]);
        data.put("arraylist.cornerRadius", al.cornerRadius[0]);
        data.put("arraylist.scale", al.scale[0]);
        data.put("arraylist.color.r", al.color[0]);
        data.put("arraylist.color.g", al.color[1]);
        data.put("arraylist.color.b", al.color[2]);
        data.put("arraylist.catCombat", al.catCombat.get());
        data.put("arraylist.catUtility", al.catUtility.get());
        data.put("arraylist.catVisuals", al.catVisuals.get());
        HitmarkerFeature hm = HitmarkerFeature.get();
        data.put("hitmarker.enabled", hm.enabled.get());
        data.put("hitmarker.bind.key", hm.bind.getKey());
        data.put("hitmarker.bind.mode", hm.bind.getMode().name());
        data.put("hitmarker.color.r", hm.color[0]);
        data.put("hitmarker.color.g", hm.color[1]);
        data.put("hitmarker.color.b", hm.color[2]);
        data.put("hitmarker.color.a", hm.color[3]);
        data.put("hitmarker.style", hm.style.get());
        data.put("hitmarker.size", hm.size[0]);
        data.put("hitmarker.gap", hm.gap[0]);
        data.put("hitmarker.thickness", hm.thickness[0]);
        data.put("hitmarker.duration", hm.duration[0]);
        data.put("hitmarker.soundEnabled", hm.soundEnabled.get());
        data.put("hitmarker.soundVolume", hm.soundVolume[0]);
        data.put("hitmarker.soundIndex", hm.soundIndex.get());
        data.put("streamproof.enabled", StreamproofOverlay.get().enabled.get());
        SprintFeature sprint = SprintFeature.get();
        data.put("sprint.enabled", sprint.enabled.get());
        data.put("sprint.bind.key", sprint.bind.getKey());
        data.put("sprint.bind.mode", sprint.bind.getMode().name());
        NoJumpDelayFeature njd = NoJumpDelayFeature.get();
        data.put("nojumpdelay.enabled", njd.enabled.get());
        data.put("nojumpdelay.bind.key", njd.bind.getKey());
        data.put("nojumpdelay.bind.mode", njd.bind.getMode().name());
        data.put("nojumpdelay.delay", njd.delay[0]);
        AutoJumpResetFeature ajr = AutoJumpResetFeature.get();
        data.put("autojumpreset.enabled", ajr.enabled.get());
        data.put("autojumpreset.bind.key", ajr.bind.getKey());
        data.put("autojumpreset.bind.mode", ajr.bind.getMode().name());
        data.put("autojumpreset.chance", ajr.chance[0]);
        data.put("autojumpreset.accuracyMin", ajr.accuracy[0]);
        data.put("autojumpreset.accuracyMax", ajr.accuracy[1]);
        data.put("autojumpreset.onlyInFov", ajr.onlyInFov.get());
        data.put("autojumpreset.liquidCheck", ajr.liquidCheck.get());
        AntiBotFeature ab = AntiBotFeature.get();
        data.put("antibot.enabled", ab.enabled.get());
        data.put("antibot.bind.key", ab.bind.getKey());
        data.put("antibot.bind.mode", ab.bind.getMode().name());
        data.put("antibot.checkMovement", ab.checkMovement.get());
        data.put("antibot.checkPing", ab.checkPing.get());
        data.put("antibot.checkName", ab.checkName.get());
        data.put("antibot.checkUserId", ab.checkUserId.get());
        TriggerbotFeature tb = TriggerbotFeature.get();
        data.put("triggerbot.enabled", tb.enabled.get());
        data.put("triggerbot.bind.key", tb.bind.getKey());
        data.put("triggerbot.bind.mode", tb.bind.getMode().name());
        data.put("triggerbot.targetPlayers", tb.targetPlayers.get());
        data.put("triggerbot.targetMobs", tb.targetMobs.get());
        data.put("triggerbot.targetInvisibles", tb.targetInvisibles.get());
        data.put("triggerbot.targetLock", tb.targetLock.get());
        data.put("triggerbot.playerPiercing", tb.playerPiercing.get());
        data.put("triggerbot.cooldownGround", tb.cooldownGround[0]);
        data.put("triggerbot.cooldownAir", tb.cooldownAir[0]);
        data.put("triggerbot.holdingWeapon", tb.holdingWeapon.get());
        data.put("triggerbot.critMode", tb.critMode.get());
        data.put("triggerbot.disableDelay", tb.disableDelay.get());
        data.put("triggerbot.delayJitter", tb.delayJitter[0]);
        data.put("triggerbot.clickSim", tb.clickSim.get());
        CritAssistFeature ca = CritAssistFeature.get();
        data.put("critassist.enabled", ca.enabled.get());
        data.put("critassist.bind.key", ca.bind.getKey());
        data.put("critassist.bind.mode", ca.bind.getMode().name());
        data.put("critassist.holdingWeapon", ca.holdingWeapon.get());
        data.put("critassist.targetPlayers", ca.targetPlayers.get());
        data.put("critassist.targetMobs", ca.targetMobs.get());
        data.put("critassist.targetInvisibles", ca.targetInvisibles.get());
        AimAssistFeature aa = AimAssistFeature.get();
        data.put("aimassist.enabled", aa.enabled.get());
        data.put("aimassist.bind.key", aa.bind.getKey());
        data.put("aimassist.bind.mode", aa.bind.getMode().name());
        data.put("aimassist.targetPlayers", aa.targetPlayers.get());
        data.put("aimassist.targetMobs", aa.targetMobs.get());
        data.put("aimassist.targetInvisibles", aa.targetInvisibles.get());
        data.put("aimassist.targetLock", aa.targetLock.get());
        data.put("aimassist.inputMode", aa.inputMode.get());
        data.put("aimassist.verticalAim", aa.verticalAim.get());
        data.put("aimassist.distance", aa.distance[0]);
        data.put("aimassist.fov", aa.fov[0]);
        data.put("aimassist.hitbox", aa.hitbox.get());
        data.put("aimassist.multipoint", aa.multipoint[0]);
        data.put("aimassist.yawSpeed", aa.yawSpeed[0]);
        data.put("aimassist.pitchSpeed", aa.pitchSpeed[0]);
        data.put("aimassist.speedJitter", aa.speedJitter[0]);
        data.put("aimassist.gcdFix", aa.gcdFix.get());
        return gson.toJson(data);
    }

    @SuppressWarnings("unchecked")
    private void applyControls(String json) {
        Map<String, Object> data;
        try {
            data = gson.fromJson(json, Map.class);
        } catch (Throwable t) {
            return;
        }
        if (data == null) {
            return;
        }
        applyEsp(data);
        applyNametags(data);
        applyArrayList(data);
        applyHitmarkers(data);
        applySprint(data);
        applyNoJumpDelay(data);
        applyAutoJumpReset(data);
        applyAntiBot(data);
        applyTriggerbot(data);
        applyCritAssist(data);
        applyAimAssist(data);
        applyStreamproof(data);
    }

    private void applyStreamproof(Map<String, Object> data) {
        Object v = data.get("streamproof.enabled");
        if (v instanceof Boolean) {
            StreamproofOverlay.get().enabled.set((Boolean) v);
        }
    }

    private void applyAimAssist(Map<String, Object> data) {
        AimAssistFeature aa = AimAssistFeature.get();
        Object v = data.get("aimassist.enabled");
        if (v instanceof Boolean) {
            aa.enabled.set((Boolean) v);
        }
        v = data.get("aimassist.bind.key");
        if (v instanceof Number) {
            aa.bind.setKey(((Number) v).intValue());
        }
        v = data.get("aimassist.bind.mode");
        if (v instanceof String) {
            try {
                aa.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("aimassist.targetPlayers");
        if (v instanceof Boolean) {
            aa.targetPlayers.set((Boolean) v);
        }
        v = data.get("aimassist.targetMobs");
        if (v instanceof Boolean) {
            aa.targetMobs.set((Boolean) v);
        }
        v = data.get("aimassist.targetInvisibles");
        if (v instanceof Boolean) {
            aa.targetInvisibles.set((Boolean) v);
        }
        v = data.get("aimassist.targetLock");
        if (v instanceof Boolean) {
            aa.targetLock.set((Boolean) v);
        }
        v = data.get("aimassist.inputMode");
        if (v instanceof Number) {
            aa.inputMode.set(((Number) v).intValue());
        }
        v = data.get("aimassist.verticalAim");
        if (v instanceof Boolean) {
            aa.verticalAim.set((Boolean) v);
        }
        v = data.get("aimassist.distance");
        if (v instanceof Number) {
            aa.distance[0] = ((Number) v).floatValue();
        }
        v = data.get("aimassist.fov");
        if (v instanceof Number) {
            aa.fov[0] = ((Number) v).floatValue();
        }
        v = data.get("aimassist.hitbox");
        if (v instanceof Number) {
            aa.hitbox.set(((Number) v).intValue());
        }
        v = data.get("aimassist.multipoint");
        if (v instanceof Number) {
            aa.multipoint[0] = ((Number) v).floatValue();
        }
        v = data.get("aimassist.yawSpeed");
        if (v instanceof Number) {
            aa.yawSpeed[0] = ((Number) v).floatValue();
        }
        v = data.get("aimassist.pitchSpeed");
        if (v instanceof Number) {
            aa.pitchSpeed[0] = ((Number) v).floatValue();
        }
        v = data.get("aimassist.speedJitter");
        if (v instanceof Number) {
            aa.speedJitter[0] = ((Number) v).floatValue();
        }
        v = data.get("aimassist.gcdFix");
        if (v instanceof Boolean) {
            aa.gcdFix.set((Boolean) v);
        }
    }

    private void applyTriggerbot(Map<String, Object> data) {
        TriggerbotFeature tb = TriggerbotFeature.get();
        Object v = data.get("triggerbot.enabled");
        if (v instanceof Boolean) {
            tb.enabled.set((Boolean) v);
        }
        v = data.get("triggerbot.bind.key");
        if (v instanceof Number) {
            tb.bind.setKey(((Number) v).intValue());
        }
        v = data.get("triggerbot.bind.mode");
        if (v instanceof String) {
            try {
                tb.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("triggerbot.targetPlayers");
        if (v instanceof Boolean) {
            tb.targetPlayers.set((Boolean) v);
        }
        v = data.get("triggerbot.targetMobs");
        if (v instanceof Boolean) {
            tb.targetMobs.set((Boolean) v);
        }
        v = data.get("triggerbot.targetInvisibles");
        if (v instanceof Boolean) {
            tb.targetInvisibles.set((Boolean) v);
        }
        v = data.get("triggerbot.targetLock");
        if (v instanceof Boolean) {
            tb.targetLock.set((Boolean) v);
        }
        v = data.get("triggerbot.playerPiercing");
        if (v instanceof Boolean) {
            tb.playerPiercing.set((Boolean) v);
        }
        v = data.get("triggerbot.cooldownGround");
        if (v instanceof Number) {
            tb.cooldownGround[0] = ((Number) v).floatValue();
        }
        v = data.get("triggerbot.cooldownAir");
        if (v instanceof Number) {
            tb.cooldownAir[0] = ((Number) v).floatValue();
        }
        v = data.get("triggerbot.holdingWeapon");
        if (v instanceof Boolean) {
            tb.holdingWeapon.set((Boolean) v);
        }
        v = data.get("triggerbot.critMode");
        if (v instanceof Number) {
            tb.critMode.set(((Number) v).intValue());
        }
        v = data.get("triggerbot.disableDelay");
        if (v instanceof Boolean) {
            tb.disableDelay.set((Boolean) v);
        }
        v = data.get("triggerbot.delayJitter");
        if (v instanceof Number) {
            tb.delayJitter[0] = ((Number) v).floatValue();
        }
        v = data.get("triggerbot.clickSim");
        if (v instanceof Boolean) {
            tb.clickSim.set((Boolean) v);
        }
    }

    private void applyCritAssist(Map<String, Object> data) {
        CritAssistFeature ca = CritAssistFeature.get();
        Object v = data.get("critassist.enabled");
        if (v instanceof Boolean) {
            ca.enabled.set((Boolean) v);
        }
        v = data.get("critassist.bind.key");
        if (v instanceof Number) {
            ca.bind.setKey(((Number) v).intValue());
        }
        v = data.get("critassist.bind.mode");
        if (v instanceof String) {
            try {
                ca.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("critassist.holdingWeapon");
        if (v instanceof Boolean) {
            ca.holdingWeapon.set((Boolean) v);
        }
        v = data.get("critassist.targetPlayers");
        if (v instanceof Boolean) {
            ca.targetPlayers.set((Boolean) v);
        }
        v = data.get("critassist.targetMobs");
        if (v instanceof Boolean) {
            ca.targetMobs.set((Boolean) v);
        }
        v = data.get("critassist.targetInvisibles");
        if (v instanceof Boolean) {
            ca.targetInvisibles.set((Boolean) v);
        }
    }

    private void applyHitmarkers(Map<String, Object> data) {
        HitmarkerFeature hm = HitmarkerFeature.get();
        Object v = data.get("hitmarker.enabled");
        if (v instanceof Boolean) { hm.enabled.set((Boolean) v); }
        v = data.get("hitmarker.bind.key");
        if (v instanceof Number) { hm.bind.setKey(((Number) v).intValue()); }
        v = data.get("hitmarker.bind.mode");
        if (v instanceof String) {
            try { hm.bind.setMode(Keybind.Mode.valueOf((String) v)); } catch (IllegalArgumentException ignored) {}
        }
        v = data.get("hitmarker.color.r");
        if (v instanceof Number) { hm.color[0] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.color.g");
        if (v instanceof Number) { hm.color[1] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.color.b");
        if (v instanceof Number) { hm.color[2] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.color.a");
        if (v instanceof Number) { hm.color[3] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.style");
        if (v instanceof Number) { hm.style.set(((Number) v).intValue()); }
        v = data.get("hitmarker.size");
        if (v instanceof Number) { hm.size[0] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.gap");
        if (v instanceof Number) { hm.gap[0] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.thickness");
        if (v instanceof Number) { hm.thickness[0] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.duration");
        if (v instanceof Number) { hm.duration[0] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.soundEnabled");
        if (v instanceof Boolean) { hm.soundEnabled.set((Boolean) v); }
        v = data.get("hitmarker.soundVolume");
        if (v instanceof Number) { hm.soundVolume[0] = ((Number) v).floatValue(); }
        v = data.get("hitmarker.soundIndex");
        if (v instanceof Number) { hm.soundIndex.set(((Number) v).intValue()); }
    }

    private void applySprint(Map<String, Object> data) {
        SprintFeature sprint = SprintFeature.get();
        Object v = data.get("sprint.enabled");
        if (v instanceof Boolean) {
            sprint.enabled.set((Boolean) v);
        }
        v = data.get("sprint.bind.key");
        if (v instanceof Number) {
            sprint.bind.setKey(((Number) v).intValue());
        }
        v = data.get("sprint.bind.mode");
        if (v instanceof String) {
            try {
                sprint.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void applyNoJumpDelay(Map<String, Object> data) {
        NoJumpDelayFeature njd = NoJumpDelayFeature.get();
        Object v = data.get("nojumpdelay.enabled");
        if (v instanceof Boolean) {
            njd.enabled.set((Boolean) v);
        }
        v = data.get("nojumpdelay.bind.key");
        if (v instanceof Number) {
            njd.bind.setKey(((Number) v).intValue());
        }
        v = data.get("nojumpdelay.bind.mode");
        if (v instanceof String) {
            try {
                njd.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("nojumpdelay.delay");
        if (v instanceof Number) {
            njd.delay[0] = ((Number) v).intValue();
        }
    }

    private void applyAutoJumpReset(Map<String, Object> data) {
        AutoJumpResetFeature ajr = AutoJumpResetFeature.get();
        Object v = data.get("autojumpreset.enabled");
        if (v instanceof Boolean) {
            ajr.enabled.set((Boolean) v);
        }
        v = data.get("autojumpreset.bind.key");
        if (v instanceof Number) {
            ajr.bind.setKey(((Number) v).intValue());
        }
        v = data.get("autojumpreset.bind.mode");
        if (v instanceof String) {
            try {
                ajr.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("autojumpreset.chance");
        if (v instanceof Number) {
            ajr.chance[0] = ((Number) v).floatValue();
        }
        v = data.get("autojumpreset.accuracyMin");
        if (v instanceof Number) {
            ajr.accuracy[0] = ((Number) v).floatValue();
        }
        v = data.get("autojumpreset.accuracyMax");
        if (v instanceof Number) {
            ajr.accuracy[1] = ((Number) v).floatValue();
        }
        v = data.get("autojumpreset.onlyInFov");
        if (v instanceof Boolean) {
            ajr.onlyInFov.set((Boolean) v);
        }
        v = data.get("autojumpreset.liquidCheck");
        if (v instanceof Boolean) {
            ajr.liquidCheck.set((Boolean) v);
        }
    }

    private void applyAntiBot(Map<String, Object> data) {
        AntiBotFeature ab = AntiBotFeature.get();
        Object v = data.get("antibot.enabled");
        if (v instanceof Boolean) {
            ab.enabled.set((Boolean) v);
        }
        v = data.get("antibot.bind.key");
        if (v instanceof Number) {
            ab.bind.setKey(((Number) v).intValue());
        }
        v = data.get("antibot.bind.mode");
        if (v instanceof String) {
            try {
                ab.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("antibot.checkMovement");
        if (v instanceof Boolean) {
            ab.checkMovement.set((Boolean) v);
        }
        v = data.get("antibot.checkPing");
        if (v instanceof Boolean) {
            ab.checkPing.set((Boolean) v);
        }
        v = data.get("antibot.checkName");
        if (v instanceof Boolean) {
            ab.checkName.set((Boolean) v);
        }
        v = data.get("antibot.checkUserId");
        if (v instanceof Boolean) {
            ab.checkUserId.set((Boolean) v);
        }
    }

    private void applyArrayList(Map<String, Object> data) {
        ArrayListFeature al = ArrayListFeature.get();
        Object v = data.get("arraylist.enabled");
        if (v instanceof Boolean) {
            al.enabled.set((Boolean) v);
        }
        v = data.get("arraylist.bind.key");
        if (v instanceof Number) {
            al.bind.setKey(((Number) v).intValue());
        }
        v = data.get("arraylist.bind.mode");
        if (v instanceof String) {
            try {
                al.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("arraylist.position");
        if (v instanceof Number) {
            al.position.set(((Number) v).intValue());
        }
        v = data.get("arraylist.opacity");
        if (v instanceof Number) {
            al.opacity[0] = ((Number) v).floatValue();
        }
        v = data.get("arraylist.cornerRadius");
        if (v instanceof Number) {
            al.cornerRadius[0] = ((Number) v).floatValue();
        }
        v = data.get("arraylist.scale");
        if (v instanceof Number) {
            al.scale[0] = ((Number) v).floatValue();
        }
        v = data.get("arraylist.color.r");
        if (v instanceof Number) {
            al.color[0] = ((Number) v).floatValue();
        }
        v = data.get("arraylist.color.g");
        if (v instanceof Number) {
            al.color[1] = ((Number) v).floatValue();
        }
        v = data.get("arraylist.color.b");
        if (v instanceof Number) {
            al.color[2] = ((Number) v).floatValue();
        }
        v = data.get("arraylist.catCombat");
        if (v instanceof Boolean) {
            al.catCombat.set((Boolean) v);
        }
        v = data.get("arraylist.catUtility");
        if (v instanceof Boolean) {
            al.catUtility.set((Boolean) v);
        }
        v = data.get("arraylist.catVisuals");
        if (v instanceof Boolean) {
            al.catVisuals.set((Boolean) v);
        }
    }

    private void applyEsp(Map<String, Object> data) {
        EspFeature esp = EspFeature.get();
        Object v = data.get("esp.enabled");
        if (v instanceof Boolean) {
            esp.enabled.set((Boolean) v);
        }
        v = data.get("esp.bind.key");
        if (v instanceof Number) {
            esp.bind.setKey(((Number) v).intValue());
        }
        v = data.get("esp.bind.mode");
        if (v instanceof String) {
            try {
                esp.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("esp.targetPlayers");
        if (v instanceof Boolean) {
            esp.targetPlayers.set((Boolean) v);
        }
        v = data.get("esp.targetMobs");
        if (v instanceof Boolean) {
            esp.targetMobs.set((Boolean) v);
        }
        v = data.get("esp.targetFriendly");
        if (v instanceof Boolean) {
            esp.targetFriendly.set((Boolean) v);
        }
        v = data.get("esp.color.r");
        if (v instanceof Number) {
            esp.color[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.color.g");
        if (v instanceof Number) {
            esp.color[1] = ((Number) v).floatValue();
        }
        v = data.get("esp.color.b");
        if (v instanceof Number) {
            esp.color[2] = ((Number) v).floatValue();
        }
        v = data.get("esp.color.a");
        if (v instanceof Number) {
            esp.color[3] = ((Number) v).floatValue();
        }
        v = data.get("esp.outlineOpacity");
        if (v instanceof Number) {
            esp.outlineOpacity[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.thickness");
        if (v instanceof Number) {
            esp.thickness[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.cornerRadius");
        if (v instanceof Number) {
            esp.cornerRadius[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.boxOutline");
        if (v instanceof Boolean) {
            esp.boxOutline.set((Boolean) v);
        }
        v = data.get("esp.autoScale");
        if (v instanceof Boolean) {
            esp.autoScale.set((Boolean) v);
        }
        v = data.get("esp.boxSize");
        if (v instanceof Number) {
            esp.boxSize[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.names");
        if (v instanceof Boolean) {
            esp.names.set((Boolean) v);
        }
        v = data.get("esp.namesShadow");
        if (v instanceof Boolean) {
            esp.namesShadow.set((Boolean) v);
        }
        v = data.get("esp.namesSize");
        if (v instanceof Number) {
            esp.namesSize[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.namesBackground");
        if (v instanceof Boolean) {
            esp.namesBackground.set((Boolean) v);
        }
        v = data.get("esp.namesBgColor.r");
        if (v instanceof Number) {
            esp.namesBgColor[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.namesBgColor.g");
        if (v instanceof Number) {
            esp.namesBgColor[1] = ((Number) v).floatValue();
        }
        v = data.get("esp.namesBgColor.b");
        if (v instanceof Number) {
            esp.namesBgColor[2] = ((Number) v).floatValue();
        }
        v = data.get("esp.namesBgOpacity");
        if (v instanceof Number) {
            esp.namesBgOpacity[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.healthbar");
        if (v instanceof Boolean) {
            esp.healthbar.set((Boolean) v);
        }
        v = data.get("esp.healthbarThickness");
        if (v instanceof Number) {
            esp.healthbarThickness[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.healthbarGradient");
        if (v instanceof Boolean) {
            esp.healthbarGradient.set((Boolean) v);
        }
        v = data.get("esp.healthbarOpacity");
        if (v instanceof Number) {
            esp.healthbarOpacity[0] = ((Number) v).floatValue();
        }
        v = data.get("esp.healthbarOutline");
        if (v instanceof Boolean) {
            esp.healthbarOutline.set((Boolean) v);
        }
    }

    private void applyNametags(Map<String, Object> data) {
        NametagsFeature nt = NametagsFeature.get();
        Object v = data.get("nametags.enabled");
        if (v instanceof Boolean) {
            nt.enabled.set((Boolean) v);
        }
        v = data.get("nametags.bind.key");
        if (v instanceof Number) {
            nt.bind.setKey(((Number) v).intValue());
        }
        v = data.get("nametags.bind.mode");
        if (v instanceof String) {
            try {
                nt.bind.setMode(Keybind.Mode.valueOf((String) v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        v = data.get("nametags.targetPlayers");
        if (v instanceof Boolean) {
            nt.targetPlayers.set((Boolean) v);
        }
        v = data.get("nametags.targetMobs");
        if (v instanceof Boolean) {
            nt.targetMobs.set((Boolean) v);
        }
        v = data.get("nametags.targetInvisibles");
        if (v instanceof Boolean) {
            nt.targetInvisibles.set((Boolean) v);
        }
        v = data.get("nametags.autoScale");
        if (v instanceof Boolean) {
            nt.autoScale.set((Boolean) v);
        }
        v = data.get("nametags.scale");
        if (v instanceof Number) {
            nt.scale[0] = ((Number) v).floatValue();
        }
        v = data.get("nametags.showHealth");
        if (v instanceof Boolean) {
            nt.showHealth.set((Boolean) v);
        }
        v = data.get("nametags.showDistance");
        if (v instanceof Boolean) {
            nt.showDistance.set((Boolean) v);
        }
        v = data.get("nametags.showEffects");
        if (v instanceof Boolean) {
            nt.showEffects.set((Boolean) v);
        }
        v = data.get("nametags.effectScale");
        if (v instanceof Number) {
            nt.effectScale[0] = ((Number) v).floatValue();
        }
        v = data.get("nametags.showEquipment");
        if (v instanceof Boolean) {
            nt.showEquipment.set((Boolean) v);
        }
        v = data.get("nametags.background");
        if (v instanceof Boolean) {
            nt.background.set((Boolean) v);
        }
        v = data.get("nametags.bgOpacity");
        if (v instanceof Number) {
            nt.bgOpacity[0] = ((Number) v).floatValue();
        }
    }

    private void drawTabCards(int tab) {
        if (tab == 0) {
            drawAimAssist();
            drawCritAssist();
            drawTriggerbot();
        } else if (tab == 1) {
            drawAntiBot();
            drawAutoJumpReset();
            drawNoJumpDelay();
            drawSprint();
        } else if (tab == 2) {
            drawEsp();
            drawArrayList();
            drawNametags();
            drawHitmarkers();
        } else {
            drawConfigs();
            drawSession();
        }
    }

    private static float smootherStep(float t) {
        t = t < 0.0f ? 0.0f : (t > 1.0f ? 1.0f : t);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private String keyName(int key) {
        if (Keybind.isMouse(key)) {
            switch (key - Keybind.MOUSE_BASE) {
                case 0: return "LMB";
                case 1: return "RMB";
                case 2: return "MMB";
                case 3: return "MB4";
                case 4: return "MB5";
                default: return "MB" + (key - Keybind.MOUSE_BASE + 1);
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_ENTER: return "ENTER";
            case GLFW.GLFW_KEY_KP_ENTER: return "NUM ENTER";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            case GLFW.GLFW_KEY_BACKSPACE: return "BACKSPACE";
            case GLFW.GLFW_KEY_DELETE: return "DELETE";
            case GLFW.GLFW_KEY_ESCAPE: return "ESC";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "L SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "L CTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT: return "L ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R ALT";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "CAPS";
            case GLFW.GLFW_KEY_UP: return "UP";
            case GLFW.GLFW_KEY_DOWN: return "DOWN";
            case GLFW.GLFW_KEY_LEFT: return "LEFT";
            case GLFW.GLFW_KEY_RIGHT: return "RIGHT";
            case GLFW.GLFW_KEY_HOME: return "HOME";
            case GLFW.GLFW_KEY_END: return "END";
            case GLFW.GLFW_KEY_PAGE_UP: return "PG UP";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PG DN";
            default: break;
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        String named = GLFW.glfwGetKeyName(key, 0);
        if (named != null && !named.isEmpty()) {
            return named.toUpperCase();
        }
        return "KEY " + key;
    }
}
