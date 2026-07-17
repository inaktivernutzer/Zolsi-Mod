package cc.zolsi.mod;

import java.util.ArrayDeque;
import java.util.Date;

public final class ZolsiLog {

    private static final int MAX = 500;
    private static final ArrayDeque<String> BUFFER = new ArrayDeque<String>();

    public static synchronized void log(String message) {
        write(message, null);
    }

    public static synchronized void log(String message, Throwable error) {
        write(message, error);
    }

    private static void write(String message, Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append(new Date()).append("  ").append(message);
        if (error != null) {
            sb.append(" | ").append(error);
        }
        BUFFER.addLast(sb.toString());
        while (BUFFER.size() > MAX) {
            BUFFER.removeFirst();
        }
    }

    private ZolsiLog() {
    }
}
