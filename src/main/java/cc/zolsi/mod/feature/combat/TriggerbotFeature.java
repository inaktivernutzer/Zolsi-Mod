package cc.zolsi.mod.feature.combat;
import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.feature.utility.AntiBotFeature;
import cc.zolsi.mod.ZolsiCore;
import cc.zolsi.mod.ZolsiLog;
import com.mojang.blaze3d.platform.InputConstants;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TriggerbotFeature {

    private static final TriggerbotFeature INSTANCE = new TriggerbotFeature();

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImBoolean targetPlayers = new ImBoolean(false);
    public final ImBoolean targetMobs = new ImBoolean(true);
    public final ImBoolean targetInvisibles = new ImBoolean(false);
    public final ImBoolean targetLock = new ImBoolean(false);
    public final ImBoolean playerPiercing = new ImBoolean(false);
    public final float[] cooldownGround = {1.0f};
    public final float[] cooldownAir = {0.8f};
    public final ImBoolean holdingWeapon = new ImBoolean(true);
    public final ImInt critMode = new ImInt(0);
    public final ImBoolean disableDelay = new ImBoolean(false);
    public final float[] delayJitter = {70.0f};
    public final ImBoolean clickSim = new ImBoolean(false);

    private long attackScheduledAt;
    private int lastAttackTick = -1;
    private String lastTrace = "";
    private Entity foundTarget;
    private Entity lockedTarget;
    private boolean prevAttackDown;
    private Field keyField;
    private boolean clickHeld;
    private Method onButtonMethod;
    private boolean clickReflectFailed;

    public static TriggerbotFeature get() {
        return INSTANCE;
    }

    public void detect() {
        foundTarget = null;
        if (!enabled.get()) {
            attackScheduledAt = 0L;
            lockedTarget = null;
            return;
        }
        if (ZolsiCore.get().isMenuOpen()) {
            attackScheduledAt = 0L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null) {
            attackScheduledAt = 0L;
            return;
        }
        if (mc.gui == null || mc.gui.screen() != null) {
            attackScheduledAt = 0L;
            trace("screen open");
            return;
        }
        LocalPlayer player = mc.player;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (!targetLock.get()) {
            lockedTarget = null;
        } else if (lockedTarget != null && (isDead(lockedTarget) || !matches(lockedTarget))) {
            lockedTarget = null;
        }
        long window = GLFW.glfwGetCurrentContext();
        boolean attackDown = window != 0L
            && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (targetLock.get() && lockedTarget != null && attackDown && !prevAttackDown) {
            Entity manual = vanillaTarget(mc, player);
            if (manual != null && manual != lockedTarget) {
                lockedTarget = manual;
            }
        }
        prevAttackDown = attackDown;
        Entity target;
        if (targetLock.get() && lockedTarget != null) {
            Entity locked = lockedTarget;
            if (!preciseHits(player, locked, pt)) {
                attackScheduledAt = 0L;
                trace("locked off-crosshair");
                return;
            }
            if (playerPiercing.get()) {
                if (blockedByWall(mc, player, locked, pt)) {
                    attackScheduledAt = 0L;
                    trace("locked wall-occluded");
                    return;
                }
            } else if (vanillaTarget(mc, player) != locked) {
                attackScheduledAt = 0L;
                trace("locked occluded");
                return;
            }
            target = locked;
        } else {
            target = vanillaTarget(mc, player);
            if (target == null) {
                attackScheduledAt = 0L;
                trace("no hitResult target");
                return;
            }
            if (!preciseHits(player, target, pt)) {
                attackScheduledAt = 0L;
                trace("precise-ray reject");
                return;
            }
        }
        if (holdingWeapon.get() && !player.getMainHandItem().has(DataComponents.WEAPON)) {
            attackScheduledAt = 0L;
            trace("holding-weapon gate");
            return;
        }
        float charge = player.getAttackStrengthScale(0.0f);
        float threshold = player.onGround() ? cooldownGround[0] : cooldownAir[0];
        if (charge < threshold) {
            attackScheduledAt = 0L;
            trace("charging");
            return;
        }
        if (!critAllowed(player)) {
            attackScheduledAt = 0L;
            trace("waiting for crit");
            return;
        }
        if (!disableDelay.get()) {
            long now = System.currentTimeMillis();
            if (attackScheduledAt == 0L) {
                attackScheduledAt = now + (long) (Math.random() * Math.max(0.0f, delayJitter[0]));
            }
            if (now < attackScheduledAt) {
                trace("waiting delay");
                return;
            }
        }
        foundTarget = target;
        trace("target ready");
    }

    public void attackTick() {
        releaseClick();
        Entity target = foundTarget;
        foundTarget = null;
        if (target == null) {
            return;
        }
        if (!enabled.get() || ZolsiCore.get().isMenuOpen()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        if (mc.gui == null || mc.gui.screen() != null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (!target.isAlive() || target.isRemoved() || !matches(target)) {
            return;
        }
        if (player.tickCount == lastAttackTick) {
            return;
        }
        boolean forceDirect = targetLock.get() && playerPiercing.get();
        if (clickSim.get() && !forceDirect) {
            simulateAttackButton(mc, true);
            clickHeld = true;
        } else {
            mc.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
        }
        if (targetLock.get()) {
            lockedTarget = target;
        }
        lastAttackTick = player.tickCount;
        attackScheduledAt = 0L;
        trace("attacked");
    }

    public void releaseClick() {
        if (!clickHeld) {
            return;
        }
        clickHeld = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            simulateAttackButton(mc, false);
        }
    }

    private void simulateAttackButton(Minecraft mc, boolean press) {
        if (!clickReflectFailed && mc.mouseHandler != null) {
            try {
                if (onButtonMethod == null) {
                    onButtonMethod = MouseHandler.class.getDeclaredMethod("onButton",
                        long.class, MouseButtonInfo.class, int.class);
                    onButtonMethod.setAccessible(true);
                }
                long window = GLFW.glfwGetCurrentContext();
                MouseButtonInfo info = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
                onButtonMethod.invoke(mc.mouseHandler, window, info,
                    press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE);
                return;
            } catch (Throwable t) {
                clickReflectFailed = true;
                ZolsiLog.log("triggerbot onButton sim failed; KeyMapping fallback", t);
            }
        }
        InputConstants.Key key = boundKey(mc.options.keyAttack);
        KeyMapping.set(key, press);
        if (press) {
            KeyMapping.click(key);
        }
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

    private Entity vanillaTarget(Minecraft mc, LocalPlayer player) {
        if (!(mc.hitResult instanceof EntityHitResult ehr)) {
            return null;
        }
        Entity e = ehr.getEntity();
        if (e == player || !(e instanceof LivingEntity) || !e.isAlive() || !matches(e)) {
            return null;
        }
        return e;
    }

    private boolean preciseHits(LocalPlayer player, Entity target, float pt) {
        Vec3 eye = player.getEyePosition(pt);
        double reach = player.entityInteractionRange();
        double w = target.getBbWidth() / 2.0;
        double h = target.getBbHeight();
        Vec3 p = target.getPosition(pt);
        AABB box = new AABB(p.x - w, p.y, p.z - w, p.x + w, p.y + h, p.z + w);
        if (box.contains(eye)) {
            return true;
        }
        Vec3 view = player.getViewVector(pt);
        Vec3 end = eye.add(view.x * reach, view.y * reach, view.z * reach);
        return box.clip(eye, end).isPresent();
    }

    private boolean blockedByWall(Minecraft mc, LocalPlayer player, Entity target, float pt) {
        if (mc.level == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition(pt);
        double w = target.getBbWidth() / 2.0;
        double h = target.getBbHeight();
        Vec3 p = target.getPosition(pt);
        AABB box = new AABB(p.x - w, p.y, p.z - w, p.x + w, p.y + h, p.z + w);
        if (box.contains(eye)) {
            return false;
        }
        double reach = player.entityInteractionRange();
        Vec3 view = player.getViewVector(pt);
        Vec3 end = eye.add(view.x * reach, view.y * reach, view.z * reach);
        Vec3 hit = box.clip(eye, end).orElse(null);
        if (hit == null) {
            return true;
        }
        HitResult br = mc.level.clip(new ClipContext(eye, hit,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return br != null && br.getType() == HitResult.Type.BLOCK;
    }

    private boolean isDead(Entity e) {
        if (e.isRemoved() || !e.isAlive()) {
            return true;
        }
        return e instanceof LivingEntity && ((LivingEntity) e).isDeadOrDying();
    }

    private void trace(String state) {
        if (!state.equals(lastTrace)) {
            lastTrace = state;
            ZolsiLog.log("triggerbot: " + state);
        }
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

    private boolean critAllowed(LocalPlayer p) {
        int mode = critMode.get();
        if (mode == 2) {
            return isCrit(p);
        }
        if (mode == 1) {
            return p.onGround() || isCrit(p);
        }
        return true;
    }

    private static boolean isCrit(LocalPlayer p) {
        return p.fallDistance > 0.0f && !p.onGround() && !p.onClimbable()
            && !p.isInWater() && !p.isSprinting();
    }

    private TriggerbotFeature() {
    }
}
