package cc.zolsi.boot;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ZolsiHook {

    public static volatile String lastError;

    private static final String[] SIGNATURES = {
        "net.fabricmc.loader.impl.launch.knot.KnotClient",
        "net.minecraft.client.main.Main",
        "cpw.mods.modlauncher.Launcher",
        "--gameDir"
    };

    public static final class Target {
        public final String id;
        public final String name;

        Target(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static Target findTarget() {
        long own = ProcessHandle.current().pid();
        Target found = null;

        List<ProcessHandle> candidates = new ArrayList<ProcessHandle>();
        ProcessHandle.allProcesses().forEach(ph -> {
            if (ph.pid() == own) {
                return;
            }
            String command = ph.info().command().orElse("");
            if (command.endsWith("java.exe") || command.endsWith("javaw.exe")) {
                candidates.add(ph);
            }
        });

        for (ProcessHandle ph : candidates) {
            String pid = String.valueOf(ph.pid());
            VirtualMachine vm = null;
            try {
                vm = VirtualMachine.attach(pid);
                String command = String.valueOf(vm.getSystemProperties().get("sun.java.command"));
                boolean match = matches(command);
                if (match && found == null) {
                    found = new Target(pid, label(command));
                }
            } catch (Throwable ignored) {
            } finally {
                if (vm != null) {
                    try {
                        vm.detach();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        return found;
    }

    public static boolean attachAndLoad(Target target, String selfJar) {
        lastError = null;
        sweepStaleTemp();
        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(target.id);
            vm.loadAgent(selfJar, selfJar);
            return true;
        } catch (Throwable t) {
            lastError = String.valueOf(t.getMessage() == null ? t.toString() : t.getMessage());
            return false;
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void sweepStaleTemp() {
        try {
            File dir = new File(System.getProperty("java.io.tmpdir"));
            File[] files = dir.listFiles((d, name) ->
                name.startsWith("zolsi-") && name.endsWith(".jar"));
            if (files == null) {
                return;
            }
            for (File f : files) {
                f.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean matches(String command) {
        if (command == null || command.isEmpty() || command.equals("null")) {
            return false;
        }
        for (String signature : SIGNATURES) {
            if (command.contains(signature)) {
                return true;
            }
        }
        return false;
    }

    private static String label(String command) {
        String head = head(command);
        int lastDot = head.lastIndexOf('.');
        return lastDot > 0 ? head.substring(lastDot + 1) : head;
    }

    private static String head(String command) {
        int space = command.indexOf(' ');
        return space > 0 ? command.substring(0, space) : command;
    }

    private ZolsiHook() {
    }
}
