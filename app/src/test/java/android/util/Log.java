package android.util;

/**
 * Test-only stand-in for the Android log, declared in the unit-test source set.
 *
 * Without it, any production line that calls Log.* throws
 * "Method i in android.util.Log not mocked" under testDebugUnitTest, which is what kept
 * CommitmentRestoreEngine (it logs on every branch) out of the unit suite. This shadows the
 * stubbed method bodies from the mockable android.jar for JVM tests only; it is not packaged into
 * the APK and never runs on a device, where the real android.util.Log is used.
 *
 * Deliberately silent: unit tests must assert on state (returned statuses, recorded writes), not on
 * log output, and the project rule forbids routing identifiers through logs anyway.
 */
public final class Log {

    private Log() {}

    public static int v(String tag, String msg) { return 0; }

    public static int v(String tag, String msg, Throwable tr) { return 0; }

    public static int d(String tag, String msg) { return 0; }

    public static int d(String tag, String msg, Throwable tr) { return 0; }

    public static int i(String tag, String msg) { return 0; }

    public static int i(String tag, String msg, Throwable tr) { return 0; }

    public static int w(String tag, String msg) { return 0; }

    public static int w(String tag, String msg, Throwable tr) { return 0; }

    public static int w(String tag, Throwable tr) { return 0; }

    public static int e(String tag, String msg) { return 0; }

    public static int e(String tag, String msg, Throwable tr) { return 0; }

    public static int println(int priority, String tag, String msg) { return 0; }

    public static boolean isLoggable(String tag, int level) { return false; }
}
