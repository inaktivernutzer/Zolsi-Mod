package cc.zolsi.mod.feature;

import cc.zolsi.mod.feature.combat.AimAssistFeature;
import cc.zolsi.mod.feature.combat.CritAssistFeature;
import cc.zolsi.mod.feature.combat.TriggerbotFeature;
import cc.zolsi.mod.feature.utility.AntiBotFeature;
import cc.zolsi.mod.feature.utility.AutoJumpResetFeature;
import cc.zolsi.mod.feature.utility.NoJumpDelayFeature;
import cc.zolsi.mod.feature.utility.SprintFeature;
import cc.zolsi.mod.feature.visuals.ArrayListFeature;
import cc.zolsi.mod.feature.visuals.EspFeature;
import cc.zolsi.mod.feature.visuals.HitmarkerFeature;
import cc.zolsi.mod.feature.visuals.NametagsFeature;
import imgui.type.ImBoolean;
import java.util.ArrayList;
import java.util.List;

public final class Modules {

    public static final int COMBAT = 0;
    public static final int UTILITY = 1;
    public static final int VISUALS = 2;

    public static final class Entry {
        public final String name;
        public final int category;
        public final ImBoolean enabled;

        Entry(String name, int category, ImBoolean enabled) {
            this.name = name;
            this.category = category;
            this.enabled = enabled;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        register("Aim Assist", COMBAT, AimAssistFeature.get().enabled);
        register("Crit Assist", COMBAT, CritAssistFeature.get().enabled);
        register("Triggerbot", COMBAT, TriggerbotFeature.get().enabled);
        register("AntiBot", UTILITY, AntiBotFeature.get().enabled);
        register("AutoJumpReset", UTILITY, AutoJumpResetFeature.get().enabled);
        register("NoJumpDelay", UTILITY, NoJumpDelayFeature.get().enabled);
        register("Sprint", UTILITY, SprintFeature.get().enabled);
        register("2D ESP", VISUALS, EspFeature.get().enabled);
        register("Nametags", VISUALS, NametagsFeature.get().enabled);
        register("Hitmarkers", VISUALS, HitmarkerFeature.get().enabled);
    }

    public static void register(String name, int category, ImBoolean enabled) {
        ENTRIES.add(new Entry(name, category, enabled));
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    private Modules() {
    }
}
