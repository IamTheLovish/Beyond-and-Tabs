package com.solegendary.reignofnether.perf;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The pure rules behind the diagnosis lines ([Screen], the Xmx check in [Gfx], the report bundle name). No Minecraft
 * types, so DiagnoseGameTests checks them without rendering. See design/diagnostics.md.
 */
public final class DiagRules {
    private DiagRules() { }

    /** Xmx at or above this share of physical RAM starves the OS and the GPU driver: flagged. */
    public static final double XMX_RAM_FLAG = 0.75;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    /** True when the JVM may take {@link #XMX_RAM_FLAG} or more of the physical RAM. Unknown RAM (<= 0) is not flagged. */
    public static boolean xmxTooHigh(long xmxBytes, long ramBytes) {
        return xmxBytes > 0 && ramBytes > 0 && xmxBytes >= ramBytes * XMX_RAM_FLAG;
    }

    /** "Xmx 8192 MB of 16384 MB RAM (50%)" plus a warning when flagged. */
    public static String xmxText(long xmxBytes, long ramBytes) {
        long mb = 1024L * 1024L;
        String s = "Xmx " + (xmxBytes / mb) + " MB of " + (ramBytes > 0 ? (ramBytes / mb) + " MB" : "unknown") + " RAM";
        if (ramBytes > 0 && xmxBytes > 0)
            s += " (" + Math.round(100.0 * xmxBytes / ramBytes) + "%)";
        if (xmxTooHigh(xmxBytes, ramBytes))
            s += " - TOO HIGH: Xmx is 75% or more of RAM, the system may swap";
        return s;
    }

    /**
     * What a non-game screen is waiting for, from whatever is known. A negative number or a null / empty string means
     * "not known" and is left out. Empty result: nothing is known.
     */
    public static String waiting(int seatsFilled, int seatsTotal, int chunkPct, int prepPct, String phase) {
        StringBuilder sb = new StringBuilder();
        if (seatsTotal >= 0 && seatsFilled >= 0)
            sb.append("lobby seats ").append(seatsFilled).append('/').append(seatsTotal);
        if (chunkPct >= 0)
            sb.append(sb.length() > 0 ? ", " : "").append("chunks in view ").append(Math.min(100, chunkPct)).append('%');
        if (prepPct >= 0)
            sb.append(sb.length() > 0 ? ", " : "").append("terrain prep ").append(Math.min(100, prepPct)).append('%');
        if (phase != null && !phase.isEmpty())
            sb.append(sb.length() > 0 ? ", " : "").append("phase ").append(phase);
        return sb.toString();
    }

    /** The [Screen] line text after the tag: change lines carry no wait, repeat lines say how long and for what. */
    public static String screenLine(String simpleName, String title, long openForSec, String waiting) {
        StringBuilder sb = new StringBuilder();
        sb.append(simpleName == null || simpleName.isEmpty() ? "(anonymous screen)" : simpleName);
        if (title != null && !title.isEmpty())
            sb.append(" \"").append(title).append('"');
        if (openForSec > 0)
            sb.append(" still open after ").append(openForSec).append(" s");
        if (waiting != null && !waiting.isEmpty())
            sb.append("; waiting for: ").append(waiting);
        return sb.toString();
    }

    /** logs/bat-report-yyyyMMdd-HHmmss.zip (file name only). */
    public static String reportName(LocalDateTime t) {
        return "bat-report-" + STAMP.format(t) + ".zip";
    }

    /**
     * Whether a log event looks like the shader pipeline failing: a GL error, a GLSL "Unknown variable", a failed
     * compile, or any ERROR from an Oculus / Iris logger. Pure: the appender passes the event's pieces.
     */
    public static boolean isShaderError(String loggerName, boolean errorOrWorse, String message) {
        String m = message == null ? "" : message;
        if (m.contains("GL_INVALID_") || m.contains("GL ERROR") || m.contains("Unknown variable")
                || m.contains("Failed to compile") || m.contains("Failed to link"))
            return true;
        if (!errorOrWorse || loggerName == null)
            return false;
        String n = loggerName.toLowerCase(Locale.ROOT);
        return n.contains("iris") || n.contains("oculus");
    }
}
