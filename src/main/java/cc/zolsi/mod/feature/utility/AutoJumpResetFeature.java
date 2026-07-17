package cc.zolsi.mod.feature.utility;
import cc.zolsi.mod.feature.Keybind;

import imgui.type.ImBoolean;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class AutoJumpResetFeature {

    private static final float FOV_HALF = 75.0f;

    private static final AutoJumpResetFeature INSTANCE = new AutoJumpResetFeature();

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final float[] chance = {60.0f};
    public final float[] accuracy = {0.8f, 1.0f};
    public final ImBoolean onlyInFov = new ImBoolean(false);
    public final ImBoolean liquidCheck = new ImBoolean(true);

    private final Random random = new Random();
    private int prevHurtTime;
    private int pendingJump = -1;

    public static AutoJumpResetFeature get() {
        return INSTANCE;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            pendingJump = -1;
            prevHurtTime = 0;
            return;
        }
        LocalPlayer player = mc.player;
        if (!enabled.get()) {
            pendingJump = -1;
            prevHurtTime = player.hurtTime;
            return;
        }

        if (pendingJump > 0) {
            pendingJump--;
            if (pendingJump == 0) {
                if (canJump(player)) {
                    player.jumpFromGround();
                }
                pendingJump = -1;
            }
        }

        int hurt = player.hurtTime;
        boolean rising = hurt > prevHurtTime;
        prevHurtTime = hurt;
        if (!rising || !canJump(player)) {
            return;
        }
        if (attackerIsBot(player)) {
            return;
        }
        if (onlyInFov.get() && !attackerInFront(player)) {
            return;
        }
        if (random.nextFloat() * 100.0f >= chance[0]) {
            return;
        }
        float acc = accuracy[0] + random.nextFloat() * (accuracy[1] - accuracy[0]);
        if (random.nextFloat() <= acc) {
            player.jumpFromGround();
        } else {
            pendingJump = 1;
        }
    }

    private boolean canJump(LocalPlayer player) {
        if (!player.onGround()) {
            return false;
        }
        return !(liquidCheck.get() && (player.isInWater() || player.isInLava()));
    }

    private boolean attackerIsBot(LocalPlayer player) {
        LivingEntity attacker = player.getLastHurtByMob();
        return attacker instanceof Player && AntiBotFeature.get().isBot((Player) attacker);
    }

    private boolean attackerInFront(LocalPlayer player) {
        return Math.abs(wrapDegrees(player.getHurtDir() - 90.0f)) <= FOV_HALF;
    }

    private static float wrapDegrees(float angle) {
        float a = angle % 360.0f;
        if (a >= 180.0f) {
            a -= 360.0f;
        }
        if (a < -180.0f) {
            a += 360.0f;
        }
        return a;
    }

    private AutoJumpResetFeature() {
    }
}
