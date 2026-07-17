package cc.zolsi.mod.feature.visuals;

import cc.zolsi.mod.feature.Keybind;
import cc.zolsi.mod.ZolsiLog;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;

import java.io.InputStream;
import java.util.List;

public final class HitmarkerFeature {

    private static final HitmarkerFeature INSTANCE = new HitmarkerFeature();

    public static final String[] STYLES = {
        "Star", "Heart", "Circle", "Snowflake"
    };
    private static final String[] TEXTURE_FILES = {
        "star", "heart", "circle", "snowflake"
    };

    public static final String[] SOUND_FILES = {
        "aimbooster", "ben", "bonk", "brotato", "click", "kick",
        "money claim", "mouthsound", "pop", "quaver", "regulus",
        "satisfying click", "spiral knight", "tavern misc1c", "ting",
        "trident pierce", "water drop", "zelda",
        "[minecraft] bow ding", "[minecraft] button", "[minecraft] egg throw",
        "[minecraft] hitsound", "[minecraft] old hitsound", "[minecraft] XP gain"
    };

    public static final String[] SOUND_NAMES = {
        "AimBooster", "Ben", "Bonk", "Brotato", "Click", "Kick",
        "Money Claim", "Mouth Sound", "Pop", "Quaver", "Regulus",
        "Satisfying Click", "Spiral Knight", "Tavern", "Ting",
        "Trident Pierce", "Water Drop", "Zelda",
        "Minecraft Bow Ding", "Minecraft Button", "Minecraft Egg Throw",
        "Minecraft Hit", "Minecraft Old Hit", "Minecraft XP Gain"
    };

    public static final String[] PHYSICS_MODES = {"Fall", "Fly"};

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImInt style = new ImInt(0);
    public final float[] particleLife = {2.0f};
    public final float[] particleSpeed = {2.0f};
    public final ImInt particleAmount = new ImInt(3);
    public final ImInt physicsMode = new ImInt(0);
    public final ImBoolean soundEnabled = new ImBoolean(true);
    public final float[] soundVolume = {0.45f};
    public final ImInt soundIndex = new ImInt(0);

    private final List<Particle> particles = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> textureCache = new ConcurrentHashMap<>();
    private final Matrix4f viewProj = new Matrix4f();
    private final Vector4f scratch = new Vector4f();
    private final float[] scrPos = new float[2];
    private float displayW;
    private float displayH;

    private byte[] soundData;
    private AudioFormat soundFormat;
    private int loadedSoundIndex = -1;
    private boolean loggedSoundError;

    private boolean prevSoundEnabled;
    private int prevSwingTime;
    private boolean prevAttackDown;

    public static HitmarkerFeature get() {
        return INSTANCE;
    }

    private void detectHits() {
        if (!enabled.get()) return;
        boolean soundNow = soundEnabled.get();
        if (soundNow != prevSoundEnabled) {
            if (soundNow) {
                System.getProperties().put("zolsi.soundFilter", Boolean.TRUE);
            } else {
                System.getProperties().remove("zolsi.soundFilter");
            }
            prevSoundEnabled = soundNow;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int st = mc.player.swingTime;
        boolean attackKey = mc.options.keyAttack.isDown();
        boolean swingEdge = st > 0 && prevSwingTime == 0;
        boolean keyEdge = attackKey && !prevAttackDown;
        prevSwingTime = st;
        prevAttackDown = attackKey;

        Vec3 pos = null;
        if (swingEdge || keyEdge) {
            if (mc.hitResult instanceof EntityHitResult ehr) {
                Entity hit = ehr.getEntity();
                if (hit != mc.player && hit.isAlive()) {
                    pos = ehr.getLocation();
                }
            }
        }
        if (pos == null) {
            LivingEntity lastHurt = mc.player.getLastHurtMob();
            if (lastHurt != null && lastHurt != mc.player && lastHurt.isAlive()) {
                pos = lastHurt.position();
            }
        }
        if (pos != null) {
            spawnParticles(pos);
        }
    }

    private void spawnParticles(Vec3 pos) {
        int amt = Math.max(1, particleAmount.get());
        float spd = particleSpeed[0] * 0.02f;
        float px = (float) pos.x;
        float py = (float) pos.y;
        float pz = (float) pos.z;
        for (int i = 0; i < amt; i++) {
            float mx = (float) (Math.random() - 0.5) * 2.0f * spd;
            float my = (float) (Math.random() - 0.5) * 2.0f * spd;
            float mz = (float) (Math.random() - 0.5) * 2.0f * spd;
            particles.add(new Particle(px, py, pz, mx, my, mz));
        }
        if (soundEnabled.get()) {
            playSound();
        }
    }

    public void render() {
        detectHits();
        if (particles.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) return;

        displayW = ImGui.getIO().getDisplaySizeX();
        displayH = ImGui.getIO().getDisplaySizeY();
        if (displayW <= 0.0f || displayH <= 0.0f) return;

        Vec3 camPos = camera.position();
        camera.getViewRotationProjectionMatrix(viewProj);
        float dt = Math.min(ImGui.getIO().getDeltaTime(), 0.05f);

        ImDrawList dl = ImGui.getForegroundDrawList();
        int texId = getTexture(style.get());

        particles.removeIf(p -> {
            p.update(dt);
            if (p.dead) return true;

            if (!project(camPos, p.x, p.y, p.z, scrPos)) return true;

            double screenZ = Math.abs(p.z - camPos.z);
            if (screenZ < 0.1) return true;

            double distScale = 8.0 / Math.sqrt(Math.max(0.1, screenZ));
            distScale = Math.max(0.4, Math.min(4.0, distScale));
            float size = 12.0f * (float) distScale * p.alpha;
            if (size < 0.5f) return true;

            int col = packColor(1.0f, 1.0f, 1.0f, p.alpha);
            if (texId != 0) {
                dl.addImage(texId, scrPos[0] - size * 0.5f, scrPos[1] - size * 0.5f,
                    scrPos[0] + size * 0.5f, scrPos[1] + size * 0.5f,
                    0.0f, 0.0f, 1.0f, 1.0f, col);
            } else {
                dl.addCircleFilled(scrPos[0], scrPos[1], size * 0.5f, col, 20);
            }
            return false;
        });
    }

    private int getTexture(int styleIdx) {
        if (styleIdx < 0 || styleIdx >= TEXTURE_FILES.length) return 0;
        String name = TEXTURE_FILES[styleIdx];
        Integer cached = textureCache.get(name);
        if (cached != null) return cached;
        int texId = loadTexture("/assets/zolsi/particles/" + name + ".png");
        textureCache.put(name, texId);
        return texId;
    }

    private static int loadTexture(String resourcePath) {
        try (InputStream in = HitmarkerFeature.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                ZolsiLog.log("hitmarker: texture not found: " + resourcePath);
                return 0;
            }
            byte[] fileBytes = in.readAllBytes();
            ByteBuffer buf = BufferUtils.createByteBuffer(fileBytes.length);
            buf.put(fileBytes);
            buf.flip();

            IntBuffer w = BufferUtils.createIntBuffer(1);
            IntBuffer h = BufferUtils.createIntBuffer(1);
            IntBuffer comp = BufferUtils.createIntBuffer(1);
            ByteBuffer image = STBImage.stbi_load_from_memory(buf, w, h, comp, 4);
            if (image == null) {
                ZolsiLog.log("hitmarker: STBImage decode failed: " + resourcePath + " - " + STBImage.stbi_failure_reason());
                return 0;
            }
            int width = w.get(0);
            int height = h.get(0);

            int texId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            STBImage.stbi_image_free(image);

            ZolsiLog.log("hitmarker: loaded texture " + resourcePath + " (" + width + "x" + height + ")");
            return texId;
        } catch (Throwable t) {
            ZolsiLog.log("hitmarker: texture load error: " + resourcePath, t);
            return 0;
        }
    }

    public void freeTextures() {
        for (int id : textureCache.values()) {
            if (id != 0) GL11.glDeleteTextures(id);
        }
        textureCache.clear();
    }

    private boolean project(Vec3 camPos, double wx, double wy, double wz, float[] out) {
        scratch.set((float) (wx - camPos.x), (float) (wy - camPos.y), (float) (wz - camPos.z), 1.0f);
        viewProj.transform(scratch);
        float w = scratch.w;
        if (w <= 1.0e-4f) return false;
        float ndcX = scratch.x / w;
        float ndcY = scratch.y / w;
        out[0] = (ndcX * 0.5f + 0.5f) * displayW;
        out[1] = (1.0f - (ndcY * 0.5f + 0.5f)) * displayH;
        return true;
    }

    private void playSound() {
        int idx = soundIndex.get();
        if (idx < 0 || idx >= SOUND_FILES.length) return;
        if (idx != loadedSoundIndex) {
            loadedSoundIndex = -1;
            soundData = null;
            soundFormat = null;
            loggedSoundError = false;
            loadSound(idx);
        }
        if (soundData == null) return;
        byte[] data = soundData;
        AudioFormat fmt = soundFormat;
        float vol = soundVolume[0];
        new Thread(() -> {
            SourceDataLine line = null;
            try {
                line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = vol <= 0.0f ? -80.0f
                        : Math.max(-80.0f, Math.min(6.0f, 20.0f * (float) Math.log10(vol)));
                    gain.setValue(db);
                }
                line.start();
                line.write(data, 0, data.length);
                line.drain();
            } catch (Throwable t) {
                if (!loggedSoundError) {
                    loggedSoundError = true;
                    ZolsiLog.log("hitmarker: sound playback failed", t);
                }
            } finally {
                if (line != null) line.close();
            }
        }, "zolsi-hitsound").start();
    }

    private void loadSound(int idx) {
        loggedSoundError = false;
        String path = "/assets/zolsi/sounds/" + SOUND_FILES[idx] + ".wav";
        try (InputStream in = HitmarkerFeature.class.getResourceAsStream(path)) {
            if (in == null) {
                ZolsiLog.log("hitmarker: sound not found: " + path);
                return;
            }
            byte[] raw = in.readAllBytes();
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(raw))) {
                soundFormat = ais.getFormat();
                soundData = ais.readAllBytes();
                loadedSoundIndex = idx;
                ZolsiLog.log("hitmarker: loaded sound " + SOUND_FILES[idx] + " (" + raw.length + " bytes, " + soundFormat.getEncoding() + ")");
            }
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

    private final class Particle {
        double x, y, z;
        double mx, my, mz;
        float alpha = 1.0f;
        float elapsed;
        boolean dead;

        Particle(double x, double y, double z, double mx, double my, double mz) {
            this.x = x; this.y = y; this.z = z;
            this.mx = mx; this.my = my; this.mz = mz;
        }

        void update(float dt) {
            elapsed += dt;
            float life = particleLife[0];
            if (elapsed >= life) { dead = true; return; }
            float p = elapsed / life;
            alpha = 1.0f - p * p;

            if (physicsMode.get() == 0) {
                my -= 0.035 * dt * 20.0;
            }

            double drag = Math.pow(0.99, dt * 20.0);
            mx *= drag; my *= drag; mz *= drag;

            double step = dt * 20.0;
            double nx = x + mx * step;
            double ny = y + my * step;
            double nz = z + mz * step;

            if (isSolid(nx, ny - 0.05, nz)) {
                my = -my * 0.5;
                mx *= 0.8;
                mz *= 0.8;
                ny = y + my * step;
            }

            x = nx; y = ny; z = nz;
        }

        private boolean isSolid(double px, double py, double pz) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;
            BlockPos bp = BlockPos.containing(px, py, pz);
            BlockState bs = mc.level.getBlockState(bp);
            return !bs.isAir() && !bs.is(Blocks.WATER) && !bs.is(Blocks.LAVA);
        }
    }

    private HitmarkerFeature() {}
}
