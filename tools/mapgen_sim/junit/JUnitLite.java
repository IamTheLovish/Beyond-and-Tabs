import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * A tiny JUnit 5 stand-in for the headless sim (Gradle and Maven are not reachable in the cloud sessions): runs every
 * {@code @Test} method of the named classes in declaration-name order and prints one line each; exit 1 on any failure.
 * CI still runs the real JUnit through Gradle - this only lets a session see red and green before it pushes.
 */
public final class JUnitLite {
    public static void main(String[] classes) throws Exception {
        int pass = 0, fail = 0;
        for (String cn : classes) {
            Class<?> c = Class.forName(cn);
            Method[] ms = c.getDeclaredMethods();
            Arrays.sort(ms, Comparator.comparing(Method::getName));
            for (Method m : ms) {
                if (!m.isAnnotationPresent(org.junit.jupiter.api.Test.class)
                        || m.isAnnotationPresent(org.junit.jupiter.api.Disabled.class))
                    continue;
                m.setAccessible(true);
                long t0 = System.nanoTime();
                try {
                    var ctor = c.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    m.invoke(Modifier.isStatic(m.getModifiers()) ? null : ctor.newInstance());
                    pass++;
                    System.out.printf("PASS %s.%s (%d ms)%n", c.getSimpleName(), m.getName(), (System.nanoTime() - t0) / 1_000_000);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    fail++;
                    System.out.printf("FAIL %s.%s: %s%n", c.getSimpleName(), m.getName(), e.getCause());
                }
            }
        }
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
