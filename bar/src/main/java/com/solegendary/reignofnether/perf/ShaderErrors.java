package com.solegendary.reignofnether.perf;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts log events that look like the shader pipeline failing (Oculus / Iris errors, GL errors, GLSL compile errors;
 * {@link DiagRules#isShaderError}). A log4j appender on the root logger: it only counts and keeps the first message, it
 * never logs (no recursion). Read and reset every 10 s by {@link ScreenLog}. Installed once, failures ignored.
 */
public final class ShaderErrors {
    private ShaderErrors() { }

    private static final AtomicInteger window = new AtomicInteger();
    private static final AtomicInteger total = new AtomicInteger();
    private static volatile String first = null;
    private static boolean installed = false;

    private static final class Counter extends AbstractAppender {
        Counter() {
            super("BatShaderErrors", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent e) {
            try {
                String msg = e.getMessage() == null ? null : e.getMessage().getFormattedMessage();
                if (!DiagRules.isShaderError(e.getLoggerName(), e.getLevel().isMoreSpecificThan(Level.ERROR), msg))
                    return;
                window.incrementAndGet();
                if (total.incrementAndGet() == 1)
                    first = msg == null ? e.getLoggerName() : (msg.length() > 160 ? msg.substring(0, 160) : msg);
            } catch (RuntimeException ignored) {
                // a counter must never break logging
            }
        }
    }

    public static void install() {
        if (installed)
            return;
        installed = true;
        try {
            LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
            Counter c = new Counter();
            c.start();
            ctx.getConfiguration().getRootLogger().addAppender((Appender) c, null, null);
            ctx.updateLoggers();
        } catch (RuntimeException | LinkageError ignored) {
            // no log4j core: no shader error count
        }
    }

    /** Errors since the last call. */
    public static int takeWindow() {
        return window.getAndSet(0);
    }

    public static int total() {
        return total.get();
    }

    public static String first() {
        return first;
    }
}
