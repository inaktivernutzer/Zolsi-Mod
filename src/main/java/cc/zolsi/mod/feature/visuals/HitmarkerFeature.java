package cc.zolsi.mod.feature.visuals;

import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.ZolsiLog;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.type.ImBoolean;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public final class HitmarkerFeature {

    private static final HitmarkerFeature INSTANCE = new HitmarkerFeature();

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final float[] color = {1.0f, 0.22f, 0.22f, 1.0f};
    public final float[] size = {5.0f};
    public final float[] duration = {0.55f};
    public final float[] gap = {2.5f};
    public final float[] thickness = {2.0f};
    public final ImBoolean soundEnabled = new ImBoolean(true);
    public final float[] soundVolume = {0.45f};

    private final List<HitEntry> active = new ArrayList<>();
    private int prevSwingTime;
    private boolean soundInitialized;
    private Clip soundClip;
    private boolean loggedSoundError;

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
                    registerHit();
                }
            }
        }
        prevSwingTime = st;
    }

    private void registerHit() {
        active.add(new HitEntry(System.currentTimeMillis()));
        if (soundEnabled.get()) {
            playSound();
        }
    }

    public void render() {
        if (active.isEmpty()) return;
        float now = System.currentTimeMillis();
        float durMs = this.duration[0] * 1000.0f;
        float dispW = ImGui.getIO().getDisplaySizeX();
        float dispH = ImGui.getIO().getDisplaySizeY();
        float cx = dispW * 0.5f;
        float cy = dispH * 0.5f;
        float baseSize = this.size[0];
        float baseGap = this.gap[0];
        float thick = this.thickness[0];

        ImDrawList dl = ImGui.getForegroundDrawList();

        Iterator<HitEntry> it = active.iterator();
        while (it.hasNext()) {
            HitEntry e = it.next();
            float age = now - e.time;
            if (age > durMs) {
                it.remove();
                continue;
            }
            float p = age / durMs;

            float scale;
            float alpha;
            if (p < 0.12f) {
                float t = p / 0.12f;
                scale = 0.4f + 0.6f * t;
                alpha = 1.0f;
            } else if (p < 0.40f) {
                scale = 1.0f;
                alpha = 1.0f;
            } else {
                float t = (p - 0.40f) / 0.60f;
                scale = 1.0f + 0.15f * t;
                alpha = 1.0f - t * t;
            }

            float s = baseSize * scale;
            float g = baseGap * scale;
            int col = packColor(this.color[0], this.color[1], this.color[2], this.color[3] * alpha);

            dl.addLine(cx - g, cy - g - s, cx - g, cy - g, col, thick);
            dl.addLine(cx + g, cy - g, cx + g, cy - g - s, col, thick);
            dl.addLine(cx - g, cy + g, cx - g, cy + g + s, col, thick);
            dl.addLine(cx + g, cy + g + s, cx + g, cy + g, col, thick);
        }
    }

    private void playSound() {
        if (!soundInitialized) {
            initSound();
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

    private void initSound() {
        soundInitialized = true;
        try {
            int sampleRate = 48000;
            int durationMs = 70;
            int numSamples = sampleRate * durationMs / 1000;
            byte[] buffer = new byte[numSamples * 2];

            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / sampleRate;
                double envelope = Math.exp(-t * 40.0);
                double sample = Math.sin(2.0 * Math.PI * 1200.0 * t) * 0.45
                    + Math.sin(2.0 * Math.PI * 2000.0 * t) * 0.30
                    + Math.sin(2.0 * Math.PI * 2800.0 * t) * 0.15
                    + Math.sin(2.0 * Math.PI * 3600.0 * t) * 0.10;
                if (t < 0.003) {
                    sample *= t / 0.003;
                }
                sample *= envelope * 0.7;
                short val = (short) (sample * Short.MAX_VALUE);
                buffer[i * 2] = (byte) (val & 0xFF);
                buffer[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
            }

            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            soundClip = AudioSystem.getClip();
            soundClip.open(format, buffer, 0, buffer.length);
        } catch (Throwable t) {
            loggedSoundError = true;
            ZolsiLog.log("hitmarker: sound init failed", t);
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
        final float time;
        HitEntry(float time) { this.time = time; }
    }

    private HitmarkerFeature() {}
}
