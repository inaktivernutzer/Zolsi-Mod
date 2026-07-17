package cc.zolsi.mod.inject;

import java.util.function.Predicate;
import net.minecraft.client.resources.sounds.SoundInstance;

public final class SoundFilterHook implements Predicate<Object> {

    public static final SoundFilterHook INSTANCE = new SoundFilterHook();

    public volatile boolean enabled;

    @Override
    public boolean test(Object obj) {
        if (!enabled) return false;
        if (!(obj instanceof SoundInstance si)) return false;
        String path = si.getIdentifier().getPath();
        return path.startsWith("entity.") && path.contains(".hurt");
    }

    private SoundFilterHook() {
    }
}
