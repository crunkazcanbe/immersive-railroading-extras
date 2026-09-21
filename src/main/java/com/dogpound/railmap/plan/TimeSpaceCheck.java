package com.dogpound.railmap.plan;

/**
 * Self-check for the booking logic. Run it, don't trust it:
 *
 * <pre>
 *   cd ~/mc-mods/railmap/src/main/java
 *   javac -d /tmp/tsc com/dogpound/railmap/plan/TimeSpaceMap.java \
 *                     com/dogpound/railmap/plan/Timetable.java \
 *                     com/dogpound/railmap/plan/TimeSpaceCheck.java
 *   java -cp /tmp/tsc com.dogpound.railmap.plan.TimeSpaceCheck
 * </pre>
 *
 * <p>These are the invariants that, if they silently broke, would let the planner book two
 * trains onto one piece of track and produce a timetable that looks fine and cannot be run.
 * No Minecraft classes are touched, so it compiles and runs on a bare JDK.
 */
public final class TimeSpaceCheck {

    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) throw new AssertionError("FAILED: " + what);
    }

    public static void main(String[] args) {
        TimeSpaceMap m = new TimeSpaceMap();
        m.setHeadway(0);                       // exact-overlap behaviour first
        long piece = 12345L;

        check(m.test(piece, 100, 200, -1) == null, "empty map has no conflicts");

        m.reserve(piece, 100, 200, 1, 100);
        check(m.size() == 1, "one booking held");
        check(m.pieces() == 1, "one piece touched");

        check(m.test(piece, 150, 250, -1) != null, "overlapping window conflicts");
        check(m.test(piece, 201, 300, -1) == null, "window after the booking is free");
        check(m.test(piece, 1, 99, -1) == null, "window before the booking is free");
        check(m.test(piece, 150, 250, 1) == null, "a service never conflicts with itself");
        check(m.test(999L, 150, 250, -1) == null, "a different piece is unaffected");

        // Headway: the gap that stops two trains being booked nose-to-tail.
        m.setHeadway(20);
        check(m.test(piece, 210, 300, -1) != null,
              "a window inside the headway gap must conflict");
        check(m.test(piece, 400, 500, -1) == null,
              "a window well clear of the headway is free");

        // pushRequired must be enough to actually clear the blocker.
        TimeSpaceMap.Conflict c = m.test(piece, 150, 250, -1);
        check(c != null, "expected a conflict to measure");
        long pushed = 150 + c.pushRequired();
        check(pushed > 200, "pushRequired moves the start past the blocker's end");

        // Clearing by service must remove exactly that service's bookings.
        m.reserve(piece, 1000, 1100, 2, 1000);
        check(m.size() == 2, "two bookings now");
        m.clearService(1);
        check(m.size() == 1, "clearService removed only service 1");
        check(m.test(piece, 100, 200, -1) == null, "service 1's window freed up");
        check(m.test(piece, 1000, 1100, -1) != null, "service 2's booking survived");

        // Timetable recurrence arithmetic: a repeating service must always return a
        // departure at or after 'now', never one in the past.
        check(Recurrence.next(1000, 400, true, 0) == 1000, "before the first departure returns it");
        check(Recurrence.next(1000, 400, true, 1000) == 1000, "exactly at departure returns it");
        check(Recurrence.next(1000, 400, true, 1001) == 1400, "just after rolls to the next");
        check(Recurrence.next(1000, 400, true, 1400) == 1400, "exactly on a later departure returns it");
        check(Recurrence.next(1000, 400, true, 1401) == 1800, "and rolls again");
        check(Recurrence.next(1000, 0, true, 1001) == -1L, "a one-off that has gone returns -1");
        check(Recurrence.next(1000, 400, false, 0) == -1L, "a disabled service never departs");

        System.out.println("TimeSpaceCheck: all " + checks + " checks passed");
    }
}
