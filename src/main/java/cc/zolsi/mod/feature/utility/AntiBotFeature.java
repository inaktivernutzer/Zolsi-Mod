package cc.zolsi.mod.feature.utility;
import cc.zolsi.mod.feature.Keybind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import imgui.type.ImBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class AntiBotFeature {

    private static final AntiBotFeature INSTANCE = new AntiBotFeature();

    private static final long STILL_MS = 2500L;
    private static final double MOVE_EPS_SQ = 0.0004;
    private static final java.util.regex.Pattern VALID_NAME =
        java.util.regex.Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public final ImBoolean enabled = new ImBoolean(false);
    public final Keybind bind = new Keybind();
    public final ImBoolean checkMovement = new ImBoolean(true);
    public final ImBoolean checkPing = new ImBoolean(true);
    public final ImBoolean checkName = new ImBoolean(false);
    public final ImBoolean checkUserId = new ImBoolean(false);

    private final Map<UUID, long[]> moveState = new HashMap<>();
    private final Map<UUID, double[]> movePos = new HashMap<>();
    private long lastSweep;

    public static AntiBotFeature get() {
        return INSTANCE;
    }

    public boolean isBot(Player player) {
        if (!this.enabled.get() || player == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || player == mc.player) {
            return false;
        }
        long now = System.currentTimeMillis();
        sweep(now);

        if (this.checkPing.get() && isPingBot(mc, player)) {
            return true;
        }
        if (this.checkName.get() && isNameBot(player)) {
            return true;
        }
        if (this.checkUserId.get() && isUserIdBot(player)) {
            return true;
        }
        if (this.checkMovement.get() && isMoveBot(mc, player, now)) {
            return true;
        }
        return false;
    }

    private boolean isPingBot(Minecraft mc, Player player) {
        ClientPacketListener conn = mc.getConnection();
        PlayerInfo info = conn != null ? conn.getPlayerInfo(player.getUUID()) : null;
        return info == null;
    }

    private boolean isNameBot(Player player) {
        String name = player.getGameProfile().name();
        return name == null || !VALID_NAME.matcher(name).matches();
    }

    private boolean isUserIdBot(Player player) {
        UUID id = player.getUUID();
        return id == null || id.version() != 4;
    }

    private boolean isMoveBot(Minecraft mc, Player player, long now) {
        UUID id = player.getUUID();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 pos = player.getPosition(partialTick);
        double[] last = this.movePos.get(id);
        long[] stamp = this.moveState.get(id);
        if (last == null || stamp == null) {
            this.movePos.put(id, new double[]{pos.x, pos.y, pos.z});
            this.moveState.put(id, new long[]{now, now});
            return false;
        }
        double dx = pos.x - last[0];
        double dy = pos.y - last[1];
        double dz = pos.z - last[2];
        if (dx * dx + dy * dy + dz * dz > MOVE_EPS_SQ) {
            last[0] = pos.x;
            last[1] = pos.y;
            last[2] = pos.z;
            stamp[0] = now;
        }
        stamp[1] = now;
        return now - stamp[0] > STILL_MS;
    }

    private void sweep(long now) {
        if (now - this.lastSweep < 5000L) {
            return;
        }
        this.lastSweep = now;
        this.moveState.entrySet().removeIf(e -> now - e.getValue()[1] > 10000L);
        this.movePos.keySet().retainAll(this.moveState.keySet());
    }

    private AntiBotFeature() {
    }
}
