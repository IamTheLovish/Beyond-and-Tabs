package org.junit.jupiter.api;
/** Shim: JUnit 5's @Disabled (the runner skips the method). */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Disabled { String value() default ""; }
