package cc.zolsi.mod.inject;

import java.util.function.Consumer;
import cc.zolsi.mod.ZolsiLog;
import cc.zolsi.mod.feature.visuals.NametagsFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class InjectedHudHook implements Consumer<Object> {

    public static final InjectedHudHook INSTANCE = new InjectedHudHook();

    private boolean loggedFirstCall;
    private boolean loggedError;

    @Override
    public void accept(Object extractor) {
        if (!loggedFirstCall) {
            loggedFirstCall = true;
            ZolsiLog.log("injected hud hook first call");
        }
        try {
            NametagsFeature feature = NametagsFeature.get();
            if (feature.enabled.get()) {
                System.getProperties().put(NameTagSuppressTransformer.HOOK_PROPERTY, Boolean.TRUE);
                feature.render((GuiGraphicsExtractor) extractor);
            } else {
                System.getProperties().remove(NameTagSuppressTransformer.HOOK_PROPERTY);
            }
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                ZolsiLog.log("injected hud hook threw", t);
            }
        }
    }

    private InjectedHudHook() {
    }
}
