package cc.zolsi.mod.feature.visuals;
import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.feature.utility.AntiBotFeature;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class EspFeature {

    private static final EspFeature INSTANCE = new EspFeature();

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImBoolean targetPlayers = new ImBoolean(false);
    public final ImBoolean targetMobs = new ImBoolean(true);
    public final ImBoolean targetFriendly = new ImBoolean(false);
    public final float[] color = {0.561f, 0.722f, 0.871f, 1.0f};
    public final float[] outlineOpacity = {1.0f};
    public final float[] thickness = {1.0f};
    public final float[] cornerRadius = {4.0f};
    public final ImBoolean boxOutline = new ImBoolean(true);
    public final ImBoolean autoScale = new ImBoolean(true);
    public final float[] boxSize = {30.0f};
    public final ImBoolean names = new ImBoolean(false);
    public final ImBoolean namesShadow = new ImBoolean(true);
    public final float[] namesSize = {11.0f};
    public final ImBoolean namesBackground = new ImBoolean(false);
    public final float[] namesBgColor = {0.0f, 0.0f, 0.0f, 1.0f};
    public final float[] namesBgOpacity = {0.5f};
    public final ImBoolean healthbar = new ImBoolean(false);
    public final ImBoolean healthbarGradient = new ImBoolean(false);
    public final float[] healthbarOpacity = {1.0f};
    public final float[] healthbarThickness = {3.0f};
    public final ImBoolean healthbarOutline = new ImBoolean(true);

    private final Matrix4f viewProj = new Matrix4f();
    private final Vector4f scratch = new Vector4f();
    private final ImVec2 textSize = new ImVec2();
    private final float[] a = new float[2];
    private final float[] b = new float[2];
    private float displayW;
    private float displayH;

    public static EspFeature get() {
        return INSTANCE;
    }

    public void render(float displayWidth, float displayHeight, ImFont nameFont, float nameBake) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (mc.gui != null && mc.gui.screen() != null) {
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

        this.displayW = displayWidth;
        this.displayH = displayHeight;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = camera.position();
        camera.getViewRotationProjectionMatrix(this.viewProj);

        float outlineAlpha = this.outlineOpacity[0];
        int lineCol = packColor(this.color[0], this.color[1], this.color[2], outlineAlpha);
        int outlineBlack = packColor(0.0f, 0.0f, 0.0f, outlineAlpha);
        int nameCol = packColor(this.color[0], this.color[1], this.color[2], 1.0f);
        float radius = this.cornerRadius[0];
        float boxThickness = this.thickness[0];
        ImDrawList dl = ImGui.getBackgroundDrawList();

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == self || !entity.isAlive() || !matches(entity)) {
                continue;
            }
            Vec3 feet = entity.getPosition(partialTick);
            if (!computeRect(camPos, feet.x, feet.y, feet.z, entity.getBbWidth(), entity.getBbHeight())) {
                continue;
            }
            float minX = this.a[0];
            float minY = this.a[1];
            float maxX = this.b[0];
            float maxY = this.b[1];
            if (!isSaneRect(minX, minY, maxX, maxY)) {
                continue;
            }

            if (this.boxOutline.get()) {
                dl.addRect(minX, minY, maxX, maxY, outlineBlack, radius, 0, boxThickness + 2.0f);
            }
            dl.addRect(minX, minY, maxX, maxY, lineCol, radius, 0, boxThickness);

            if (this.healthbar.get() && entity instanceof LivingEntity) {
                drawHealthbar(dl, (LivingEntity) entity, minX, minY, maxY, radius);
            }
            if (this.names.get()) {
                drawName(dl, entity, minX, maxX, minY, nameFont, nameBake, nameCol, radius);
            }
        }
    }

    private boolean computeRect(Vec3 camPos, double fx, double fy, double fz, float width, float height) {
        double half = width * 0.5;
        if (this.autoScale.get()) {
            double minWx = fx - half;
            double maxWx = fx + half;
            double minWz = fz - half;
            double maxWz = fz + half;
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                double cx = (i & 1) == 0 ? minWx : maxWx;
                double cy = (i & 2) == 0 ? fy : fy + height;
                double cz = (i & 4) == 0 ? minWz : maxWz;
                if (!project(camPos, cx, cy, cz, this.a)) {
                    return false;
                }
                minX = Math.min(minX, this.a[0]);
                minY = Math.min(minY, this.a[1]);
                maxX = Math.max(maxX, this.a[0]);
                maxY = Math.max(maxY, this.a[1]);
            }
            this.a[0] = minX;
            this.a[1] = minY;
            this.b[0] = maxX;
            this.b[1] = maxY;
            return true;
        }

        if (!project(camPos, fx, fy + height * 0.5, fz, this.a)) {
            return false;
        }
        float halfH = this.boxSize[0] * 0.5f;
        float halfW = this.boxSize[0] * 0.3f;
        float cx = this.a[0];
        float cy = this.a[1];
        this.a[0] = cx - halfW;
        this.a[1] = cy - halfH;
        this.b[0] = cx + halfW;
        this.b[1] = cy + halfH;
        return true;
    }

    private void drawHealthbar(ImDrawList dl, LivingEntity entity, float minX, float minY, float maxY, float radius) {
        float max = entity.getMaxHealth();
        if (max <= 0.0f) {
            return;
        }
        float frac = entity.getHealth() / max;
        frac = Math.max(0.0f, Math.min(1.0f, frac));
        float alpha = this.healthbarOpacity[0];

        float barW = this.healthbarThickness[0];
        float x2 = minX - 2.0f;
        float x1 = x2 - barW;
        float r = Math.min(radius, barW * 0.5f);
        if (this.healthbarOutline.get()) {
            dl.addRectFilled(x1 - 1.0f, minY - 1.0f, x2 + 1.0f, maxY + 1.0f, packColor(0.0f, 0.0f, 0.0f, alpha), r);
        }

        float height = maxY - minY;
        float fillTop = maxY - height * frac;
        if (this.healthbarGradient.get()) {
            int top = healthColor(frac, alpha);
            int bottom = packColor(1.0f, 0.0f, 0.0f, alpha);
            dl.addRectFilledMultiColor(x1, fillTop, x2, maxY, top, top, bottom, bottom);
        } else {
            dl.addRectFilled(x1, fillTop, x2, maxY, healthColor(frac, alpha), r);
        }
    }

    private void drawName(ImDrawList dl, Entity entity, float minX, float maxX, float minY,
                          ImFont font, float bake, int color, float radius) {
        if (font == null) {
            return;
        }
        String text = entity.getName().getString();
        if (text == null || text.isEmpty()) {
            return;
        }
        int fontSizePx = Math.max(1, Math.round(this.namesSize[0]));
        font.calcTextSizeA(this.textSize, fontSizePx, Float.MAX_VALUE, 0.0f, text);
        float w = this.textSize.x;
        float h = this.textSize.y;
        if (!(Float.isFinite(w) && Float.isFinite(h)) || w <= 0.0f) {
            return;
        }
        float tx = (minX + maxX) * 0.5f - w * 0.5f;
        float ty = minY - h - 3.0f;

        if (this.namesBackground.get()) {
            float pad = 2.0f;
            int bg = packColor(this.namesBgColor[0], this.namesBgColor[1], this.namesBgColor[2], this.namesBgOpacity[0]);
            float bgR = Math.min(radius, (h + pad * 2.0f) * 0.5f);
            dl.addRectFilled(tx - pad, ty - pad, tx + w + pad, ty + h + pad, bg, bgR);
        }
        if (this.namesShadow.get()) {
            int black = packColor(0.0f, 0.0f, 0.0f, 1.0f);
            dl.addText(font, fontSizePx, tx + 1.0f, ty + 1.0f, black, text);
        }
        dl.addText(font, fontSizePx, tx, ty, color, text);
    }

    private boolean matches(Entity entity) {
        if (entity instanceof Player && AntiBotFeature.get().isBot((Player) entity)) {
            return false;
        }
        return (this.targetPlayers.get() && entity instanceof Player)
            || (this.targetMobs.get() && entity instanceof Monster)
            || (this.targetFriendly.get() && entity instanceof Animal);
    }

    private boolean isSaneRect(float minX, float minY, float maxX, float maxY) {
        if (!(Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(maxX) && Float.isFinite(maxY))) {
            return false;
        }
        if (maxX < minX || maxY < minY) {
            return false;
        }
        float lim = 16000.0f;
        if (minX < -lim || minY < -lim || maxX > this.displayW + lim || maxY > this.displayH + lim) {
            return false;
        }
        return true;
    }

    private boolean project(Vec3 camPos, double wx, double wy, double wz, float[] out) {
        this.scratch.set((float) (wx - camPos.x), (float) (wy - camPos.y), (float) (wz - camPos.z), 1.0f);
        this.viewProj.transform(this.scratch);
        float w = this.scratch.w;
        if (w <= 1.0e-4f) {
            return false;
        }
        float ndcX = this.scratch.x / w;
        float ndcY = this.scratch.y / w;
        out[0] = (ndcX * 0.5f + 0.5f) * this.displayW;
        out[1] = (1.0f - (ndcY * 0.5f + 0.5f)) * this.displayH;
        return true;
    }

    private static int healthColor(float frac, float alpha) {
        float r = Math.min(1.0f, 2.0f * (1.0f - frac));
        float g = Math.min(1.0f, 2.0f * frac);
        return packColor(r, g, 0.0f, alpha);
    }

    private static int packColor(float r, float g, float b, float alpha) {
        int ri = channel(r);
        int gi = channel(g);
        int bi = channel(b);
        int ai = channel(alpha);
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }

    private static int channel(float value) {
        return (int) (Math.max(0.0f, Math.min(1.0f, value)) * 255.0f + 0.5f);
    }

    private EspFeature() {
    }
}
