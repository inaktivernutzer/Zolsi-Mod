package cc.zolsi.mod.inject;

import cc.zolsi.mod.ZolsiLog;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZolsiAgent {

    private static volatile boolean installed;
    private static volatile String selfJarPath;
    private static volatile boolean internalsOpened;
    private static ClassLoader gameClassLoader;
    private static ZolsiClassLoader imguiHost;
    private static ZolsiClassLoader currentLoader;
    private static Instrumentation instrumentation;
    private static final List<ClassFileTransformer> transformers = new ArrayList<ClassFileTransformer>();
    private static final List<Object> retainedNatives = new ArrayList<Object>();

    public static void agentmain(String args, Instrumentation inst) throws Exception {
        bootstrap(args, inst);
    }

    public static void premain(String args, Instrumentation inst) throws Exception {
        bootstrap(args, inst);
    }

    private static synchronized void bootstrap(String rawArgs, Instrumentation inst) throws Exception {
        instrumentation = inst;
        if (rawArgs == null) {
            ZolsiLog.log("agent bootstrap aborted: no args");
            return;
        }
        selfJarPath = rawArgs;
        if (installed) {
            rehook(rawArgs);
            return;
        }
        installed = true;

        ZolsiLog.log("agent bootstrap start; selfPath=" + rawArgs);

        Class<?> minecraft = findMinecraft(inst);
        if (minecraft == null) {
            ZolsiLog.log("Minecraft class was NOT loaded; aborting");
            installed = false;
            return;
        }
        gameClassLoader = minecraft.getClassLoader();
        ZolsiLog.log("Minecraft loaded by " + gameClassLoader);

        Map<String, byte[]> resources = readSelf(rawArgs);
        ZolsiLog.log("classpath loaded in-memory: " + resources.size() + " entries");

        currentLoader = makeRenderLoader(resources);
        storeHook(currentLoader);

        installTransformers();
    }

    private static ZolsiClassLoader makeRenderLoader(Map<String, byte[]> resources) {
        if (imguiHost != null) {
            ZolsiLog.log("render loader: reusing live imgui host (native not reloaded)");
            return new ZolsiClassLoader(resources, imguiHost, true);
        }
        ZolsiClassLoader loader = new ZolsiClassLoader(resources, gameClassLoader, false);
        imguiHost = loader;
        ZolsiLog.log("render loader: fresh imgui load, this loader now hosts the native");
        return loader;
    }

    private static void installTransformers() {
        addTracked(new RenderHookTransformer());
        addTracked(new FreelookTransformer());
        addTracked(new TickHookTransformer());
        addTracked(new HudHookTransformer());
        addTracked(new NameTagSuppressTransformer());

        retransformTarget(RenderHookTransformer.TARGET, "GlSurface");
        retransformTarget(TickHookTransformer.TARGET, "Minecraft");
        retransformTarget(FreelookTransformer.TARGET, "Camera");
        retransformTarget(HudHookTransformer.TARGET, "Hud");
        retransformTarget(NameTagSuppressTransformer.TARGET, "EntityRenderer");
    }

    private static void addTracked(ClassFileTransformer transformer) {
        instrumentation.addTransformer(transformer, true);
        transformers.add(transformer);
    }

    private static void retransformTarget(String target, String label) {
        try {
            Class<?> loaded = findLoaded(instrumentation, target.replace('/', '.'));
            if (loaded != null) {
                instrumentation.retransformClasses(loaded);
                ZolsiLog.log("retransformed " + label + " (" + loaded.getName() + ")");
            } else {
                ZolsiLog.log(label + " not loaded yet; transformer will patch it on load");
            }
        } catch (Throwable t) {
            ZolsiLog.log("retransform " + label + " failed", t);
        }
    }

    public static synchronized void teardown() {
        removeAllTransformers();
        revertTransforms();
        unloadImguiNative();
        closeAppendedJar();
        if (currentLoader != null) {
            try {
                currentLoader.clearResources();
                ZolsiLog.log("agent teardown: in-memory jar/imgui bytes released");
            } catch (Throwable t) {
                ZolsiLog.log("agent teardown: clearResources failed", t);
            }
        }
    }

    private static void removeAllTransformers() {
        if (instrumentation == null || transformers.isEmpty()) {
            return;
        }
        int removed = 0;
        for (ClassFileTransformer transformer : transformers) {
            try {
                if (instrumentation.removeTransformer(transformer)) {
                    removed++;
                }
            } catch (Throwable t) {
                ZolsiLog.log("agent teardown: removeTransformer failed", t);
            }
        }
        transformers.clear();
        ZolsiLog.log("agent teardown: " + removed + " transformers removed from instrumentation");
    }

    private static void revertTransforms() {
        if (instrumentation == null) {
            return;
        }
        String[] targets = {
            RenderHookTransformer.TARGET,
            TickHookTransformer.TARGET,
            FreelookTransformer.TARGET,
            HudHookTransformer.TARGET,
            NameTagSuppressTransformer.TARGET
        };
        List<Class<?>> patched = new ArrayList<Class<?>>();
        for (String target : targets) {
            Class<?> loaded = findLoaded(instrumentation, target.replace('/', '.'));
            if (loaded != null) {
                patched.add(loaded);
            }
        }
        if (patched.isEmpty()) {
            return;
        }
        try {
            instrumentation.retransformClasses(patched.toArray(new Class<?>[0]));
            ZolsiLog.log("agent teardown: reverted " + patched.size() + " patched classes to original bytecode");
        } catch (Throwable t) {
            ZolsiLog.log("agent teardown: bytecode revert failed", t);
        }
    }

    private static boolean unloadImguiNative() {
        ZolsiClassLoader host = imguiHost;
        if (host == null) {
            return false;
        }
        try {
            if (!openLoaderInternals()) {
                ZolsiLog.log("imgui unload: loader internals not open; keeping host, dll stays locked");
                return false;
            }
            Object nativeLibraries = readField(host, "libraries");
            if (nativeLibraries == null) {
                return false;
            }
            Map<?, ?> libs = asMap(readField(nativeLibraries, "libraries"));
            if (libs == null || libs.isEmpty()) {
                ZolsiLog.log("imgui unload: no native libraries registered on host loader");
                return false;
            }
            List<Object> keysToRemove = new ArrayList<Object>();
            int closed = 0;
            for (Map.Entry<?, ?> entry : new ArrayList<Map.Entry<?, ?>>(libs.entrySet())) {
                Object impl = entry.getValue();
                if (impl == null) {
                    continue;
                }
                Object name = readField(impl, "name");
                if (name != null && String.valueOf(name).toLowerCase().contains("imgui-java")) {
                    Method close = findMethod(impl.getClass(), "close");
                    if (close != null) {
                        close.invoke(impl);
                    }
                    retainedNatives.add(impl);
                    keysToRemove.add(entry.getKey());
                    closed++;
                }
            }
            for (Object key : keysToRemove) {
                libs.remove(key);
            }
            if (closed > 0) {
                imguiHost = null;
                ZolsiLog.log("imgui unload: FreeLibrary'd " + closed + " native(s); host released, dll is now deletable");
                return true;
            }
            ZolsiLog.log("imgui unload: no imgui-java native matched on host loader");
            return false;
        } catch (Throwable t) {
            ZolsiLog.log("imgui unload failed; keeping host for safe reuse (dll stays locked)", t);
            return false;
        }
    }

    private static void closeAppendedJar() {
        String path = selfJarPath;
        if (path == null || instrumentation == null) {
            return;
        }
        try {
            File target = new File(path).getCanonicalFile();
            if (!openLoaderInternals()) {
                ZolsiLog.log("agent teardown: loader internals not open; appended jar stays locked");
                return;
            }
            Object ucp = readField(ClassLoader.getSystemClassLoader(), "ucp");
            if (ucp == null) {
                return;
            }
            synchronized (ucp) {
                List<?> loaders = asList(readField(ucp, "loaders"));
                Map<?, ?> lmap = asMap(readField(ucp, "lmap"));
                Collection<?> pathList = asCollection(readField(ucp, "path"));
                Collection<?> unopened = asCollection(readField(ucp, "unopenedUrls"));

                List<Object> matched = new ArrayList<Object>();
                if (loaders != null) {
                    for (Object loader : new ArrayList<Object>(loaders)) {
                        if (loader == null) {
                            continue;
                        }
                        Object base = readField(loader, "base");
                        File file = urlToFile(base instanceof URL ? (URL) base : null);
                        if (file != null && sameFile(file, target)) {
                            closeLoader(loader);
                            matched.add(loader);
                        }
                    }
                    loaders.removeAll(matched);
                }
                if (lmap != null && !matched.isEmpty()) {
                    lmap.values().removeAll(matched);
                }
                removeUrls(pathList, target);
                removeUrls(unopened, target);
                ZolsiLog.log("agent teardown: appended jar lock released (" + matched.size() + " loader(s) closed)");
            }
        } catch (Throwable t) {
            ZolsiLog.log("agent teardown: closeAppendedJar failed", t);
        }
    }

    private static boolean openLoaderInternals() {
        if (!internalsOpened) {
            internalsOpened = openPackage("jdk.internal.loader");
        }
        openPackage("java.lang");
        return internalsOpened;
    }

    private static boolean openPackage(String pkg) {
        try {
            Module base = Object.class.getModule();
            Module self = ZolsiAgent.class.getModule();
            Map<String, Set<Module>> opens = new HashMap<String, Set<Module>>();
            opens.put(pkg, Collections.singleton(self));
            instrumentation.redefineModule(base, Collections.<Module>emptySet(),
                Collections.<String, Set<Module>>emptyMap(), opens,
                Collections.<Class<?>>emptySet(), Collections.<Class<?>, List<Class<?>>>emptyMap());
            return true;
        } catch (Throwable t) {
            ZolsiLog.log("agent teardown: redefineModule open " + pkg + " failed", t);
            return false;
        }
    }

    private static void closeLoader(Object loader) {
        try {
            Method close = findMethod(loader.getClass(), "close");
            if (close != null) {
                close.invoke(loader);
            }
        } catch (Throwable t) {
            ZolsiLog.log("agent teardown: loader close failed", t);
        }
    }

    private static void removeUrls(Collection<?> urls, File target) {
        if (urls == null) {
            return;
        }
        try {
            for (Iterator<?> it = urls.iterator(); it.hasNext();) {
                Object entry = it.next();
                if (entry instanceof URL) {
                    File file = urlToFile((URL) entry);
                    if (file != null && sameFile(file, target)) {
                        it.remove();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static File urlToFile(URL url) {
        if (url == null) {
            return null;
        }
        try {
            String proto = url.getProtocol();
            if ("file".equals(proto)) {
                return new File(url.toURI());
            }
            if ("jar".equals(proto)) {
                String spec = url.getPath();
                int bang = spec.indexOf("!/");
                if (bang >= 0) {
                    spec = spec.substring(0, bang);
                }
                return new File(new URI(spec));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean sameFile(File a, File target) {
        try {
            return a.getCanonicalFile().equals(target);
        } catch (Throwable t) {
            return a.getAbsoluteFile().equals(target.getAbsoluteFile());
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> k = type; k != null; k = k.getSuperclass()) {
            try {
                Field field = k.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> k = type; k != null; k = k.getSuperclass()) {
            try {
                Method method = k.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Object readField(Object owner, String name) throws Exception {
        Field field = findField(owner.getClass(), name);
        return field == null ? null : field.get(owner);
    }

    private static List<?> asList(Object o) {
        return o instanceof List ? (List<?>) o : null;
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map ? (Map<?, ?>) o : null;
    }

    private static Collection<?> asCollection(Object o) {
        return o instanceof Collection ? (Collection<?>) o : null;
    }

    private static synchronized void rehook(String selfPath) throws Exception {
        ZolsiLog.log("agent rehook start; selfPath=" + selfPath);
        if (gameClassLoader == null) {
            ZolsiLog.log("rehook aborted: no game loader (first install never completed)");
            return;
        }

        Map<String, byte[]> resources = readSelf(selfPath);
        currentLoader = makeRenderLoader(resources);

        if (transformers.isEmpty()) {
            installTransformers();
            ZolsiLog.log("rehook: transformers reinstalled (reverted on prior unhook)");
        }

        storeHook(currentLoader);
        ZolsiLog.log("rehook complete: fresh render code loaded");
    }

    private static void storeHook(ZolsiClassLoader loader) throws Exception {
        Class<?> hookClass = loader.loadClass("cc.zolsi.mod.inject.InjectedRenderHook");
        Object hookInstance = hookClass.getField("INSTANCE").get(null);
        System.getProperties().put(RenderHookTransformer.HOOK_PROPERTY, hookInstance);
        ZolsiLog.log("render hook stored (" + hookInstance.getClass().getClassLoader() + ")");

        Class<?> tickHookClass = loader.loadClass("cc.zolsi.mod.inject.InjectedTickHook");
        Object tickHookInstance = tickHookClass.getField("INSTANCE").get(null);
        System.getProperties().put(TickHookTransformer.HOOK_PROPERTY, tickHookInstance);
        ZolsiLog.log("tick hook stored (" + tickHookInstance.getClass().getClassLoader() + ")");

        Class<?> hudHookClass = loader.loadClass("cc.zolsi.mod.inject.InjectedHudHook");
        Object hudHookInstance = hudHookClass.getField("INSTANCE").get(null);
        System.getProperties().put(HudHookTransformer.HOOK_PROPERTY, hudHookInstance);
        ZolsiLog.log("hud hook stored (" + hudHookInstance.getClass().getClassLoader() + ")");
    }

    private static Class<?> findMinecraft(Instrumentation inst) {
        return findLoaded(inst, "net.minecraft.client.Minecraft");
    }

    private static Class<?> findLoaded(Instrumentation inst, String name) {
        for (Class<?> loaded : inst.getAllLoadedClasses()) {
            if (loaded.getName().equals(name)) {
                return loaded;
            }
        }
        return null;
    }

    private static Map<String, byte[]> readSelf(String selfPath) {
        Map<String, byte[]> resources = new HashMap<String, byte[]>();
        if (selfPath == null) {
            return resources;
        }
        File jar = new File(selfPath);
        if (!jar.isFile()) {
            return resources;
        }
        try {
            byte[] jarBytes = Files.readAllBytes(jar.toPath());
            readZip(jarBytes, resources);
        } catch (Throwable t) {
            ZolsiLog.log("readSelf failed", t);
        }
        return resources;
    }

    private static void readZip(byte[] zipBytes, Map<String, byte[]> resources) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] data = zis.readAllBytes();
                String name = entry.getName();
                if (name.startsWith("META-INF/jars/") && name.endsWith(".jar")) {
                    readZip(data, resources);
                } else {
                    resources.putIfAbsent(name, data);
                }
            }
        }
    }

    private ZolsiAgent() {
    }
}
