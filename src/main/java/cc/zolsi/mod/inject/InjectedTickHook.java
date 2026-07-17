package cc.zolsi.mod.inject;

import cc.zolsi.mod.ZolsiLog;
import cc.zolsi.mod.gui.ImGuiManager;

public final class InjectedTickHook implements Runnable {

    public static final InjectedTickHook INSTANCE = new InjectedTickHook();

    private boolean loggedFirstCall;
    private boolean loggedError;

    @Override
    public void run() {
        if (!loggedFirstCall) {
            loggedFirstCall = true;
            ZolsiLog.log("injected tick hook run() first call");
        }
        try {
            ImGuiManager.get().tickGame();
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                ZolsiLog.log("injected tick hook run() threw", t);
            }
        }
    }

    private InjectedTickHook() {
    }
}
