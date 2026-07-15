package cc.zolsi.mod.inject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public final class ZolsiClassLoader extends ClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final Map<String, byte[]> resources;
    private final boolean reuseParentImgui;

    public ZolsiClassLoader(Map<String, byte[]> resources, ClassLoader parent) {
        this(resources, parent, false);
    }

    public ZolsiClassLoader(Map<String, byte[]> resources, ClassLoader parent, boolean reuseParentImgui) {
        super(parent);
        this.resources = resources;
        this.reuseParentImgui = reuseParentImgui;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                boolean owned = resources.containsKey(name.replace('.', '/') + ".class");
                boolean selfLoad = name.startsWith("imgui.")
                    ? (!reuseParentImgui && owned)
                    : owned || name.startsWith("cc.zolsi.");
                if (selfLoad) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                } else {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = resources.get(name.replace('.', '/') + ".class");
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String pkg = name.substring(0, dot);
            if (getDefinedPackage(pkg) == null) {
                try {
                    definePackage(pkg, null, null, null, null, null, null, null);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    @Override
    protected URL findResource(String name) {
        byte[] bytes = resources.get(normalize(name));
        return bytes == null ? null : memUrl(name, bytes);
    }

    @Override
    protected Enumeration<URL> findResources(String name) {
        byte[] bytes = resources.get(normalize(name));
        List<URL> list = new ArrayList<URL>(1);
        if (bytes != null) {
            URL url = memUrl(name, bytes);
            if (url != null) {
                list.add(url);
            }
        }
        return Collections.enumeration(list);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] bytes = resources.get(normalize(name));
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        return super.getResourceAsStream(name);
    }

    public void clearResources() {
        resources.clear();
    }

    private static String normalize(String name) {
        return name.startsWith("/") ? name.substring(1) : name;
    }

    private URL memUrl(String name, byte[] bytes) {
        try {
            return new URL("zolsimem", "", -1, "/" + normalize(name), new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) {
                    return new URLConnection(u) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(bytes);
                        }
                    };
                }
            });
        } catch (Throwable t) {
            return null;
        }
    }
}
