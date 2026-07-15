package cc.zolsi.mod.gui;

import cc.zolsi.mod.ZolsiLog;
import imgui.ImDrawData;
import imgui.gl3.ImGuiImplGl3;
import imgui.type.ImBoolean;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class StreamproofOverlay {

    private static final StreamproofOverlay INSTANCE = new StreamproofOverlay();

    public static StreamproofOverlay get() {
        return INSTANCE;
    }

    public final ImBoolean enabled = new ImBoolean(false);

    private static final byte[] PREMULT = buildPremultTable();

    private long mcWindow;
    private long mcWindowAtCreate;
    private long overlayWindow;
    private long overlayHwnd;
    private boolean created;
    private boolean failed;
    private boolean visible;
    private boolean loggedRenderError;

    private int fbo;
    private int fboTex;
    private int fboW;
    private int fboH;
    private final int[] pbo = new int[2];
    private int pboIndex;
    private boolean firstReadback;
    private final ByteBuffer[] readBuf = new ByteBuffer[2];
    private int curWrite;

    private static final double PRESENT_INTERVAL = 1.0 / 120.0;
    private double lastPresentTime = -1.0;

    private final Object frameLock = new Object();
    private Thread presentThread;
    private volatile boolean workerRunning;
    private ByteBuffer pending;
    private int pendingX;
    private int pendingY;
    private int pendingW;
    private int pendingH;
    private byte[] workerScratch;
    private boolean loggedWorkerError;

    private final int[] px = new int[1];
    private final int[] py = new int[1];
    private final int[] sw = new int[1];
    private final int[] sh = new int[1];
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private int lastW;
    private int lastH;

    private StreamproofOverlay() {
    }

    public boolean beginFrame(long currentMcWindow) {
        mcWindow = currentMcWindow;
        if (!enabled.get()) {
            hideIfVisible();
            return false;
        }
        if (failed) {
            return false;
        }
        if (created && mcWindowAtCreate != currentMcWindow) {
            destroy();
        }
        if (!created) {
            create(currentMcWindow);
            if (!created) {
                return false;
            }
        }
        reposition(currentMcWindow);
        if (!visible) {
            GLFW.glfwShowWindow(overlayWindow);
            visible = true;
            applyWindowFlags();
        }
        return true;
    }

    public void renderInto(ImGuiImplGl3 gl3, ImDrawData drawData, int fbW, int fbH) {
        if (!created || fbW <= 0 || fbH <= 0) {
            return;
        }
        double now = GLFW.glfwGetTime();
        if (lastPresentTime >= 0.0 && (now - lastPresentTime) < PRESENT_INTERVAL) {
            return;
        }
        lastPresentTime = now;
        try {
            int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            ensureResources(fbW, fbH);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, fbW, fbH);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            gl3.renderDrawData(drawData);

            int cur = pboIndex;
            int prev = pboIndex ^ 1;
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[cur]);
            GL11.glReadPixels(0, 0, fbW, fbH, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0L);

            boolean produced = false;
            if (!firstReadback) {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[prev]);
                ByteBuffer dst = readBuf[curWrite];
                dst.clear();
                GL15.glGetBufferSubData(GL21.GL_PIXEL_PACK_BUFFER, 0L, dst);
                produced = true;
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);

            if (produced) {
                GLFW.glfwGetWindowPos(mcWindow, px, py);
                synchronized (frameLock) {
                    if (pending == null) {
                        pending = readBuf[curWrite];
                        pendingX = px[0];
                        pendingY = py[0];
                        pendingW = fbW;
                        pendingH = fbH;
                        curWrite ^= 1;
                        frameLock.notifyAll();
                    }
                }
            }
            pboIndex = prev;
            firstReadback = false;
        } catch (Throwable t) {
            if (!loggedRenderError) {
                loggedRenderError = true;
                ZolsiLog.log("streamproof: overlay render failed", t);
            }
        }
    }

    private void workerLoop() {
        while (workerRunning) {
            ByteBuffer buf;
            int x;
            int y;
            int w;
            int h;
            synchronized (frameLock) {
                while (workerRunning && pending == null) {
                    try {
                        frameLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!workerRunning) {
                    return;
                }
                buf = pending;
                x = pendingX;
                y = pendingY;
                w = pendingW;
                h = pendingH;
            }
            try {
                int len = w * h * 4;
                if (workerScratch == null || workerScratch.length < len) {
                    workerScratch = new byte[len];
                }
                buf.rewind();
                buf.get(workerScratch, 0, len);
                premultiply(workerScratch, len);
                Win32.present(overlayHwnd, x, y, w, h, workerScratch, len);
            } catch (Throwable t) {
                if (!loggedWorkerError) {
                    loggedWorkerError = true;
                    ZolsiLog.log("streamproof: present worker failed", t);
                }
            } finally {
                synchronized (frameLock) {
                    pending = null;
                    frameLock.notifyAll();
                }
            }
        }
    }

    private void startWorker() {
        if (presentThread != null) {
            return;
        }
        workerRunning = true;
        presentThread = new Thread(this::workerLoop, "zolsi-streamproof-present");
        presentThread.setDaemon(true);
        presentThread.start();
    }

    private void stopWorker() {
        workerRunning = false;
        synchronized (frameLock) {
            pending = null;
            frameLock.notifyAll();
        }
        Thread t = presentThread;
        presentThread = null;
        if (t != null) {
            try {
                t.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void ensureResources(int w, int h) {
        if (fbo != 0 && fboW == w && fboH == h) {
            return;
        }
        if (fboTex != 0) {
            GL11.glDeleteTextures(fboTex);
            fboTex = 0;
        }
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (pbo[0] != 0) {
            GL15.glDeleteBuffers(pbo[0]);
            pbo[0] = 0;
        }
        if (pbo[1] != 0) {
            GL15.glDeleteBuffers(pbo[1]);
            pbo[1] = 0;
        }
        fboTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fboTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, fboTex, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        fboW = w;
        fboH = h;

        int bytes = w * h * 4;
        pbo[0] = GL15.glGenBuffers();
        pbo[1] = GL15.glGenBuffers();
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[0]);
        GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) bytes, GL15.GL_STREAM_READ);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo[1]);
        GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) bytes, GL15.GL_STREAM_READ);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        pboIndex = 0;
        firstReadback = true;

        synchronized (frameLock) {
            pending = null;
            readBuf[0] = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            readBuf[1] = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            curWrite = 0;
        }
        Win32.ensureSurface(w, h);
    }

    private static void premultiply(byte[] bgra, int len) {
        for (int i = 0; i < len; i += 4) {
            int a = bgra[i + 3] & 0xFF;
            if (a == 255) {
                continue;
            }
            if (a == 0) {
                bgra[i] = 0;
                bgra[i + 1] = 0;
                bgra[i + 2] = 0;
                continue;
            }
            int base = a << 8;
            bgra[i] = PREMULT[base | (bgra[i] & 0xFF)];
            bgra[i + 1] = PREMULT[base | (bgra[i + 1] & 0xFF)];
            bgra[i + 2] = PREMULT[base | (bgra[i + 2] & 0xFF)];
        }
    }

    private static byte[] buildPremultTable() {
        byte[] table = new byte[256 * 256];
        for (int a = 0; a < 256; a++) {
            int base = a << 8;
            for (int c = 0; c < 256; c++) {
                table[base | c] = (byte) ((c * a + 127) / 255);
            }
        }
        return table;
    }

    public void destroy() {
        stopWorker();
        if (fboTex != 0) {
            try {
                GL11.glDeleteTextures(fboTex);
            } catch (Throwable ignored) {
            }
            fboTex = 0;
        }
        if (fbo != 0) {
            try {
                GL30.glDeleteFramebuffers(fbo);
            } catch (Throwable ignored) {
            }
            fbo = 0;
        }
        if (pbo[0] != 0) {
            try {
                GL15.glDeleteBuffers(pbo[0]);
            } catch (Throwable ignored) {
            }
            pbo[0] = 0;
        }
        if (pbo[1] != 0) {
            try {
                GL15.glDeleteBuffers(pbo[1]);
            } catch (Throwable ignored) {
            }
            pbo[1] = 0;
        }
        fboW = 0;
        fboH = 0;
        readBuf[0] = null;
        readBuf[1] = null;
        workerScratch = null;
        Win32.releaseSurface();
        if (overlayWindow != 0L) {
            long win = overlayWindow;
            overlayWindow = 0L;
            overlayHwnd = 0L;
            try {
                GLFW.glfwDestroyWindow(win);
            } catch (Throwable t) {
                ZolsiLog.log("streamproof: overlay destroy failed", t);
            }
        }
        created = false;
        visible = false;
        failed = false;
        lastX = Integer.MIN_VALUE;
        lastY = Integer.MIN_VALUE;
        lastW = 0;
        lastH = 0;
    }

    private void hideIfVisible() {
        if (visible && overlayWindow != 0L) {
            GLFW.glfwHideWindow(overlayWindow);
            visible = false;
        }
    }

    private void create(long window) {
        try {
            GLFW.glfwGetWindowSize(window, sw, sh);
            int w = Math.max(sw[0], 1);
            int h = Math.max(sh[0], 1);

            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);

            long win = GLFW.glfwCreateWindow(w, h, "", 0L, 0L);
            GLFW.glfwDefaultWindowHints();
            if (win == 0L) {
                failed = true;
                ZolsiLog.log("streamproof: glfwCreateWindow returned 0, staying on Minecraft framebuffer");
                return;
            }
            overlayWindow = win;
            overlayHwnd = GLFWNativeWin32.glfwGetWin32Window(win);

            mcWindowAtCreate = window;
            created = true;
            lastPresentTime = -1.0;
            loggedWorkerError = false;
            applyWindowFlags();
            startWorker();
            ZolsiLog.log("streamproof: overlay window created (" + w + "x" + h + ", layered, async present)");
        } catch (Throwable t) {
            failed = true;
            ZolsiLog.log("streamproof: overlay creation failed", t);
        }
    }

    private void reposition(long window) {
        GLFW.glfwGetWindowPos(window, px, py);
        GLFW.glfwGetWindowSize(window, sw, sh);
        if (px[0] != lastX || py[0] != lastY || sw[0] != lastW || sh[0] != lastH) {
            GLFW.glfwSetWindowPos(overlayWindow, px[0], py[0]);
            GLFW.glfwSetWindowSize(overlayWindow, Math.max(sw[0], 1), Math.max(sh[0], 1));
            lastX = px[0];
            lastY = py[0];
            lastW = sw[0];
            lastH = sh[0];
        }
    }

    private void applyWindowFlags() {
        try {
            Win32.apply(overlayHwnd);
        } catch (Throwable t) {
            ZolsiLog.log("streamproof: could not apply layered/capture flags (overlay still shown)", t);
        }
    }

    private static final class Win32 {

        private static final int GWL_EXSTYLE = -20;
        private static final long WS_EX_TOOLWINDOW = 0x00000080L;
        private static final long WS_EX_NOACTIVATE = 0x08000000L;
        private static final long WS_EX_TOPMOST = 0x00000008L;
        private static final long WS_EX_APPWINDOW = 0x00040000L;
        private static final long WS_EX_LAYERED = 0x00080000L;
        private static final long WS_EX_TRANSPARENT = 0x00000020L;
        private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;
        private static final long HWND_TOPMOST = -1L;
        private static final int SWP_NOSIZE = 0x0001;
        private static final int SWP_NOMOVE = 0x0002;
        private static final int SWP_NOACTIVATE = 0x0010;
        private static final int SWP_FRAMECHANGED = 0x0020;
        private static final int ULW_ALPHA = 0x00000002;
        private static final int AC_SRC_OVER = 0x00;
        private static final int AC_SRC_ALPHA = 0x01;

        private static final Object DIB_LOCK = new Object();

        private static MethodHandle getExStyle;
        private static MethodHandle setExStyle;
        private static MethodHandle setAffinity;
        private static MethodHandle setWindowPos;
        private static MethodHandle updateLayered;
        private static MethodHandle createCompatibleDC;
        private static MethodHandle createDIBSection;
        private static MethodHandle selectObject;
        private static MethodHandle deleteObject;
        private static MethodHandle deleteDC;
        private static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
        private static final VarHandle LAST_ERROR =
            CAPTURE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));
        private static boolean initialized;
        private static boolean available;
        private static boolean gdiAvailable;
        private static boolean loggedPresent;

        private static long memDc;
        private static long dibBitmap;
        private static long dibOldBitmap;
        private static MemorySegment dib;
        private static int dibW;
        private static int dibH;

        private static synchronized void init() {
            if (initialized) {
                return;
            }
            initialized = true;
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
                SymbolLookup gdi32 = SymbolLookup.libraryLookup("gdi32", Arena.global());
                getExStyle = linker.downcallHandle(user32.find("GetWindowLongPtrW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
                setExStyle = linker.downcallHandle(user32.find("SetWindowLongPtrW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
                setAffinity = linker.downcallHandle(user32.find("SetWindowDisplayAffinity").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
                setWindowPos = linker.downcallHandle(user32.find("SetWindowPos").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                updateLayered = linker.downcallHandle(user32.find("UpdateLayeredWindow").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                    Linker.Option.captureCallState("GetLastError"));
                createCompatibleDC = linker.downcallHandle(gdi32.find("CreateCompatibleDC").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
                createDIBSection = linker.downcallHandle(gdi32.find("CreateDIBSection").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
                selectObject = linker.downcallHandle(gdi32.find("SelectObject").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
                deleteObject = linker.downcallHandle(gdi32.find("DeleteObject").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
                deleteDC = linker.downcallHandle(gdi32.find("DeleteDC").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
                available = true;
                gdiAvailable = true;
            } catch (Throwable t) {
                available = false;
                gdiAvailable = false;
                ZolsiLog.log("streamproof: user32/gdi32 FFM link failed (overlay disabled)", t);
            }
        }

        static void apply(long hwnd) throws Throwable {
            init();
            if (!available || hwnd == 0L) {
                return;
            }
            long ex = (long) getExStyle.invoke(hwnd, GWL_EXSTYLE);
            ex |= WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE | WS_EX_TOPMOST | WS_EX_LAYERED | WS_EX_TRANSPARENT;
            ex &= ~WS_EX_APPWINDOW;
            long ignoredStyle = (long) setExStyle.invoke(hwnd, GWL_EXSTYLE, ex);
            int ignoredAffinity = (int) setAffinity.invoke(hwnd, WDA_EXCLUDEFROMCAPTURE);
            int ignoredPos = (int) setWindowPos.invoke(hwnd, HWND_TOPMOST, 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_FRAMECHANGED);
        }

        static void ensureSurface(int w, int h) {
            init();
            if (!gdiAvailable) {
                return;
            }
            synchronized (DIB_LOCK) {
                if (dibBitmap != 0L && dibW == w && dibH == h) {
                    return;
                }
                releaseSurfaceLocked();
                try {
                    memDc = (long) createCompatibleDC.invoke(0L);
                    if (memDc == 0L) {
                        return;
                    }
                    try (Arena arena = Arena.ofConfined()) {
                        MemorySegment bmi = arena.allocate(40);
                        bmi.set(ValueLayout.JAVA_INT, 0, 40);
                        bmi.set(ValueLayout.JAVA_INT, 4, w);
                        bmi.set(ValueLayout.JAVA_INT, 8, h);
                        bmi.set(ValueLayout.JAVA_SHORT, 12, (short) 1);
                        bmi.set(ValueLayout.JAVA_SHORT, 14, (short) 32);
                        bmi.set(ValueLayout.JAVA_INT, 16, 0);
                        MemorySegment ppv = arena.allocate(ValueLayout.JAVA_LONG);
                        dibBitmap = (long) createDIBSection.invoke(memDc, bmi, 0, ppv, 0L, 0);
                        if (dibBitmap == 0L) {
                            releaseSurfaceLocked();
                            return;
                        }
                        long addr = ppv.get(ValueLayout.JAVA_LONG, 0);
                        dib = MemorySegment.ofAddress(addr).reinterpret((long) w * h * 4);
                        dibOldBitmap = (long) selectObject.invoke(memDc, dibBitmap);
                        dibW = w;
                        dibH = h;
                    }
                } catch (Throwable t) {
                    releaseSurfaceLocked();
                    ZolsiLog.log("streamproof: DIB surface creation failed", t);
                }
            }
        }

        static void releaseSurface() {
            synchronized (DIB_LOCK) {
                releaseSurfaceLocked();
            }
        }

        private static void releaseSurfaceLocked() {
            try {
                if (memDc != 0L && dibOldBitmap != 0L) {
                    selectObject.invoke(memDc, dibOldBitmap);
                }
                if (dibBitmap != 0L) {
                    deleteObject.invoke(dibBitmap);
                }
                if (memDc != 0L) {
                    deleteDC.invoke(memDc);
                }
            } catch (Throwable ignored) {
            }
            memDc = 0L;
            dibBitmap = 0L;
            dibOldBitmap = 0L;
            dib = null;
            dibW = 0;
            dibH = 0;
        }

        static void present(long hwnd, int x, int y, int w, int h, byte[] pixels, int len) throws Throwable {
            synchronized (DIB_LOCK) {
                if (!gdiAvailable || dib == null || hwnd == 0L || w != dibW || h != dibH) {
                    return;
                }
                MemorySegment.copy(MemorySegment.ofArray(pixels), 0L, dib, 0L, len);
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment ptDst = arena.allocate(8);
                    ptDst.set(ValueLayout.JAVA_INT, 0, x);
                    ptDst.set(ValueLayout.JAVA_INT, 4, y);
                    MemorySegment size = arena.allocate(8);
                    size.set(ValueLayout.JAVA_INT, 0, w);
                    size.set(ValueLayout.JAVA_INT, 4, h);
                    MemorySegment ptSrc = arena.allocate(8);
                    ptSrc.set(ValueLayout.JAVA_INT, 0, 0);
                    ptSrc.set(ValueLayout.JAVA_INT, 4, 0);
                    MemorySegment blend = arena.allocate(4);
                    blend.set(ValueLayout.JAVA_BYTE, 0, (byte) AC_SRC_OVER);
                    blend.set(ValueLayout.JAVA_BYTE, 1, (byte) 0);
                    blend.set(ValueLayout.JAVA_BYTE, 2, (byte) 255);
                    blend.set(ValueLayout.JAVA_BYTE, 3, (byte) AC_SRC_ALPHA);
                    MemorySegment callState = arena.allocate(CAPTURE_LAYOUT);
                    int ok = (int) updateLayered.invoke(callState, hwnd, 0L, ptDst, size, memDc, ptSrc, 0, blend, ULW_ALPHA);
                    if (!loggedPresent) {
                        loggedPresent = true;
                        int err = ok != 0 ? 0 : (int) LAST_ERROR.get(callState, 0L);
                        ZolsiLog.log("streamproof: UpdateLayeredWindow first call ok=" + ok + " err=" + err + " (" + w + "x" + h + ")");
                    }
                }
            }
        }
    }
}
