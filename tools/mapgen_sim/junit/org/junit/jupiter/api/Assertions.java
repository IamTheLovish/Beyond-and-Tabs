package org.junit.jupiter.api;

/** Shim: the JUnit 5 assertions the maps tests use, failing with an AssertionError as JUnit does. */
public final class Assertions {
    private Assertions() { }

    private static void fail(String msg, Object expected, Object actual) {
        throw new AssertionError((msg == null ? "" : msg + " ==> ") + "expected: <" + expected + "> but was: <" + actual + ">");
    }

    public static void assertTrue(boolean c, String msg) { if (!c) throw new AssertionError(msg); }
    public static void assertTrue(boolean c) { assertTrue(c, "expected true"); }
    public static void assertFalse(boolean c, String msg) { if (c) throw new AssertionError(msg); }
    public static void assertFalse(boolean c) { assertFalse(c, "expected false"); }
    public static void assertEquals(double e, double a, double d, String msg) { if (!(Math.abs(e - a) <= d)) fail(msg, e, a); }
    public static void assertEquals(long e, long a, String msg) { if (e != a) fail(msg, e, a); }
    public static void assertEquals(long e, long a) { assertEquals(e, a, null); }
    public static void assertEquals(Object e, Object a, String msg) { if (!java.util.Objects.equals(e, a)) fail(msg, e, a); }
    public static void assertEquals(Object e, Object a) { assertEquals(e, a, null); }
    public static void assertNotEquals(Object e, Object a, String msg) { if (java.util.Objects.equals(e, a)) fail(msg, "not " + e, a); }
    public static void assertNotEquals(Object e, Object a) { assertNotEquals(e, a, null); }
    public static void assertNotNull(Object o, String msg) { if (o == null) throw new AssertionError(msg); }
    public static void assertNotNull(Object o) { assertNotNull(o, "expected not null"); }
}
