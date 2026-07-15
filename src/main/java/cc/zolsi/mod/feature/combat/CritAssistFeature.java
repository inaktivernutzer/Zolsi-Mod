package cc.zolsi.mod.feature.combat;
import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.feature.utility.AntiBotFeature;

import cc.zolsi.mod.ZolsiCore;
import com.mojang.blaze3d.platform.InputConstants;
import imgui.type.ImBoolean;
import java.lang.reflect.Field;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class CritAssistFeature {

    private static final CritAssistFeature INSTANCE = new CritAssistFeature();

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImBoolean holdingWeapon = new ImBoolean(false);
    public final ImBoolean targetPlayers = new ImBoolean(false);
    public final ImBoolean targetMobs = new ImBoolean(true);
    public final ImBoolean targetInvisibles = new ImBoolean(false);

    private boolean releasing;
    private Field keyField;

    public static CritAssistFeature get() {
        return INSTANCE;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options == null) {
            releasing = false;
            return;
        }
        boolean active = enabled.get() && !ZolsiCore.get().isMenuOpen() && shouldRelease(mc);
        if (active) {
            mc.options.keyUp.setDown(false);
            releasing = true;
        } else if (releasing) {
            long window = GLFW.glfwGetCurrentContext();
            int code = boundKey(mc.options.keyUp).getValue();
            boolean physical = window != 0L && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
            mc.options.keyUp.setDown(physical);
            releasing = false;
        }
    }

    public void release() {
        if (!releasing) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            long window = GLFW.glfwGetCurrentContext();
            int code = boundKey(mc.options.keyUp).getValue();
            boolean physical = window != 0L && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
            mc.options.keyUp.setDown(physical);
        }
        releasing = false;
    }

    private InputConstants.Key boundKey(KeyMapping km) {
        try {
            if (keyField == null) {
                keyField = KeyMapping.class.getDeclaredField("key");
                keyField.setAccessible(true);
            }
            return (InputConstants.Key) keyField.get(km);
        } catch (Exception e) {
            return km.getDefaultKey();
        }
    }

    private boolean shouldRelease(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (holdingWeapon.get() && !player.getMainHandItem().has(DataComponents.WEAPON)) {
            return false;
        }
        if (player.onGround() || player.getDeltaMovement().y >= 0.0 || !player.isSprinting()) {
            return false;
        }
        return lookingAtTarget(mc, player);
    }

    private boolean lookingAtTarget(Minecraft mc, LocalPlayer player) {
        if (!(mc.hitResult instanceof EntityHitResult)) {
            return false;
        }
        Entity e = ((EntityHitResult) mc.hitResult).getEntity();
        return e != player && e instanceof LivingEntity && e.isAlive() && matches(e);
    }

    private boolean matches(Entity e) {
        if (e.isInvisible() && !targetInvisibles.get()) {
            return false;
        }
        if (e instanceof Player && AntiBotFeature.get().isBot((Player) e)) {
            return false;
        }
        return (targetPlayers.get() && e instanceof Player)
            || (targetMobs.get() && e instanceof Mob);
    }

    private CritAssistFeature() {
    }
}
