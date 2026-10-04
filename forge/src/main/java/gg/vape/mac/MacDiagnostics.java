package gg.vape.mac;

import gg.vape.mac.bootstrap.MacAgent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded diagnostics: a failing module must not write a stack trace every tick. */
public final class MacDiagnostics {
    private static final Set<String> seen = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    private static final AtomicLong failures = new AtomicLong();
    public static long failures() { return failures.get(); }
    public static void failure(String source, Throwable error) {
        failures.incrementAndGet();
        String key = source + ":" + error.getClass().getName();
        if (seen.size() < 64 && seen.add(key)) MacAgent.log("Runtime listener failure " + source + "\n" + MacAgent.describe(error));
    }
}
