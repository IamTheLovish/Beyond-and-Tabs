package org.junit.jupiter.api;
/** Shim: JUnit 5's @Test, so the maps/gen unit tests run under plain javac (tools/mapgen_sim/run.sh junit). */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
public @interface Test { }
