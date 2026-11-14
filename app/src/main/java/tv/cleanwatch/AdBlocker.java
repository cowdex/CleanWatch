package tv.cleanwatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host blocklist. A request is blocked when its host, or any parent domain of it, is listed.
 *
 * Accepts one entry per line in any of these forms; '#' and '!' start a comment:
 * <pre>
 *   ads.example.com
 *   0.0.0.0 ads.example.com
 *   ||ads.example.com^
 * </pre>
 */
final class AdBlocker {
    private final Set<String> hosts = new HashSet<>();
    private final AtomicInteger blocked = new AtomicInteger();

    /** Adds the entries of one list. Call before the first page load; not thread-safe. */
    void load(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String host = parse(line);
                if (host != null) {
                    hosts.add(host);
                }
            }
        }
    }

    static String parse(String line) {
        int hash = line.indexOf('#');
        if (hash >= 0) {
            line = line.substring(0, hash);
        }
        line = line.trim().toLowerCase(Locale.ROOT);
        if (line.isEmpty() || line.charAt(0) == '!') {
            return null;
        }
        if (line.startsWith("||")) {
            line = line.substring(2);
            int end = line.indexOf('^');
            if (end >= 0) {
                line = line.substring(0, end);
            }
        } else {
            // hosts-file form: keep the last field
            int space = Math.max(line.lastIndexOf(' '), line.lastIndexOf('\t'));
            if (space >= 0) {
                line = line.substring(space + 1);
            }
        }
        if (line.indexOf('.') <= 0 || line.endsWith(".") || line.indexOf('/') >= 0
                || line.equals("0.0.0.0") || line.equals("127.0.0.1")) {
            return null;
        }
        return line;
    }

    /** True when the host or one of its parent domains is listed. Safe from any thread. */
    boolean matches(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (hosts.contains(h)) {
                return true;
            }
            int dot = h.indexOf('.');
            if (dot < 0) {
                return false;
            }
            h = h.substring(dot + 1);
            if (h.indexOf('.') < 0) {
                return false; // a bare top-level domain is never an entry
            }
        }
    }

    void countBlocked() {
        blocked.incrementAndGet();
    }

    int blockedCount() {
        return blocked.get();
    }

    int size() {
        return hosts.size();
    }
}
