package cc.zolsi.mod.feature.combat;
import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.feature.utility.AntiBotFeature;

import cc.zolsi.mod.ZolsiCore;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class AimAssistFeature {

    private static final AimAssistFeature INSTANCE = new AimAssistFeature();

    public static final int MODE_ALWAYS = 0;
    public static final int MODE_MOUSE = 1;
    public static final int MODE_SILENT = 2;
    public static final int HITBOX_HEAD = 0;
    public static final int HITBOX_BODY = 1;
    public static final int HITBOX_NEAREST = 2;

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImBoolean targetPlayers = new ImBoolean(true);
    public final ImBoolean targetMobs = new ImBoolean(false);
    public final ImBoolean targetInvisibles = new ImBoolean(false);
    public final ImBoolean targetLock = new ImBoolean(false);
    public final ImInt inputMode = new ImInt(MODE_ALWAYS);
    public final ImBoolean verticalAim = new ImBoolean(true);
    public final float[] distance = {12.0f};
    public final float[] fov = {90.0f};
    public final ImInt hitbox = new ImInt(HITBOX_BODY);
    public final float[] multipoint = {75.0f};
    public final float[] yawSpeed = {0.01f};
    public final float[] pitchSpeed = {0.01f};
    public final float[] speedJitter = {20.0f};
    public final ImBoolean gcdFix = new ImBoolean(true);

    private double lastCursorX;
    private double lastCursorY;
    private boolean hasCursor;
    private Entity lockedTarget;
    private final float[] freelook = new float[3];
    private boolean freelookPublished;
    private float camYaw;
    private float camPitch;
    private boolean camInit;
    private double mouseDx;
    private double mouseDy;
    private double currentGcd;

    public static AimAssistFeature get() {
        return INSTANCE;
    }

    public void release() {
        freelook[2] = 0.0f;
        System.getProperties().remove("zolsi.freelook");
        freelookPublished = false;
        hasCursor = false;
        lockedTarget = null;
        camInit = false;
    }

    public void tick() {
        if (!freelookPublished) {
            System.getProperties().put("zolsi.freelook", freelook);
            freelookPublished = true;
        }
        freelook[2] = 0.0f;
        if (!enabled.get()) {
            hasCursor = false;
            lockedTarget = null;
            camInit = false;
            return;
        }
        if (ZolsiCore.get().isMenuOpen()) {
            hasCursor = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            hasCursor = false;
            return;
        }
        if (mc.gui == null || mc.gui.screen() != null) {
            hasCursor = false;
            return;
        }
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            return;
        }
        boolean moving = updateMouseMovement(window);
        if (inputMode.get() == MODE_MOUSE && !moving) {
            return;
        }
        LocalPlayer player = mc.player;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Entity target;
        if (targetLock.get()) {
            if (lockedTarget != null) {
                if (isDead(lockedTarget)) {
                    lockedTarget = null;
                    enabled.set(false);
                    camInit = false;
                    return;
                }
                if (!inRange(player, lockedTarget, pt)) {
                    camInit = false;
                    return;
                }
                target = lockedTarget;
            } else {
                target = findTarget(mc, player, pt);
                lockedTarget = target;
                if (target == null) {
                    camInit = false;
                    return;
                }
            }
        } else {
            lockedTarget = null;
            target = findTarget(mc, player, pt);
            if (target == null) {
                camInit = false;
                return;
            }
        }
        currentGcd = computeGcd(mc);
        if (inputMode.get() == MODE_SILENT) {
            silentAim(mc, player, target, pt);
        } else {
            camInit = false;
            aimAt(player, target, pt);
        }
    }

    private double computeGcd(Minecraft mc) {
        double sens = 0.4;
        try {
            sens = mc.options.sensitivity().get();
        } catch (Throwable ignored) {
        }
        double f = sens * 0.6 + 0.2;
        return f * f * f * 1.2;
    }

    private float applyStep(float cur, float diff, float speed) {
        float jitter = clamp01(speedJitter[0] / 100.0f);
        float factor = 1.0f + (float) ((Math.random() * 2.0 - 1.0) * jitter);
        float step = diff * clamp01(speed) * factor;
        float next = cur + step;
        if (gcdFix.get() && currentGcd > 1.0e-6) {
            double delta = next - cur;
            next = cur + (float) (Math.round(delta / currentGcd) * currentGcd);
        }
        return next;
    }

    private void silentAim(Minecraft mc, LocalPlayer player, Entity target, float pt) {
        if (!camInit) {
            camYaw = player.getYRot();
            camPitch = player.getXRot();
            camInit = true;
        }
        double sens = 0.4;
        try {
            sens = mc.options.sensitivity().get();
        } catch (Throwable ignored) {
        }
        double f = sens * 0.6 + 0.2;
        double degPerPixel = f * f * f * 8.0 * 0.15;
        camYaw += (float) (mouseDx * degPerPixel);
        camPitch = Math.max(-90.0f, Math.min(90.0f, camPitch + (float) (mouseDy * degPerPixel)));

        aimAt(player, target, pt);

        freelook[0] = camYaw;
        freelook[1] = camPitch;
        freelook[2] = 1.0f;
    }

    private boolean updateMouseMovement(long window) {
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(window, cx, cy);
        boolean moving = false;
        if (hasCursor) {
            mouseDx = cx[0] - lastCursorX;
            mouseDy = cy[0] - lastCursorY;
            moving = (mouseDx * mouseDx + mouseDy * mouseDy) > 0.01;
        } else {
            mouseDx = 0.0;
            mouseDy = 0.0;
        }
        lastCursorX = cx[0];
        lastCursorY = cy[0];
        hasCursor = true;
        return moving;
    }

    private Entity findTarget(Minecraft mc, LocalPlayer player, float pt) {
        Vec3 eye = player.getEyePosition(pt);
        Vec3 view = player.getViewVector(pt).normalize();
        double maxDist = distance[0];
        double maxDistSq = maxDist * maxDist;
        double halfFov = fov[0] * 0.5;
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == player || !(e instanceof LivingEntity) || !e.isAlive() || !matches(e)) {
                continue;
            }
            Vec3 pos = e.getPosition(pt);
            Vec3 center = new Vec3(pos.x, pos.y + e.getBbHeight() * 0.5, pos.z);
            if (eye.distanceToSqr(center) > maxDistSq) {
                continue;
            }
            Vec3 dir = center.subtract(eye);
            double len = dir.length();
            if (len < 1.0e-4) {
                continue;
            }
            double dot = dir.scale(1.0 / len).dot(view);
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
            if (angle <= halfFov && angle < bestAngle) {
                bestAngle = angle;
                best = e;
            }
        }
        return best;
    }

    private void aimAt(LocalPlayer player, Entity target, float pt) {
        Vec3 eye = player.getEyePosition(pt);
        Vec3 aim = aimPoint(player, target, pt, eye);
        double dX = aim.x - eye.x;
        double dY = aim.y - eye.y;
        double dZ = aim.z - eye.z;
        double horiz = Math.sqrt(dX * dX + dZ * dZ);
        float desiredYaw = (float) (Math.toDegrees(Math.atan2(dZ, dX)) - 90.0);
        float desiredPitch = (float) (-Math.toDegrees(Math.atan2(dY, horiz)));

        float curYaw = player.getYRot();
        float curPitch = player.getXRot();
        float yawDiff = (float) wrapDegrees(desiredYaw - curYaw);
        player.setYRot(applyStep(curYaw, yawDiff, yawSpeed[0]));
        if (verticalAim.get()) {
            float pitchDiff = desiredPitch - curPitch;
            float newPitch = applyStep(curPitch, pitchDiff, pitchSpeed[0]);
            player.setXRot(Math.max(-90.0f, Math.min(90.0f, newPitch)));
        }
    }

    private Vec3 aimPoint(LocalPlayer player, Entity target, float pt, Vec3 eye) {
        Vec3 pos = target.getPosition(pt);
        double w = target.getBbWidth() * 0.5;
        double h = target.getBbHeight();
        double minX = pos.x - w;
        double maxX = pos.x + w;
        double minZ = pos.z - w;
        double maxZ = pos.z + w;
        Vec3 dir = player.getViewVector(pt).normalize();

        double aimY;
        if (hitbox.get() == HITBOX_HEAD) {
            double band = Math.min(0.5, h * 0.3);
            aimY = pos.y + h - band * 0.5;
        } else if (hitbox.get() == HITBOX_BODY) {
            aimY = pos.y + h * 0.5;
        } else {
            double dx = pos.x - eye.x;
            double dz = pos.z - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            double viewHoriz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            double projY = viewHoriz > 1.0e-4 ? eye.y + dir.y / viewHoriz * horiz : pos.y + h * 0.5;
            aimY = clamp(projY, pos.y, pos.y + h);
        }

        double t = Math.max(0.0, (pos.x - eye.x) * dir.x + (aimY - eye.y) * dir.y + (pos.z - eye.z) * dir.z);
        double footX = eye.x + dir.x * t;
        double footZ = eye.z + dir.z * t;
        double nearX = clamp(footX, minX, maxX);
        double nearZ = clamp(footZ, minZ, maxZ);
        double pct = clamp(multipoint[0] / 100.0, 0.0, 1.0);
        double aimX = pos.x + (nearX - pos.x) * pct;
        double aimZ = pos.z + (nearZ - pos.z) * pct;
        return new Vec3(aimX, aimY, aimZ);
    }

    private boolean inRange(LocalPlayer player, Entity target, float pt) {
        Vec3 eye = player.getEyePosition(pt);
        Vec3 pos = target.getPosition(pt);
        Vec3 center = new Vec3(pos.x, pos.y + target.getBbHeight() * 0.5, pos.z);
        double maxDistSq = (double) distance[0] * distance[0];
        return eye.distanceToSqr(center) <= maxDistSq;
    }

    private boolean isDead(Entity e) {
        if (e.isRemoved() || !e.isAlive()) {
            return true;
        }
        return e instanceof LivingEntity && ((LivingEntity) e).isDeadOrDying();
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

    private static double wrapDegrees(double d) {
        double r = d % 360.0;
        if (r >= 180.0) {
            r -= 360.0;
        }
        if (r < -180.0) {
            r += 360.0;
        }
        return r;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private AimAssistFeature() {
    }
}
