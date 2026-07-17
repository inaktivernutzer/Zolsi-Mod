package cc.zolsi.mod.feature.visuals;

import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.ZolsiLog;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class HitmarkerFeature {

    private static final HitmarkerFeature INSTANCE = new HitmarkerFeature();

    public static final String[] STYLES = {"Russian X", "Plus", "Diamond", "Circle", "Starburst"};

    public static final String[] SOUND_FILES = {
        "agpa1", "agpa2", "aimbooster", "amongus_kill", "applepay", "arena_switch",
        "bameware", "bell", "ben", "bonk", "brotato", "bubble",
        "click", "cod", "combobreak", "door", "fatality", "flush",
        "hentai1", "hentai2", "hentai3", "kick", "kill_doof_01", "killcard_1",
        "minecraft_bow_ding", "minecraft_button", "minecraft_egg_throw", "minecraft_hit", "minecraft_xp_gain",
        "money_claim", "mouthsound", "msfrs", "na naxuy", "pop",
        "pubg_pan", "quaver", "regulus", "rust_headshot", "satisfying click",
        "spiral knight", "stony", "tavern_misc1c", "telegram_notification", "trident_pierce",
        "water_drop", "xp_rankdown_02", "zelda"
    };

    public static final String[] SOUND_NAMES = {
        "AGPA 1", "AGPA 2", "AimBooster", "Among Us Kill", "Apple Pay", "Arena Switch",
        "Bameware", "Bell", "Ben", "Bonk", "Brotato", "Bubble",
        "Click", "CoD", "Combo Break", "Door", "Fatality", "Flush",
        "Hentai 1", "Hentai 2", "Hentai 3", "Kick", "Kill Doof", "Killcard",
        "Minecraft Bow Ding", "Minecraft Button", "Minecraft Egg Throw", "Minecraft Hit", "Minecraft XP Gain",
        "Money Claim", "Mouth Sound", "MSFRS", "Na Naxuy", "Pop",
        "PUBG Pan", "Quaver", "Regulus", "Rust Headshot", "Satisfying Click",
        "Spiral Knight", "Stony", "Tavern", "Telegram", "Trident Pierce",
        "Water Drop", "XP Rankdown", "Zelda"
    };

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImInt style = new ImInt(0);
    public final float[] color = {1.0f, 0.22f, 0.22f, 1.0f};
    public final float[] size = {6.0f};
    public final float[] duration = {0.5f};
    public final float[] gap = {3.0f};
    public final float[] thickness = {2.0f};
    public final ImBoolean soundEnabled = new ImBoolean(true);
    public final float[] soundVolume = {0.45f};
    public final ImInt soundIndex = new ImInt(0);

    private final List<HitEntry> active = new ArrayList<>();
    private int prevSwingTime;
    private Clip soundClip;
    private int loadedSoundIndex = -1;
    private boolean loggedSoundError;

    private final Matrix4f scratchM = new Matrix4f();
    private final Vector4f scratchV = new Vector4f();
    private final float[] scratch2 = new float[2];

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    public static HitmarkerFeature get() {
        return INSTANCE;
    }

    public void detect() {
        if (!enabled.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int st = mc.player.swingTime;
        if (st > 0 && prevSwingTime == 0) {
            if (mc.hitResult instanceof EntityHitResult ehr) {
                Entity target = ehr.getEntity();
                if (target != mc.player && target.isAlive()) {
                    registerHit(target);
                }
            }
        }
        prevSwingTime = st;
    }

    private void registerHit(Entity target) {
        active.removeIf(e -> !e.entity.isAlive());
        active.add(new HitEntry(target, System.currentTimeMillis()));
        if (soundEnabled.get()) {
            playSound();
        }
    }

    public void render() {
        if (active.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = camera.position();
        camera.getViewRotationProjectionMatrix(this.scratchM);

        float dispW = ImGui.getIO().getDisplaySizeX();
        float dispH = ImGui.getIO().getDisplaySizeY();
        float now = System.currentTimeMillis();
        float durMs = this.duration[0] * 1000.0f;
        float baseSize = this.size[0];
        float baseGap = this.gap[0];
        float thick = this.thickness[0];
        int curStyle = this.style.get();

        ImDrawList dl = ImGui.getForegroundDrawList();

        Iterator<HitEntry> it = active.iterator();
        while (it.hasNext()) {
            HitEntry e = it.next();
            if (e.entity == null || !e.entity.isAlive()) {
                it.remove();
                continue;
            }
            float age = now - e.time;
            if (age > durMs) {
                it.remove();
                continue;
            }

            Vec3 pos = e.entity.getPosition(partialTick);
            float entityH = e.entity instanceof LivingEntity le ? le.getBbHeight() : 1.8f;
            if (!project(camPos, pos.x, pos.y + entityH * 0.5f, pos.z, dispW, dispH, this.scratch2)) {
                continue;
            }
            float cx = this.scratch2[0];
            float cy = this.scratch2[1];

            float p = age / durMs;
            float scale;
            float alpha;
            if (p < 0.12f) {
                float t = p / 0.12f;
                scale = 0.3f + 0.7f * t;
                alpha = 1.0f;
            } else if (p < 0.35f) {
                scale = 1.0f;
                alpha = 1.0f;
            } else {
                float t = (p - 0.35f) / 0.65f;
                scale = 1.0f + 0.2f * t;
                alpha = 1.0f - t * t;
            }

            float s = baseSize * scale;
            float g = baseGap * scale;
            int col = packColor(this.color[0], this.color[1], this.color[2], this.color[3] * alpha);

            switch (curStyle) {
                case 0 -> drawRussianX(dl, cx, cy, s, g, col, thick);
                case 1 -> drawPlus(dl, cx, cy, s, g, col, thick);
                case 2 -> drawDiamond(dl, cx, cy, s, col, thick);
                case 3 -> drawCircle(dl, cx, cy, s, col, thick, p);
                case 4 -> drawStarburst(dl, cx, cy, s, col, thick);
            }
        }
    }

    private void drawRussianX(ImDrawList dl, float cx, float cy, float s, float g, int col, float t) {
        dl.addLine(cx - g, cy - g - s, cx - g, cy - g, col, t);
        dl.addLine(cx + g, cy - g, cx + g, cy - g - s, col, t);
        dl.addLine(cx - g, cy + g, cx - g, cy + g + s, col, t);
        dl.addLine(cx + g, cy + g + s, cx + g, cy + g, col, t);
    }

    private void drawPlus(ImDrawList dl, float cx, float cy, float s, float g, int col, float t) {
        dl.addLine(cx, cy - g - s, cx, cy - g, col, t);
        dl.addLine(cx + g, cy, cx + g + s, cy, col, t);
        dl.addLine(cx, cy + g, cx, cy + g + s, col, t);
        dl.addLine(cx - g - s, cy, cx - g, cy, col, t);
    }

    private void drawDiamond(ImDrawList dl, float cx, float cy, float s, int col, float t) {
        float h = s * 1.2f;
        dl.addLine(cx, cy - h, cx + h * 0.6f, cy, col, t);
        dl.addLine(cx + h * 0.6f, cy, cx, cy + h, col, t);
        dl.addLine(cx, cy + h, cx - h * 0.6f, cy, col, t);
        dl.addLine(cx - h * 0.6f, cy, cx, cy - h, col, t);
    }

    private void drawCircle(ImDrawList dl, float cx, float cy, float s, int col, float t, float progress) {
        float r = s * 0.5f + s * progress;
        dl.addCircle(cx, cy, Math.max(1.0f, r), col, 24, t);
    }

    private void drawStarburst(ImDrawList dl, float cx, float cy, float s, int col, float t) {
        for (int i = 0; i < 8; i++) {
            float angle = TWO_PI * i / 8.0f;
            float dx = (float) Math.cos(angle) * s;
            float dy = (float) Math.sin(angle) * s;
            dl.addLine(cx, cy, cx + dx, cy + dy, col, t);
        }
    }

    private boolean project(Vec3 camPos, double wx, double wy, double wz, float dispW, float dispH, float[] out) {
        this.scratchV.set((float) (wx - camPos.x), (float) (wy - camPos.y), (float) (wz - camPos.z), 1.0f);
        this.scratchM.transform(this.scratchV);
        float w = this.scratchV.w;
        if (w <= 1.0e-4f) return false;
        float ndcX = this.scratchV.x / w;
        float ndcY = this.scratchV.y / w;
        out[0] = (ndcX * 0.5f + 0.5f) * dispW;
        out[1] = (1.0f - (ndcY * 0.5f + 0.5f)) * dispH;
        return true;
    }

    private void playSound() {
        int idx = this.soundIndex.get();
        if (idx < 0 || idx >= SOUND_FILES.length) return;
        if (idx != loadedSoundIndex) {
            if (soundClip != null) {
                soundClip.close();
                soundClip = null;
            }
            loadedSoundIndex = -1;
            loadSound(idx);
        }
        if (soundClip != null) {
            try {
                soundClip.stop();
                soundClip.setFramePosition(0);
                if (soundClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) soundClip.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = this.soundVolume[0] <= 0.0f ? -80.0f
                        : Math.max(-80.0f, Math.min(6.0f, 20.0f * (float) Math.log10(this.soundVolume[0])));
                    gain.setValue(db);
                }
                soundClip.start();
            } catch (Throwable t) {
                if (!loggedSoundError) {
                    loggedSoundError = true;
                    ZolsiLog.log("hitmarker: sound playback failed", t);
                }
            }
        }
    }

    private void loadSound(int idx) {
        String path = "/assets/zolsi/sounds/" + SOUND_FILES[idx] + ".vsnd_c";
        try (java.io.InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                ZolsiLog.log("hitmarker: sound not found: " + path);
                return;
            }
            byte[] fileBytes = in.readAllBytes();
            if (fileBytes.length <= 88) {
                ZolsiLog.log("hitmarker: sound file too small: " + path);
                return;
            }
            int dataLen = fileBytes.length - 88;
            byte[] pcm = new byte[dataLen];
            System.arraycopy(fileBytes, 88, pcm, 0, dataLen);
            AudioFormat format = new AudioFormat(48000, 16, 1, true, false);
            Clip clip = AudioSystem.getClip();
            clip.open(format, pcm, 0, pcm.length);
            this.soundClip = clip;
            this.loadedSoundIndex = idx;
        } catch (Throwable t) {
            if (!loggedSoundError) {
                loggedSoundError = true;
                ZolsiLog.log("hitmarker: sound load failed: " + path, t);
            }
        }
    }

    private static int packColor(float r, float g, float b, float alpha) {
        int ri = (int) (Math.max(0.0f, Math.min(1.0f, r)) * 255.0f + 0.5f);
        int gi = (int) (Math.max(0.0f, Math.min(1.0f, g)) * 255.0f + 0.5f);
        int bi = (int) (Math.max(0.0f, Math.min(1.0f, b)) * 255.0f + 0.5f);
        int ai = (int) (Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f + 0.5f);
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }

    private static final class HitEntry {
        final Entity entity;
        final float time;
        HitEntry(Entity entity, float time) { this.entity = entity; this.time = time; }
    }

    private HitmarkerFeature() {}
}
