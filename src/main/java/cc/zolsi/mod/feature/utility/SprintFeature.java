package cc.zolsi.mod.feature.utility;
import cc.zolsi.mod.feature.Keybind;

import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;

public final class SprintFeature {

    private static final SprintFeature INSTANCE = new SprintFeature();

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();

    private boolean applied;

    public static SprintFeature get() {
        return INSTANCE;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options == null) {
            applied = false;
            return;
        }
        if (enabled.get()) {
            mc.options.keySprint.setDown(true);
            applied = true;
        } else if (applied) {
            mc.options.keySprint.setDown(false);
            applied = false;
        }
    }

    public void release() {
        if (!applied) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.keySprint.setDown(false);
        }
        applied = false;
    }

    private SprintFeature() {
    }
}
