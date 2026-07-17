package cc.zolsi.mod.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class LocalConfig {

    private static final LocalConfig INSTANCE = new LocalConfig();

    private static final int MAX_CONFIGS = 5;

    private final File configDir;

    private List<String> names = new ArrayList<>();
    private String status = "";
    private boolean busy;
    private String pendingApply;

    private LocalConfig() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            appData = System.getProperty("user.home") + "\\.zolsi.cc";
        }
        configDir = new File(appData, "zolsi.cc\\configs");
        configDir.mkdirs();
    }

    public static LocalConfig get() {
        return INSTANCE;
    }

    public boolean isConfigured() {
        return true;
    }

    public List<String> getNames() {
        return names;
    }

    public String getStatus() {
        return status;
    }

    public boolean isBusy() {
        return busy;
    }

    public String consumePendingApply() {
        String v = pendingApply;
        pendingApply = null;
        return v;
    }

    public void refreshNow() {
        busy = true;
        try {
            List<String> list = new ArrayList<>();
            File[] files = configDir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    list.add(name.substring(0, name.length() - 5));
                }
            }
            names = list;
            status = list.size() + " config(s)";
        } catch (Throwable t) {
            status = "list failed";
        } finally {
            busy = false;
        }
    }

    public void saveNow(String name, String json) {
        if (name == null || name.isBlank()) {
            status = "name required";
            return;
        }
        busy = true;
        try {
            if (names.size() >= MAX_CONFIGS && !names.contains(name)) {
                status = "limit reached (" + MAX_CONFIGS + " max)";
                return;
            }
            File file = new File(configDir, name + ".json");
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
            refreshNow();
            status = "saved '" + name + "'";
        } catch (Throwable t) {
            status = "save failed";
        } finally {
            busy = false;
        }
    }

    public void loadNow(String name) {
        if (name == null || name.isBlank()) {
            status = "name required";
            return;
        }
        busy = true;
        try {
            File file = new File(configDir, name + ".json");
            if (!file.isFile()) {
                status = "'" + name + "' not found";
                return;
            }
            String data = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            pendingApply = data;
            status = "loaded '" + name + "'";
        } catch (Throwable t) {
            status = "'" + name + "' unreadable";
        } finally {
            busy = false;
        }
    }

    public void deleteNow(String name) {
        if (name == null || name.isBlank()) {
            status = "name required";
            return;
        }
        busy = true;
        try {
            File file = new File(configDir, name + ".json");
            if (file.delete()) {
                refreshNow();
                status = "deleted '" + name + "'";
            } else {
                status = "delete failed";
            }
        } catch (Throwable t) {
            status = "delete failed";
        } finally {
            busy = false;
        }
    }
}
