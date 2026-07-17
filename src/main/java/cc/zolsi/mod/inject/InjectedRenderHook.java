package cc.zolsi.mod.inject;

import cc.zolsi.mod.ZolsiLog;
import cc.zolsi.mod.gui.ImGuiManager;

public final class InjectedRenderHook implements Runnable {

    public static final InjectedRenderHook INSTANCE = new InjectedRenderHook();

    private boolean loggedFirstCall;
    private boolean loggedError;

    @Override
    public void run() {
        if (!loggedFirstCall) {
            loggedFirstCall = true;
            ZolsiLog.log("injected hook run() first call");
        }
        try {
            ImGuiManager.get().render();
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                ZolsiLog.log("injected hook run() threw", t);
            }
        }
    }

    private InjectedRenderHook() {
    }
}
