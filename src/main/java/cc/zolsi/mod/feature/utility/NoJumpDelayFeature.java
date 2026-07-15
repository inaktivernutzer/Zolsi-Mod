package cc.zolsi.mod.feature.utility;
import cc.zolsi.mod.feature.Keybind;

import imgui.type.ImBoolean;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

public final class NoJumpDelayFeature {

    private static final NoJumpDelayFeature INSTANCE = new NoJumpDelayFeature();

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final int[] delay = {0};

    private Field noJumpDelayField;
    private boolean fieldResolved;

    public static NoJumpDelayFeature get() {
        return INSTANCE;
    }

    public void tick() {
        if (!enabled.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        Field f = field();
        if (f == null) {
            return;
        }
        try {
            int cap = delay[0];
            if (f.getInt(mc.player) > cap) {
                f.setInt(mc.player, cap);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private Field field() {
        if (!fieldResolved) {
            fieldResolved = true;
            try {
                Field f = LivingEntity.class.getDeclaredField("noJumpDelay");
                f.setAccessible(true);
                noJumpDelayField = f;
            } catch (NoSuchFieldException | RuntimeException ignored) {
            }
        }
        return noJumpDelayField;
    }

    private NoJumpDelayFeature() {
    }
}
