package cc.zolsi.mod.feature.visuals;
import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.feature.utility.AntiBotFeature;

import imgui.type.ImBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class NametagsFeature {

    private static final NametagsFeature INSTANCE = new NametagsFeature();

    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImBoolean targetPlayers = new ImBoolean(true);
    public final ImBoolean targetMobs = new ImBoolean(false);
    public final ImBoolean targetInvisibles = new ImBoolean(false);
    public final ImBoolean autoScale = new ImBoolean(true);
    public final float[] scale = {1.0f};
    public final ImBoolean showHealth = new ImBoolean(true);
    public final ImBoolean showDistance = new ImBoolean(true);
    public final ImBoolean showEffects = new ImBoolean(true);
    public final float[] effectScale = {0.6f};
    public final ImBoolean showEquipment = new ImBoolean(true);
    public final ImBoolean background = new ImBoolean(true);
    public final float[] bgOpacity = {0.25f};

    private final Matrix4f viewProj = new Matrix4f();
    private final Vector4f scratch = new Vector4f();
    private final float[] corner = new float[2];
    private final float[] aabb = new float[4];

    public static NametagsFeature get() {
        return INSTANCE;
    }

    public void render(GuiGraphicsExtractor gg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || gg == null) {
            return;
        }
        if (mc.gui == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) {
            return;
        }
        ClientLevel level = mc.level;
        LocalPlayer self = mc.player;
        if (level == null || self == null) {
            return;
        }
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) {
            return;
        }

        float guiW = gg.guiWidth();
        float guiH = gg.guiHeight();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = camera.position();
        camera.getViewRotationProjectionMatrix(this.viewProj);
        Font font = mc.font;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == self || !entity.isAlive() || !matches(entity)) {
                continue;
            }
            if (!computeAabb(camPos, entity, partialTick, guiW, guiH)) {
                continue;
            }
            float minX = this.aabb[0];
            float minY = this.aabb[1];
            float maxX = this.aabb[2];
            float maxY = this.aabb[3];
            if (!isSane(minX, minY, maxX, maxY, guiW, guiH)) {
                continue;
            }

            float s = this.autoScale.get()
                ? clamp((maxY - minY) / 64.0f, 0.5f, 2.5f)
                : Math.max(0.1f, this.scale[0]);

            float dist = self.distanceTo(entity);
            drawNameBlock(gg, font, entity, (minX + maxX) * 0.5f, minY, s, dist);
            if (this.showEquipment.get() && entity instanceof LivingEntity) {
                drawEquipmentColumn(gg, (LivingEntity) entity, minX, minY, s);
            }
        }
    }

    private void drawNameBlock(GuiGraphicsExtractor gg, Font font, Entity entity,
                               float cx, float topY, float s, float dist) {
        String name = entity.getName().getString();
        if (name == null || name.isEmpty()) {
            return;
        }
        Matrix3x2fStack pose = gg.pose();
        pose.pushMatrix();
        pose.translate(cx, topY);
        pose.scale(s, s);

        int nameW = font.width(name);
        int nameX = -nameW / 2;
        int nameY = -12;

        String distStr = this.showDistance.get() ? Math.round(dist) + "m" : null;
        int distW = distStr != null ? font.width(distStr) : 0;

        String healthStr = null;
        int healthCol = 0xFFFFFFFF;
        if (this.showHealth.get() && entity instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) entity;
            healthStr = fmtHealth((le.getHealth() + le.getAbsorptionAmount()) * 0.5f);
            float max = le.getMaxHealth();
            healthCol = max > 0.0f ? healthColor(clamp(le.getHealth() / max, 0.0f, 1.0f)) : 0xFF66FF66;
        }
        int healthW = healthStr != null ? font.width(healthStr) : 0;

        int leftBound = nameX - (distStr != null ? 4 + distW : 0);
        int rightBound = nameX + nameW + (healthStr != null ? 4 + healthW : 0);

        if (this.background.get()) {
            int a = (int) (clamp(this.bgOpacity[0], 0.0f, 1.0f) * 255.0f + 0.5f);
            gg.fill(leftBound - 2, nameY - 2, rightBound + 2, nameY + 10, a << 24);
        }

        gg.text(font, name, nameX, nameY, 0xFFFFFFFF);
        if (distStr != null) {
            gg.text(font, distStr, nameX - 4 - distW, nameY, 0xFFB0B8C4);
        }
        if (healthStr != null) {
            gg.text(font, healthStr, nameX + nameW + 4, nameY, healthCol);
        }

        if (this.showEffects.get() && entity instanceof LivingEntity) {
            drawEffects(gg, (LivingEntity) entity, nameY);
        }
        pose.popMatrix();
    }

    private void drawEffects(GuiGraphicsExtractor gg, LivingEntity entity, int nameY) {
        java.util.Collection<MobEffectInstance> effects = entity.getActiveEffects();
        if (effects.isEmpty()) {
            return;
        }
        int size = Math.max(1, Math.round(18.0f * clamp(this.effectScale[0], 0.1f, 2.0f)));
        int gap = 1;
        int total = effects.size() * (size + gap) - gap;
        int x = -total / 2;
        int y = nameY - 2 - size;
        for (MobEffectInstance instance : effects) {
            Holder<MobEffect> effect = instance.getEffect();
            Identifier sprite = Hud.getMobEffectSprite(effect);
            if (sprite != null) {
                gg.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size);
            }
            x += size + gap;
        }
    }

    private void drawEquipmentColumn(GuiGraphicsExtractor gg, LivingEntity entity, float leftX, float topY, float s) {
        ItemStack[] stacks = new ItemStack[5];
        int count = 0;
        for (int i = 0; i < ARMOR.length; i++) {
            ItemStack st = entity.getItemBySlot(ARMOR[i]);
            if (st != null && !st.isEmpty()) {
                stacks[count++] = st;
            }
        }
        ItemStack hand = entity.getMainHandItem();
        if (hand != null && !hand.isEmpty()) {
            stacks[count++] = hand;
        }
        if (count == 0) {
            return;
        }
        Matrix3x2fStack pose = gg.pose();
        pose.pushMatrix();
        pose.translate(leftX, topY);
        pose.scale(s, s);
        int icon = 16;
        int rowH = 18;
        int x = -(icon + 4);
        int y = 2;
        for (int i = 0; i < count; i++) {
            gg.item(stacks[i], x, y);
            y += rowH;
        }
        pose.popMatrix();
    }

    private boolean computeAabb(Vec3 camPos, Entity entity, float partialTick, float guiW, float guiH) {
        Vec3 feet = entity.getPosition(partialTick);
        double half = entity.getBbWidth() * 0.5;
        double height = entity.getBbHeight();
        double minWx = feet.x - half;
        double maxWx = feet.x + half;
        double minWz = feet.z - half;
        double maxWz = feet.z + half;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double cx = (i & 1) == 0 ? minWx : maxWx;
            double cy = (i & 2) == 0 ? feet.y : feet.y + height;
            double cz = (i & 4) == 0 ? minWz : maxWz;
            if (!project(camPos, cx, cy, cz, guiW, guiH, this.corner)) {
                return false;
            }
            minX = Math.min(minX, this.corner[0]);
            minY = Math.min(minY, this.corner[1]);
            maxX = Math.max(maxX, this.corner[0]);
            maxY = Math.max(maxY, this.corner[1]);
        }
        this.aabb[0] = minX;
        this.aabb[1] = minY;
        this.aabb[2] = maxX;
        this.aabb[3] = maxY;
        return true;
    }

    private boolean project(Vec3 camPos, double wx, double wy, double wz, float guiW, float guiH, float[] out) {
        this.scratch.set((float) (wx - camPos.x), (float) (wy - camPos.y), (float) (wz - camPos.z), 1.0f);
        this.viewProj.transform(this.scratch);
        float w = this.scratch.w;
        if (w <= 1.0e-4f) {
            return false;
        }
        float ndcX = this.scratch.x / w;
        float ndcY = this.scratch.y / w;
        out[0] = (ndcX * 0.5f + 0.5f) * guiW;
        out[1] = (1.0f - (ndcY * 0.5f + 0.5f)) * guiH;
        return true;
    }

    private boolean matches(Entity entity) {
        if (entity.isInvisible() && !this.targetInvisibles.get()) {
            return false;
        }
        if (entity instanceof Player && AntiBotFeature.get().isBot((Player) entity)) {
            return false;
        }
        return (this.targetPlayers.get() && entity instanceof Player)
            || (this.targetMobs.get() && entity instanceof Mob);
    }

    private boolean isSane(float minX, float minY, float maxX, float maxY, float guiW, float guiH) {
        if (!(Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(maxX) && Float.isFinite(maxY))) {
            return false;
        }
        if (maxX < minX || maxY < minY) {
            return false;
        }
        float lim = 16000.0f;
        return !(minX < -lim || minY < -lim || maxX > guiW + lim || maxY > guiH + lim);
    }

    private static String fmtHealth(float hearts) {
        return String.format(java.util.Locale.ROOT, "%.1f", hearts);
    }

    private static int healthColor(float frac) {
        int r = (int) (Math.min(1.0f, 2.0f * (1.0f - frac)) * 255.0f + 0.5f);
        int g = (int) (Math.min(1.0f, 2.0f * frac) * 255.0f + 0.5f);
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private NametagsFeature() {
    }
}
