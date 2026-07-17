package cc.zolsi.mod.gui;

import cc.zolsi.mod.ZolsiCore;
import cc.zolsi.mod.ZolsiLog;
import cc.zolsi.mod.feature.combat.AimAssistFeature;
import cc.zolsi.mod.feature.utility.AntiBotFeature;
import cc.zolsi.mod.feature.visuals.ArrayListFeature;
import cc.zolsi.mod.feature.utility.AutoJumpResetFeature;
import cc.zolsi.mod.feature.combat.CritAssistFeature;
import cc.zolsi.mod.feature.visuals.EspFeature;
import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.feature.visuals.NametagsFeature;
import cc.zolsi.mod.feature.utility.NoJumpDelayFeature;
import cc.zolsi.mod.feature.utility.SprintFeature;
import cc.zolsi.mod.feature.combat.TriggerbotFeature;
import net.minecraft.client.Minecraft;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.type.ImBoolean;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class ImGuiManager {

    static {
        pinImguiNative();
    }

    private static final ImGuiManager INSTANCE = new ImGuiManager();

    public static final int BODY_SIZE = 17;
    public static final int MEDIUM_SIZE = 18;
    public static final int TITLE_SIZE = 21;
    public static final int MONO_SIZE = 14;

    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();
    private final ClickGui clickGui = new ClickGui();

    private final double[] cursorX = new double[1];
    private final double[] cursorY = new double[1];
    private final int[] fbWidth = new int[1];
    private final int[] fbHeight = new int[1];
    private final int[] winWidth = new int[1];
    private final int[] winHeight = new int[1];

    private ImFont arraylistFont;
    private ImFont fontBody;
    private ImFont fontMedium;
    private ImFont fontTitle;
    private ImFont fontMono;
    private boolean initialized;
    private boolean openGlAvailable;
    private boolean mouseCaptured;
    private boolean toggleWasDown;
    private boolean loggedFirstFrame;
    private boolean loggedDraw;

    private boolean callbacksInstalled;
    private boolean unhooked;
    private boolean detached;
    private boolean glTornDown;
    private double lastFrameTime = -1.0;
    private long windowHandle;
    private GLFWKeyCallback ourKeyCallback;
    private GLFWCharCallback ourCharCallback;
    private GLFWCursorPosCallback ourCursorPosCallback;
    private GLFWMouseButtonCallback ourMouseButtonCallback;
    private GLFWScrollCallback ourScrollCallback;
    private GLFWKeyCallback prevKeyCallback;
    private GLFWCharCallback prevCharCallback;
    private GLFWCursorPosCallback prevCursorPosCallback;
    private GLFWMouseButtonCallback prevMouseButtonCallback;
    private GLFWScrollCallback prevScrollCallback;
    private double scrollAccumX;
    private double scrollAccumY;
    private int lastKeyPressed = -1;
    private boolean espBindWasDown;
    private boolean arrayBindWasDown;
    private boolean sprintBindWasDown;
    private boolean noJumpDelayBindWasDown;
    private boolean triggerbotBindWasDown;
    private boolean critAssistBindWasDown;
    private boolean aimAssistBindWasDown;
    private boolean nametagsBindWasDown;
    private boolean autoJumpResetBindWasDown;
    private boolean antiBotBindWasDown;
    private boolean loggedEspError;
    private boolean loggedArrayError;
    private boolean loggedDrawError;
    private boolean loggedOverlayError;
    private int fontTexId;
    private final boolean[] featureErrorLogged = new boolean[6];

    public static ImGuiManager get() {
        return INSTANCE;
    }

    private static void pinImguiNative() {
        try {
            java.io.File dir;
            String existing = System.getProperty("imgui.library.path");
            if (existing != null && !existing.isEmpty()) {
                dir = new java.io.File(existing);
            } else {
                dir = findLwjglNativeDir();
            }
            if (dir == null || !dir.isDirectory()) {
                ZolsiLog.log("lwjgl native dir not found; leaving imgui default extraction");
                return;
            }
            java.io.File dll = new java.io.File(dir, "imgui-java64.dll");
            if (!dll.isFile() || dll.length() == 0L) {
                try (java.io.InputStream in = ImGuiManager.class.getResourceAsStream("/io/imgui/java/native-bin/imgui-java64.dll")) {
                    if (in == null) {
                        ZolsiLog.log("imgui native not found in classpath; leaving imgui default extraction");
                        return;
                    }
                    java.nio.file.Files.copy(in, dll.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    ZolsiLog.log("imgui native re-extracted to " + dll.getAbsolutePath());
                }
            }
            if (!dll.isFile() || dll.length() == 0L) {
                return;
            }
            dll.deleteOnExit();
            System.setProperty("imgui.library.path", dir.getAbsolutePath());
            ZolsiLog.log("imgui native ensured at " + dll.getAbsolutePath());
        } catch (Throwable t) {
            ZolsiLog.log("imgui native pin failed; imgui will fall back to default temp extraction", t);
        }
    }

    private static java.io.File findLwjglNativeDir() {
        java.io.File tmp = new java.io.File(System.getProperty("java.io.tmpdir"));
        java.io.File[] roots = tmp.listFiles(f -> f.isDirectory()
            && f.getName().toLowerCase().startsWith("lwjgl"));
        if (roots == null) {
            return null;
        }
        java.io.File best = null;
        long bestTime = Long.MIN_VALUE;
        for (java.io.File root : roots) {
            java.io.File hit = findDirContaining(root, "lwjgl.dll", 4);
            if (hit != null && hit.lastModified() > bestTime) {
                bestTime = hit.lastModified();
                best = hit;
            }
        }
        return best;
    }

    private static java.io.File findDirContaining(java.io.File dir, String fileName, int depth) {
        if (dir == null || depth < 0) {
            return null;
        }
        java.io.File direct = new java.io.File(dir, fileName);
        if (direct.isFile()) {
            return dir;
        }
        java.io.File[] children = dir.listFiles(java.io.File::isDirectory);
        if (children == null) {
            return null;
        }
        for (java.io.File child : children) {
            java.io.File hit = findDirContaining(child, fileName, depth - 1);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    public void setMouseCaptured(boolean value) {
        this.mouseCaptured = value;
    }

    public ImFont fontBody() {
        return fontBody;
    }

    public ImFont fontMedium() {
        return fontMedium;
    }

    public ImFont fontTitle() {
        return fontTitle;
    }

    public ImFont fontMono() {
        return fontMono;
    }

    public ImFont arraylistFont() {
        return arraylistFont;
    }

    public void closeMenu() {
        ZolsiCore.get().setMenuOpen(false);
        mouseCaptured = false;
        long window = windowHandle != 0L ? windowHandle : GLFW.glfwGetCurrentContext();
        restoreGameCursor(window);
        dropCameraJump();
    }

    public int consumeKeyPress() {
        int key = lastKeyPressed;
        lastKeyPressed = -1;
        return key;
    }

    public void detach() {
        if (detached) {
            return;
        }
        detached = true;

        TriggerbotFeature.get().releaseClick();
        ZolsiCore.get().setMenuOpen(false);
        mouseCaptured = false;

        long window = windowHandle != 0L ? windowHandle : GLFW.glfwGetCurrentContext();

        if (window != 0L && callbacksInstalled) {
            GLFW.glfwSetKeyCallback(window, prevKeyCallback);
            GLFW.glfwSetCharCallback(window, prevCharCallback);
            GLFW.glfwSetCursorPosCallback(window, prevCursorPosCallback);
            GLFW.glfwSetMouseButtonCallback(window, prevMouseButtonCallback);
            GLFW.glfwSetScrollCallback(window, prevScrollCallback);
            freeCallback(ourKeyCallback);
            freeCallback(ourCharCallback);
            freeCallback(ourCursorPosCallback);
            freeCallback(ourMouseButtonCallback);
            freeCallback(ourScrollCallback);
            ourKeyCallback = null;
            ourCharCallback = null;
            ourCursorPosCallback = null;
            ourMouseButtonCallback = null;
            ourScrollCallback = null;
            prevKeyCallback = null;
            prevCharCallback = null;
            prevCursorPosCallback = null;
            prevMouseButtonCallback = null;
            prevScrollCallback = null;
            callbacksInstalled = false;
        }

        restoreGameCursor(window);
        dropCameraJump();

        SprintFeature.get().release();
        CritAssistFeature.get().release();
        AimAssistFeature.get().release();

        System.getProperties().remove(cc.zolsi.mod.inject.TickHookTransformer.HOOK_PROPERTY);
        System.getProperties().remove(cc.zolsi.mod.inject.HudHookTransformer.HOOK_PROPERTY);
        System.getProperties().remove(cc.zolsi.mod.inject.NameTagSuppressTransformer.HOOK_PROPERTY);
        System.getProperties().remove(cc.zolsi.mod.inject.FreelookTransformer.PROPERTY);

        ZolsiLog.log("detached: input/camera restored, tick/hud/nametag/freelook hooks cleared");
    }

    public void finishUnhook() {
        if (unhooked) {
            return;
        }
        unhooked = true;
        detach();

        StreamproofOverlay.get().destroy();
        System.getProperties().remove(cc.zolsi.mod.inject.RenderHookTransformer.HOOK_PROPERTY);

        ZolsiLog.log("unhook complete: render hook cleared");
    }

    public void unhook() {
        finishUnhook();
    }

    private void teardownGl() {
        if (glTornDown) {
            return;
        }
        glTornDown = true;
        try {
            gl3.shutdown();
        } catch (Throwable t) {
            ZolsiLog.log("imgui teardown: gl3 shutdown failed", t);
        }
        try {
            ImGui.destroyContext();
        } catch (Throwable t) {
            ZolsiLog.log("imgui teardown: destroyContext failed", t);
        }
        arraylistFont = null;
        fontBody = null;
        fontMedium = null;
        fontTitle = null;
        fontMono = null;
        fontTexId = 0;
        initialized = false;
        openGlAvailable = false;
        loggedFirstFrame = false;
        loggedDraw = false;
        lastFrameTime = -1.0;
        ZolsiLog.log("imgui teardown: gl3 disposed, context destroyed, fonts released");
        teardownAgent();
    }

    private void teardownAgent() {
        try {
            ClassLoader system = ClassLoader.getSystemClassLoader();
            Class<?> agent = Class.forName("cc.zolsi.mod.inject.ZolsiAgent", true, system);
            agent.getMethod("teardown").invoke(null);
            ZolsiLog.log("agent teardown invoked (transformers removed, bytecode reverted, imgui native unloaded, jar lock released, memory freed)");
        } catch (Throwable t) {
            ZolsiLog.log("agent teardown invoke failed", t);
        }
    }

    private static void freeCallback(org.lwjgl.system.Callback callback) {
        if (callback != null) {
            callback.free();
        }
    }

    public void render() {
        if (unhooked) {
            return;
        }
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            ZolsiLog.log("render() reached; GLFW context=" + GLFW.glfwGetCurrentContext());
        }

        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            return;
        }

        if (!initialized) {
            init(window);
        }
        if (!openGlAvailable) {
            return;
        }

        pollToggle(window);
        pollBinds(window);
        tickFeatures();

        if (StreamproofOverlay.get().enabled.get() && NametagsFeature.get().enabled.get()) {
            NametagsFeature.get().enabled.set(false);
        }

        GLFW.glfwGetFramebufferSize(window, fbWidth, fbHeight);
        GLFW.glfwGetWindowSize(window, winWidth, winHeight);
        if (fbWidth[0] <= 0 || fbHeight[0] <= 0) {
            return;
        }

        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(fbWidth[0], fbHeight[0]);
        float scaleX = winWidth[0] > 0 ? (float) fbWidth[0] / winWidth[0] : 1.0f;
        float scaleY = winHeight[0] > 0 ? (float) fbHeight[0] / winHeight[0] : 1.0f;
        io.setDisplayFramebufferScale(1.0f, 1.0f);

        if (mouseCaptured) {
            GLFW.glfwGetCursorPos(window, cursorX, cursorY);
            io.setMousePos((float) cursorX[0] * scaleX, (float) cursorY[0] * scaleY);
            io.setMouseDown(0, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS);
            io.setMouseDown(1, GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS);
            io.setMouseWheel((float) scrollAccumY);
            io.setMouseWheelH((float) scrollAccumX);
        } else {
            io.setMouseDown(0, false);
            io.setMouseDown(1, false);
            io.setMouseWheel(0.0f);
            io.setMouseWheelH(0.0f);
        }
        scrollAccumX = 0.0;
        scrollAccumY = 0.0;

        double nowTime = GLFW.glfwGetTime();
        float frameDt = lastFrameTime < 0.0 ? (1.0f / 60.0f) : (float) (nowTime - lastFrameTime);
        lastFrameTime = nowTime;
        if (frameDt < 0.0001f) {
            frameDt = 0.0001f;
        } else if (frameDt > 0.25f) {
            frameDt = 0.25f;
        }
        io.setDeltaTime(frameDt);

        gl3.newFrame();
        ImGui.newFrame();
        if (EspFeature.get().enabled.get()) {
            try {
                EspFeature.get().render(fbWidth[0], fbHeight[0], arraylistFont, ArrayListFeature.FONT_BAKE_PX);
            } catch (Throwable t) {
                if (!loggedEspError) {
                    loggedEspError = true;
                    ZolsiLog.log("esp render failed", t);
                }
            }
        }
        if (ArrayListFeature.get().enabled.get()) {
            try {
                ArrayListFeature.get().render(fbWidth[0], fbHeight[0], arraylistFont, ArrayListFeature.FONT_BAKE_PX);
            } catch (Throwable t) {
                if (!loggedArrayError) {
                    loggedArrayError = true;
                    ZolsiLog.log("arraylist render failed", t);
                }
            }
        }
        try {
            clickGui.draw();
        } catch (Throwable t) {
            Theme.setGlobalAlpha(1.0f);
            Theme.setLayerAlpha(1.0f);
            if (!loggedDrawError) {
                loggedDrawError = true;
                ZolsiLog.log("clickGui draw failed", t);
            }
        }
        ImGui.render();

        if (unhooked) {
            teardownGl();
            return;
        }

        StreamproofOverlay overlay = StreamproofOverlay.get();
        boolean toOverlay;
        try {
            toOverlay = overlay.beginFrame(window);
        } catch (Throwable t) {
            toOverlay = false;
            if (!loggedOverlayError) {
                loggedOverlayError = true;
                ZolsiLog.log("streamproof beginFrame failed", t);
            }
        }

        if (toOverlay) {
            overlay.renderInto(gl3, ImGui.getDrawData(), fbWidth[0], fbHeight[0]);
        } else {
            int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glViewport(0, 0, fbWidth[0], fbHeight[0]);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            gl3.renderDrawData(ImGui.getDrawData());
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        }

        if (ZolsiCore.get().isMenuOpen() && !loggedDraw) {
            loggedDraw = true;
            ZolsiLog.log("draw pass: fb=" + fbWidth[0] + "x" + fbHeight[0]
                + " target=" + (toOverlay ? "streamproof-overlay" : "minecraft")
                + " cmdLists=" + ImGui.getDrawData().getCmdListsCount());
        }
    }

    private void pollToggle(long window) {
        boolean down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_INSERT) == GLFW.GLFW_PRESS;
        if (down && !toggleWasDown) {
            boolean open = !ZolsiCore.get().isMenuOpen();
            ZolsiCore.get().setMenuOpen(open);
            setMouseCaptured(open);
            if (open) {
                releaseGameCursor(window);
            } else {
                restoreGameCursor(window);
                dropCameraJump();
            }
            ZolsiLog.log("INSERT toggled menu " + (open ? "open" : "closed"));
        }
        toggleWasDown = down;
    }

    private void pollBinds(long window) {
        espBindWasDown = pollBind(window, EspFeature.get().bind, EspFeature.get().enabled, espBindWasDown);
        arrayBindWasDown = pollBind(window, ArrayListFeature.get().bind, ArrayListFeature.get().enabled, arrayBindWasDown);
        sprintBindWasDown = pollBind(window, SprintFeature.get().bind, SprintFeature.get().enabled, sprintBindWasDown);
        noJumpDelayBindWasDown = pollBind(window, NoJumpDelayFeature.get().bind, NoJumpDelayFeature.get().enabled, noJumpDelayBindWasDown);
        autoJumpResetBindWasDown = pollBind(window, AutoJumpResetFeature.get().bind, AutoJumpResetFeature.get().enabled, autoJumpResetBindWasDown);
        antiBotBindWasDown = pollBind(window, AntiBotFeature.get().bind, AntiBotFeature.get().enabled, antiBotBindWasDown);
        triggerbotBindWasDown = pollBind(window, TriggerbotFeature.get().bind, TriggerbotFeature.get().enabled, triggerbotBindWasDown);
        critAssistBindWasDown = pollBind(window, CritAssistFeature.get().bind, CritAssistFeature.get().enabled, critAssistBindWasDown);
        aimAssistBindWasDown = pollBind(window, AimAssistFeature.get().bind, AimAssistFeature.get().enabled, aimAssistBindWasDown);
        if (StreamproofOverlay.get().enabled.get()) {
            nametagsBindWasDown = false;
        } else {
            nametagsBindWasDown = pollBind(window, NametagsFeature.get().bind, NametagsFeature.get().enabled, nametagsBindWasDown);
        }
    }

    private void releaseGameCursor(long window) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.mouseHandler != null) {
            mc.mouseHandler.releaseMouse();
        } else if (window != 0L) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    private void restoreGameCursor(long window) {
        Minecraft mc = Minecraft.getInstance();
        boolean screenOpen = mc != null && mc.gui != null && mc.gui.screen() != null;
        boolean inWorld = mc != null && mc.level != null && mc.player != null;
        if (mc != null && mc.mouseHandler != null) {
            if (screenOpen || !inWorld) {
                mc.mouseHandler.releaseMouse();
            } else {
                mc.mouseHandler.grabMouse();
            }
        } else if (window != 0L) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        }
    }

    private void dropCameraJump() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.mouseHandler != null) {
                mc.mouseHandler.setIgnoreFirstMove();
            }
        } catch (Throwable ignored) {
        }
    }

    private void tickFeatures() {
        tickSafe(SprintFeature.get()::tick, 0);
        tickSafe(NoJumpDelayFeature.get()::tick, 1);
        tickSafe(CritAssistFeature.get()::tick, 2);
        tickSafe(TriggerbotFeature.get()::detect, 3);
        tickSafe(AimAssistFeature.get()::tick, 4);
    }

    public void tickGame() {
        if (unhooked) {
            return;
        }
        tickSafe(TriggerbotFeature.get()::attackTick, 3);
        tickSafe(AutoJumpResetFeature.get()::tick, 5);
    }

    private void tickSafe(Runnable feature, int index) {
        try {
            feature.run();
        } catch (Throwable t) {
            if (!featureErrorLogged[index]) {
                featureErrorLogged[index] = true;
                ZolsiLog.log("feature tick failed [" + index + "]", t);
            }
        }
    }

    private boolean pollBind(long window, Keybind bind, ImBoolean target, boolean wasDown) {
        if (!bind.isBound()) {
            return false;
        }
        int key = bind.getKey();
        boolean down = Keybind.isMouse(key)
            ? GLFW.glfwGetMouseButton(window, key - Keybind.MOUSE_BASE) == GLFW.GLFW_PRESS
            : GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        if (ZolsiCore.get().isMenuOpen()) {
            return down;
        }
        if (bind.getMode() == Keybind.Mode.HOLD) {
            target.set(down);
        } else if (down && !wasDown) {
            target.set(!target.get());
        }
        return down;
    }

    private void init(long window) {
        initialized = true;
        windowHandle = window;

        try {
            String glVersion = GL11.glGetString(GL11.GL_VERSION);
            if (glVersion == null) {
                openGlAvailable = false;
                ZolsiLog.log("No OpenGL context. Zolsi-Mod is OpenGL only; set Graphics API to OpenGL.");
                return;
            }

            ImGui.createContext();
            ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null);
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
            io.setConfigWindowsResizeFromEdges(true);
            loadFonts(io);
            applyStyle();
            gl3.init("#version 150");
            enableFontMipmaps(io);
            openGlAvailable = true;
            installInputCallbacks(window);
            ZolsiLog.log("ImGui initialized on OpenGL " + glVersion);
        } catch (Throwable t) {
            openGlAvailable = false;
            ZolsiLog.log("ImGui init failed", t);
        }
    }

    public int fontTextureId() {
        if (fontTexId == 0) {
            fontTexId = readFontTexture();
        }
        return fontTexId;
    }

    private int readFontTexture() {
        try {
            java.lang.reflect.Field dataField = gl3.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            Object data = dataField.get(gl3);
            java.lang.reflect.Field texField = data.getClass().getDeclaredField("fontTexture");
            texField.setAccessible(true);
            return texField.getInt(data);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void enableFontMipmaps(ImGuiIO io) {
        try {
            int tex = readFontTexture();
            fontTexId = tex;
            if (tex == 0) {
                return;
            }
            int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            ZolsiLog.log("font atlas mipmaps enabled");
        } catch (Throwable t) {
            ZolsiLog.log("font mipmap setup failed", t);
        }
    }

    private static byte[] readResource(String path) {
        try (java.io.InputStream in = ImGuiManager.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }

    private void loadFonts(ImGuiIO io) {
        io.getFonts().setTexGlyphPadding(3);
        byte[] regular = readResource("/assets/zolsi/font/Geist-Regular.ttf");
        byte[] medium = readResource("/assets/zolsi/font/Geist-Medium.ttf");
        byte[] semi = readResource("/assets/zolsi/font/Geist-SemiBold.ttf");
        byte[] monoData = readResource("/assets/zolsi/font/JetBrainsMono-Regular.ttf");

        if (regular == null || medium == null || semi == null || monoData == null) {
            ZolsiLog.log("font resources missing, falling back to default font");
            ImFont fallback = io.getFonts().addFontDefault();
            fontBody = fallback;
            fontMedium = fallback;
            fontTitle = fallback;
            fontMono = fallback;
            ImFontConfig fallbackConfig = new ImFontConfig();
            fallbackConfig.setSizePixels(ArrayListFeature.FONT_BAKE_PX);
            fallbackConfig.setOversampleH(3);
            fallbackConfig.setOversampleV(3);
            fallbackConfig.setPixelSnapH(true);
            arraylistFont = io.getFonts().addFontDefault(fallbackConfig);
            fallbackConfig.destroy();
            return;
        }

        ImFontConfig fontConfig = new ImFontConfig();
        fontConfig.setOversampleH(3);
        fontConfig.setOversampleV(3);
        fontConfig.setPixelSnapH(true);
        fontConfig.setRasterizerMultiply(1.1f);
        fontBody = io.getFonts().addFontFromMemoryTTF(regular, BODY_SIZE, fontConfig);
        fontMedium = io.getFonts().addFontFromMemoryTTF(medium, MEDIUM_SIZE, fontConfig);
        fontTitle = io.getFonts().addFontFromMemoryTTF(semi, TITLE_SIZE, fontConfig);
        fontMono = io.getFonts().addFontFromMemoryTTF(monoData, MONO_SIZE, fontConfig);
        arraylistFont = io.getFonts().addFontFromMemoryTTF(semi, ArrayListFeature.FONT_BAKE_PX, fontConfig);
        fontConfig.destroy();
        io.setFontDefault(fontBody);
        ZolsiLog.log("custom fonts loaded (Geist + JetBrains Mono)");
    }

    private void installInputCallbacks(long window) {
        if (callbacksInstalled) {
            return;
        }
        callbacksInstalled = true;

        ourKeyCallback = GLFWKeyCallback.create((w, key, scancode, action, mods) -> {
            if (ZolsiCore.get().isMenuOpen()) {
                if (action == GLFW.GLFW_PRESS) {
                    lastKeyPressed = key;
                }
                return;
            }
            if (prevKeyCallback != null) {
                prevKeyCallback.invoke(w, key, scancode, action, mods);
            }
        });
        prevKeyCallback = GLFW.glfwSetKeyCallback(window, ourKeyCallback);

        ourCharCallback = GLFWCharCallback.create((w, codepoint) -> {
            if (ZolsiCore.get().isMenuOpen()) {
                ImGui.getIO().addInputCharacter(codepoint);
                return;
            }
            if (prevCharCallback != null) {
                prevCharCallback.invoke(w, codepoint);
            }
        });
        prevCharCallback = GLFW.glfwSetCharCallback(window, ourCharCallback);

        ourCursorPosCallback = GLFWCursorPosCallback.create((w, xpos, ypos) -> {
            if (ZolsiCore.get().isMenuOpen()) {
                return;
            }
            if (prevCursorPosCallback != null) {
                prevCursorPosCallback.invoke(w, xpos, ypos);
            }
        });
        prevCursorPosCallback = GLFW.glfwSetCursorPosCallback(window, ourCursorPosCallback);

        ourMouseButtonCallback = GLFWMouseButtonCallback.create((w, button, action, mods) -> {
            if (ZolsiCore.get().isMenuOpen()) {
                if (action == GLFW.GLFW_PRESS) {
                    lastKeyPressed = Keybind.MOUSE_BASE + button;
                }
                return;
            }
            if (prevMouseButtonCallback != null) {
                prevMouseButtonCallback.invoke(w, button, action, mods);
            }
        });
        prevMouseButtonCallback = GLFW.glfwSetMouseButtonCallback(window, ourMouseButtonCallback);

        ourScrollCallback = GLFWScrollCallback.create((w, xoffset, yoffset) -> {
            if (ZolsiCore.get().isMenuOpen()) {
                scrollAccumX += xoffset;
                scrollAccumY += yoffset;
                return;
            }
            if (prevScrollCallback != null) {
                prevScrollCallback.invoke(w, xoffset, yoffset);
            }
        });
        prevScrollCallback = GLFW.glfwSetScrollCallback(window, ourScrollCallback);

        ZolsiLog.log("Input callbacks installed (menu-open input is blocked from Minecraft)");
    }

    private void applyStyle() {
        ImGuiStyle style = ImGui.getStyle();

        style.setAntiAliasedLines(true);
        style.setAntiAliasedLinesUseTex(false);
        style.setAntiAliasedFill(true);
        style.setCircleTessellationMaxError(0.10f);
        style.setCurveTessellationTol(0.65f);
        style.setWindowRounding(12.0f);
        style.setChildRounding(8.0f);
        style.setFrameRounding(8.0f);
        style.setGrabRounding(8.0f);
        style.setTabRounding(8.0f);
        style.setScrollbarRounding(8.0f);
        style.setPopupRounding(10.0f);
        style.setWindowBorderSize(1.0f);
        style.setChildBorderSize(0.0f);
        style.setFrameBorderSize(0.0f);
        style.setPopupBorderSize(1.0f);
        style.setWindowPadding(14.0f, 12.0f);
        style.setFramePadding(10.0f, 8.0f);
        style.setItemSpacing(10.0f, 8.0f);
        style.setScrollbarSize(8.0f);
        style.setGrabMinSize(10.0f);

        setCol(style, ImGuiCol.Text, Theme.TEXT, 1.0f);
        setCol(style, ImGuiCol.TextDisabled, Theme.DIM, 1.0f);
        setCol(style, ImGuiCol.WindowBg, Theme.BG, 0.98f);
        setCol(style, ImGuiCol.ChildBg, Theme.BG, 0.0f);
        setCol(style, ImGuiCol.PopupBg, Theme.CARD, 0.99f);
        setCol(style, ImGuiCol.Border, Theme.BORDER, 1.0f);
        setCol(style, ImGuiCol.FrameBg, Theme.FIELD, 1.0f);
        setCol(style, ImGuiCol.FrameBgHovered, Theme.FIELD_HI, 1.0f);
        setCol(style, ImGuiCol.FrameBgActive, Theme.FIELD_HI, 1.0f);
        setCol(style, ImGuiCol.TitleBg, Theme.BG, 1.0f);
        setCol(style, ImGuiCol.TitleBgActive, Theme.BG, 1.0f);
        setCol(style, ImGuiCol.TitleBgCollapsed, Theme.BG, 1.0f);
        setCol(style, ImGuiCol.CheckMark, Theme.ACCENT, 1.0f);
        setCol(style, ImGuiCol.SliderGrab, Theme.ACCENT, 1.0f);
        setCol(style, ImGuiCol.SliderGrabActive, Theme.TEXT, 1.0f);
        setCol(style, ImGuiCol.Button, Theme.FIELD, 1.0f);
        setCol(style, ImGuiCol.ButtonHovered, Theme.FIELD_HI, 1.0f);
        setCol(style, ImGuiCol.ButtonActive, Theme.FIELD_HI, 1.0f);
        setCol(style, ImGuiCol.Header, Theme.FIELD, 1.0f);
        setCol(style, ImGuiCol.HeaderHovered, Theme.FIELD_HI, 1.0f);
        setCol(style, ImGuiCol.HeaderActive, Theme.FIELD_HI, 1.0f);
        setCol(style, ImGuiCol.Separator, Theme.BORDER, 1.0f);
        setCol(style, ImGuiCol.SeparatorHovered, Theme.BORDER, 1.0f);
        setCol(style, ImGuiCol.SeparatorActive, Theme.ACCENT, 1.0f);
        setCol(style, ImGuiCol.ScrollbarBg, Theme.BG, 0.0f);
        setCol(style, ImGuiCol.ScrollbarGrab, Theme.BORDER, 1.0f);
        setCol(style, ImGuiCol.ScrollbarGrabHovered, Theme.FAINT, 1.0f);
        setCol(style, ImGuiCol.ScrollbarGrabActive, Theme.DIM, 1.0f);
        setCol(style, ImGuiCol.TextSelectedBg, Theme.ACCENT, 0.35f);
        setCol(style, ImGuiCol.NavHighlight, Theme.ACCENT, 0.0f);
    }

    private static void setCol(ImGuiStyle style, int idx, float[] c, float alpha) {
        style.setColor(idx, c[0], c[1], c[2], c[3] * alpha);
    }
}
