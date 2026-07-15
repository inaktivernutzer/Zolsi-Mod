package cc.zolsi.mod.gui;

import imgui.ImDrawList;
import imgui.flag.ImDrawFlags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Icons {

    private static final float VIEW = 24.0f;
    private static final Map<String, List<Poly>> CACHE = new HashMap<>();

    private static final class Poly {
        final float[] pts;
        final boolean closed;

        Poly(float[] pts, boolean closed) {
            this.pts = pts;
            this.closed = closed;
        }
    }

    private Icons() {
    }

    public static void draw(ImDrawList dl, String name, float x, float y, float size, int col) {
        drawRotated(dl, name, x, y, size, col, 0.0f);
    }

    public static void drawRotated(ImDrawList dl, String name, float x, float y, float size, int col, float angle) {
        List<Poly> polys = get(name);
        if (polys == null) {
            return;
        }
        float scale = size / VIEW;
        float stroke = Math.max(1.3f, size / 12.0f);
        float cap = stroke * 0.5f;
        float cx = x + size * 0.5f;
        float cy = y + size * 0.5f;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        for (Poly p : polys) {
            int n = p.pts.length / 2;
            if (n < 2) {
                continue;
            }
            dl.pathClear();
            for (int i = 0; i < n; i++) {
                float lx = x + p.pts[i * 2] * scale - cx;
                float ly = y + p.pts[i * 2 + 1] * scale - cy;
                dl.pathLineTo(cx + lx * cos - ly * sin, cy + lx * sin + ly * cos);
            }
            dl.pathStroke(col, p.closed ? ImDrawFlags.Closed : ImDrawFlags.None, stroke);
            if (!p.closed) {
                cap(dl, p, 0, x, y, scale, cx, cy, cos, sin, cap, col);
                cap(dl, p, n - 1, x, y, scale, cx, cy, cos, sin, cap, col);
            }
        }
    }

    private static void cap(ImDrawList dl, Poly p, int idx, float x, float y, float scale,
                            float cx, float cy, float cos, float sin, float cap, int col) {
        float lx = x + p.pts[idx * 2] * scale - cx;
        float ly = y + p.pts[idx * 2 + 1] * scale - cy;
        dl.addCircleFilled(cx + lx * cos - ly * sin, cy + lx * sin + ly * cos, cap, col);
    }

    private static List<Poly> get(String name) {
        List<Poly> cached = CACHE.get(name);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        List<Poly> polys = new ArrayList<>();
        try {
            String svg = readResource("/assets/zolsi/icons/" + name + ".svg");
            if (svg != null) {
                parse(svg, polys);
            }
        } catch (Throwable ignored) {
        }
        CACHE.put(name, polys);
        return polys.isEmpty() ? null : polys;
    }

    private static String readResource(String path) {
        try (java.io.InputStream in = Icons.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void parse(String svg, List<Poly> polys) {
        parseElements(svg, "<path", polys);
        parseElements(svg, "<circle", polys);
        parseElements(svg, "<rect", polys);
    }

    private static void parseElements(String svg, String tag, List<Poly> polys) {
        int from = 0;
        while (true) {
            int start = svg.indexOf(tag, from);
            if (start < 0) {
                return;
            }
            int end = svg.indexOf('>', start);
            if (end < 0) {
                return;
            }
            String element = svg.substring(start, end);
            from = end + 1;
            if (tag.equals("<path")) {
                String d = attr(element, "d");
                if (d != null) {
                    parsePath(d, polys);
                }
            } else if (tag.equals("<circle")) {
                float cx = attrF(element, "cx", 0.0f);
                float cy = attrF(element, "cy", 0.0f);
                float r = attrF(element, "r", 0.0f);
                if (r > 0.0f) {
                    polys.add(new Poly(circle(cx, cy, r), true));
                }
            } else if (tag.equals("<rect")) {
                float rx = attrF(element, "x", 0.0f);
                float ry = attrF(element, "y", 0.0f);
                float rw = attrF(element, "width", 0.0f);
                float rh = attrF(element, "height", 0.0f);
                float round = attrF(element, "rx", 0.0f);
                if (rw > 0.0f && rh > 0.0f) {
                    polys.add(new Poly(roundRect(rx, ry, rw, rh, round), true));
                }
            }
        }
    }

    private static String attr(String element, String key) {
        String needle = " " + key + "=\"";
        int i = element.indexOf(needle);
        if (i < 0) {
            return null;
        }
        i += needle.length();
        int j = element.indexOf('"', i);
        if (j < 0) {
            return null;
        }
        return element.substring(i, j);
    }

    private static float attrF(String element, String key, float def) {
        String v = attr(element, key);
        if (v == null) {
            return def;
        }
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float[] circle(float cx, float cy, float r) {
        int n = 40;
        float[] pts = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2.0;
            pts[i * 2] = cx + (float) Math.cos(a) * r;
            pts[i * 2 + 1] = cy + (float) Math.sin(a) * r;
        }
        return pts;
    }

    private static float[] roundRect(float x, float y, float w, float h, float r) {
        r = Math.max(0.0f, Math.min(r, Math.min(w, h) * 0.5f));
        List<Float> pts = new ArrayList<>();
        addCorner(pts, x + w - r, y + r, r, -Math.PI / 2.0, 0.0);
        addCorner(pts, x + w - r, y + h - r, r, 0.0, Math.PI / 2.0);
        addCorner(pts, x + r, y + h - r, r, Math.PI / 2.0, Math.PI);
        addCorner(pts, x + r, y + r, r, Math.PI, Math.PI * 1.5);
        float[] out = new float[pts.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = pts.get(i);
        }
        return out;
    }

    private static void addCorner(List<Float> pts, float cx, float cy, float r, double a0, double a1) {
        int seg = 8;
        for (int i = 0; i <= seg; i++) {
            double a = a0 + (a1 - a0) * (i / (double) seg);
            pts.add(cx + (float) Math.cos(a) * r);
            pts.add(cy + (float) Math.sin(a) * r);
        }
    }

    private static void parsePath(String d, List<Poly> polys) {
        PathScanner s = new PathScanner(d);
        List<Float> cur = new ArrayList<>();
        boolean curClosed = false;
        float px = 0.0f;
        float py = 0.0f;
        float sx = 0.0f;
        float sy = 0.0f;
        float cpx = 0.0f;
        float cpy = 0.0f;
        char cmd = 0;
        char prevCmd = 0;
        while (s.hasCommand()) {
            char c = s.nextCommand();
            if (c != 0) {
                cmd = c;
            } else if (cmd == 'M') {
                cmd = 'L';
            } else if (cmd == 'm') {
                cmd = 'l';
            }
            boolean rel = Character.isLowerCase(cmd);
            char u = Character.toUpperCase(cmd);
            switch (u) {
                case 'M': {
                    float nx = s.num() + (rel ? px : 0.0f);
                    float ny = s.num() + (rel ? py : 0.0f);
                    flush(cur, curClosed, polys);
                    cur = new ArrayList<>();
                    curClosed = false;
                    px = nx;
                    py = ny;
                    sx = px;
                    sy = py;
                    cur.add(px);
                    cur.add(py);
                    break;
                }
                case 'L': {
                    px = s.num() + (rel ? px : 0.0f);
                    py = s.num() + (rel ? py : 0.0f);
                    cur.add(px);
                    cur.add(py);
                    break;
                }
                case 'H': {
                    px = s.num() + (rel ? px : 0.0f);
                    cur.add(px);
                    cur.add(py);
                    break;
                }
                case 'V': {
                    py = s.num() + (rel ? py : 0.0f);
                    cur.add(px);
                    cur.add(py);
                    break;
                }
                case 'C': {
                    float x1 = s.num() + (rel ? px : 0.0f);
                    float y1 = s.num() + (rel ? py : 0.0f);
                    float x2 = s.num() + (rel ? px : 0.0f);
                    float y2 = s.num() + (rel ? py : 0.0f);
                    float ex = s.num() + (rel ? px : 0.0f);
                    float ey = s.num() + (rel ? py : 0.0f);
                    cubic(cur, px, py, x1, y1, x2, y2, ex, ey);
                    cpx = x2;
                    cpy = y2;
                    px = ex;
                    py = ey;
                    break;
                }
                case 'S': {
                    float x1 = px;
                    float y1 = py;
                    if (prevCmd == 'C' || prevCmd == 'S') {
                        x1 = 2.0f * px - cpx;
                        y1 = 2.0f * py - cpy;
                    }
                    float x2 = s.num() + (rel ? px : 0.0f);
                    float y2 = s.num() + (rel ? py : 0.0f);
                    float ex = s.num() + (rel ? px : 0.0f);
                    float ey = s.num() + (rel ? py : 0.0f);
                    cubic(cur, px, py, x1, y1, x2, y2, ex, ey);
                    cpx = x2;
                    cpy = y2;
                    px = ex;
                    py = ey;
                    break;
                }
                case 'Q': {
                    float x1 = s.num() + (rel ? px : 0.0f);
                    float y1 = s.num() + (rel ? py : 0.0f);
                    float ex = s.num() + (rel ? px : 0.0f);
                    float ey = s.num() + (rel ? py : 0.0f);
                    quad(cur, px, py, x1, y1, ex, ey);
                    cpx = x1;
                    cpy = y1;
                    px = ex;
                    py = ey;
                    break;
                }
                case 'T': {
                    float x1 = px;
                    float y1 = py;
                    if (prevCmd == 'Q' || prevCmd == 'T') {
                        x1 = 2.0f * px - cpx;
                        y1 = 2.0f * py - cpy;
                    }
                    float ex = s.num() + (rel ? px : 0.0f);
                    float ey = s.num() + (rel ? py : 0.0f);
                    quad(cur, px, py, x1, y1, ex, ey);
                    cpx = x1;
                    cpy = y1;
                    px = ex;
                    py = ey;
                    break;
                }
                case 'A': {
                    float rx = s.num();
                    float ry = s.num();
                    float rot = s.num();
                    float large = s.num();
                    float sweep = s.num();
                    float ex = s.num() + (rel ? px : 0.0f);
                    float ey = s.num() + (rel ? py : 0.0f);
                    arc(cur, px, py, rx, ry, rot, large != 0.0f, sweep != 0.0f, ex, ey);
                    px = ex;
                    py = ey;
                    break;
                }
                case 'Z': {
                    curClosed = true;
                    px = sx;
                    py = sy;
                    break;
                }
                default:
                    return;
            }
            prevCmd = u;
        }
        flush(cur, curClosed, polys);
    }

    private static void flush(List<Float> cur, boolean closed, List<Poly> polys) {
        if (cur.size() < 4) {
            return;
        }
        float[] arr = new float[cur.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = cur.get(i);
        }
        polys.add(new Poly(arr, closed));
    }

    private static void cubic(List<Float> out, float x0, float y0, float x1, float y1,
                              float x2, float y2, float x3, float y3) {
        int seg = 20;
        for (int i = 1; i <= seg; i++) {
            float t = i / (float) seg;
            float mt = 1.0f - t;
            float a = mt * mt * mt;
            float b = 3.0f * mt * mt * t;
            float c = 3.0f * mt * t * t;
            float e = t * t * t;
            out.add(a * x0 + b * x1 + c * x2 + e * x3);
            out.add(a * y0 + b * y1 + c * y2 + e * y3);
        }
    }

    private static void quad(List<Float> out, float x0, float y0, float x1, float y1, float x2, float y2) {
        int seg = 16;
        for (int i = 1; i <= seg; i++) {
            float t = i / (float) seg;
            float mt = 1.0f - t;
            out.add(mt * mt * x0 + 2.0f * mt * t * x1 + t * t * x2);
            out.add(mt * mt * y0 + 2.0f * mt * t * y1 + t * t * y2);
        }
    }

    private static void arc(List<Float> out, float x0, float y0, float rx, float ry,
                            float rotDeg, boolean large, boolean sweep, float x1, float y1) {
        if (rx == 0.0f || ry == 0.0f) {
            out.add(x1);
            out.add(y1);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(rotDeg);
        double cosP = Math.cos(phi);
        double sinP = Math.sin(phi);
        double dx = (x0 - x1) / 2.0;
        double dy = (y0 - y1) / 2.0;
        double x1p = cosP * dx + sinP * dy;
        double y1p = -sinP * dx + cosP * dy;

        double rxs = rx * rx;
        double rys = ry * ry;
        double x1ps = x1p * x1p;
        double y1ps = y1p * y1p;
        double lambda = x1ps / rxs + y1ps / rys;
        if (lambda > 1.0) {
            double sq = Math.sqrt(lambda);
            rx *= sq;
            ry *= sq;
            rxs = rx * rx;
            rys = ry * ry;
        }

        double sign = (large != sweep) ? 1.0 : -1.0;
        double num = rxs * rys - rxs * y1ps - rys * x1ps;
        double den = rxs * y1ps + rys * x1ps;
        double co = sign * Math.sqrt(Math.max(0.0, num / den));
        double cxp = co * (rx * y1p / ry);
        double cyp = co * -(ry * x1p / rx);

        double cx = cosP * cxp - sinP * cyp + (x0 + x1) / 2.0;
        double cy = sinP * cxp + cosP * cyp + (y0 + y1) / 2.0;

        double startAngle = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && delta > 0.0) {
            delta -= Math.PI * 2.0;
        } else if (sweep && delta < 0.0) {
            delta += Math.PI * 2.0;
        }

        int seg = Math.max(4, (int) Math.ceil(Math.abs(delta) / (Math.PI / 16.0)));
        for (int i = 1; i <= seg; i++) {
            double a = startAngle + delta * (i / (double) seg);
            double ex = Math.cos(a) * rx;
            double ey = Math.sin(a) * ry;
            out.add((float) (cosP * ex - sinP * ey + cx));
            out.add((float) (sinP * ex + cosP * ey + cy));
        }
    }

    private static double angle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        double a = Math.acos(Math.max(-1.0, Math.min(1.0, dot / len)));
        if (ux * vy - uy * vx < 0.0) {
            a = -a;
        }
        return a;
    }

    private static final class PathScanner {
        private final String s;
        private int i;

        PathScanner(String s) {
            this.s = s;
        }

        private void skip() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == ',' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        boolean hasCommand() {
            skip();
            return i < s.length();
        }

        char nextCommand() {
            skip();
            if (i >= s.length()) {
                return 0;
            }
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                i++;
                return c;
            }
            return 0;
        }

        float num() {
            skip();
            int start = i;
            boolean seenDot = false;
            boolean seenDigit = false;
            if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                i++;
            }
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    seenDigit = true;
                    i++;
                } else if (c == '.' && !seenDot) {
                    seenDot = true;
                    i++;
                } else if ((c == 'e' || c == 'E') && seenDigit) {
                    i++;
                    if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                        i++;
                    }
                } else {
                    break;
                }
            }
            if (i == start) {
                return 0.0f;
            }
            try {
                return Float.parseFloat(s.substring(start, i));
            } catch (NumberFormatException e) {
                return 0.0f;
            }
        }
    }
}
