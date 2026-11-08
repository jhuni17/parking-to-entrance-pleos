package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;

import static kr.juchakkaji.app.ParkingPlan.*;
import static kr.juchakkaji.app.VehicleReadResult.Session.Action.*;
import static org.junit.Assert.*;

public final class VehicleReadResultTest {
    private static VehicleReadResult hardware() {
        return VehicleReadResult.connected(VehicleProfile.fromHardware(null, "Test vehicle", null,
                new Integer[]{2250, 4700, 1990}, new Integer[]{FuelType.UNLEADED}));
    }

    /** Fake clock/reader: the same policy as Activity, without sleeping or Android handlers. */
    private static final class FakeScreen {
        final VehicleReadResult.Session session = new VehicleReadResult.Session();
        long now;
        int expirations;
        Runnable expiry;
        int generation;
        int reads;
        boolean reading;
        VehicleProfile vehicle = VehicleProfile.UNKNOWN;
        Runnable scheduled;

        void start(VehicleReadResult first, VehicleReadResult second) {
            int token = ++generation;
            session.start(token, now);
            expiry = () -> apply(session.expire(token, now));
            vehicle = VehicleProfile.UNKNOWN;
            reading = true;
            scheduled = null;
            read(token, 0, first, second);
        }

        void read(int token, int attempt, VehicleReadResult result, VehicleReadResult second) {
            if (!session.isCurrent(token)) return;
            if (session.expire(token, now) == EXPIRE) {
                apply(EXPIRE);
                return;
            }
            reads++;
            if (result != null) deliver(token, attempt, result, second);
        }

        void deliver(int token, int attempt, VehicleReadResult result, VehicleReadResult second) {
            switch (session.accept(token, attempt, result, now)) {
                case RETRY -> {
                    long due = now + VehicleReadResult.Session.RETRY_DELAY_MS;
                    scheduled = () -> { now = Math.max(now, due); read(token, 1, second, null); };
                }
                case EXPIRE -> apply(EXPIRE);
                case COMPLETE -> {
                    vehicle = result.profile();
                    reading = false;
                }
                case IGNORE -> { }
            }
        }

        void apply(VehicleReadResult.Session.Action action) {
            if (action == EXPIRE) {
                expirations++;
                vehicle = VehicleProfile.UNKNOWN;
                reading = false;
            }
        }

        void advance(long time) {
            now = time;
            expiry.run();
        }

        void stop() {
            generation++;
            session.stop();
            scheduled = null;
            reading = false;
        }
    }

    @Test public void disconnectedThenSuccessAutomaticallyRecalculatesWithHardware() {
        FakeScreen screen = new FakeScreen();
        screen.start(VehicleReadResult.disconnected(), hardware());
        assertEquals(1, screen.reads);
        assertTrue(screen.reading);
        assertNull(screen.vehicle.heightMm());
        assertEquals(0, requestableCount(compare(SampleTrip.LOTS, screen.vehicle.heightMm(),
                screen.vehicle.drive(), 120, false)));
        assertEquals(500, VehicleReadResult.Session.RETRY_DELAY_MS);
        screen.scheduled.run();
        assertEquals(2, screen.reads);
        assertFalse(screen.reading);
        assertEquals(Integer.valueOf(2250), screen.vehicle.heightMm());
        assertFalse(screen.vehicle.catalogHeight());
        var options = compare(SampleTrip.LOTS, screen.vehicle.heightMm(), screen.vehicle.drive(), 120, false);
        assertEquals("대안 A · 시연 주차장", options.get(0).lot().name());
        assertEquals(1, requestableCount(options));
        screen.scheduled.run(); // Even a duplicate timer cannot read a third time.
        assertEquals(2, screen.reads);
    }

    @Test public void twoDisconnectedReadsTerminateUnknownWithoutDemoOrFuelAssumptions() {
        FakeScreen screen = new FakeScreen();
        screen.start(VehicleReadResult.disconnected(), VehicleReadResult.disconnected());
        screen.scheduled.run();
        screen.scheduled.run();
        assertEquals(2, screen.reads);
        assertFalse(screen.reading);
        assertEquals(VehicleProfile.UNKNOWN, screen.vehicle);
        assertNull(screen.vehicle.heightMm());
        assertEquals(VehicleProfile.DriveType.NEEDS_CHECK, screen.vehicle.drive());
        assertEquals(0, requestableCount(compare(SampleTrip.LOTS, screen.vehicle.heightMm(),
                screen.vehicle.drive(), 120, false)));
    }

    @Test public void connectedWithoutDimensionsAndUnavailableDoNotRetry() {
        for (VehicleReadResult result : new VehicleReadResult[]{
                VehicleReadResult.connected(VehicleProfile.fromHardware(null, null, null, null, null)),
                VehicleReadResult.unavailable(), hardware()}) {
            FakeScreen screen = new FakeScreen();
            screen.start(result, hardware());
            assertEquals(1, screen.reads);
            assertNull(screen.scheduled);
            assertFalse(screen.reading);
            assertEquals(result.profile(), screen.vehicle);
        }
    }

    @Test public void retryFailureIsTerminalEvenWhenNotDisconnected() {
        FakeScreen screen = new FakeScreen();
        screen.start(VehicleReadResult.disconnected(), VehicleReadResult.unavailable());
        screen.scheduled.run();
        assertEquals(2, screen.reads);
        assertFalse(screen.reading);
        assertEquals(VehicleProfile.UNKNOWN, screen.vehicle);
    }

    @Test public void stoppedOrDestroyedSessionDiscardsTimersAndLateResults() {
        FakeScreen screen = new FakeScreen();
        screen.start(VehicleReadResult.disconnected(), hardware());
        int oldGeneration = screen.generation;
        Runnable oldTimer = screen.scheduled;
        screen.stop(); // onStop uses this invalidation; onDestroy repeats it.
        assertNull(screen.scheduled);
        oldTimer.run();
        screen.deliver(oldGeneration, 1, hardware(), null);
        screen.stop();
        oldTimer.run();
        assertEquals(1, screen.reads);
        assertEquals(VehicleProfile.UNKNOWN, screen.vehicle);
        assertFalse(screen.reading);
    }

    @Test public void reopenedSessionRejectsOldTimerAndBothOldAttemptResults() {
        FakeScreen screen = new FakeScreen();
        screen.start(VehicleReadResult.disconnected(), hardware());
        int oldGeneration = screen.generation;
        Runnable oldTimer = screen.scheduled;
        Runnable oldExpiry = screen.expiry;
        screen.stop();
        screen.start(VehicleReadResult.disconnected(), VehicleReadResult.disconnected());
        Runnable newTimer = screen.scheduled;
        oldExpiry.run();
        assertEquals(0, screen.expirations);
        oldTimer.run();
        screen.deliver(oldGeneration, 0, hardware(), null);
        screen.deliver(oldGeneration, 1, hardware(), null);
        assertSame(newTimer, screen.scheduled);
        assertEquals(2, screen.reads);
        assertTrue(screen.reading);
        newTimer.run();
        assertEquals(3, screen.reads);
        assertFalse(screen.reading);
        assertEquals(VehicleProfile.UNKNOWN, screen.vehicle);
    }

    @Test public void duplicateFirstResultCannotScheduleAnotherRetry() {
        VehicleReadResult.Session session = new VehicleReadResult.Session();
        session.start(7, 0);
        assertEquals(RETRY, session.accept(7, 0, VehicleReadResult.disconnected(), 100));
        assertEquals(IGNORE, session.accept(7, 0, hardware(), 100));
        assertEquals(COMPLETE, session.accept(7, 1, hardware(), 600));
        assertEquals(IGNORE, session.accept(7, 1, hardware(), 600));
    }

    @Test public void noResponseExpiresOnceAtFixedDeadlineAndLateResultsAreIgnored() {
        FakeScreen screen = new FakeScreen();
        screen.start(null, null); // Reader never calls back.
        assertEquals(3000, screen.session.deadline());
        screen.advance(2999);
        assertTrue(screen.reading);
        assertEquals(0, screen.expirations);
        screen.advance(3000);
        assertFalse(screen.reading);
        assertEquals(1, screen.expirations);
        assertNull(screen.vehicle.heightMm());
        assertNull(screen.vehicle.widthMm());
        assertEquals(VehicleProfile.DriveType.NEEDS_CHECK, screen.vehicle.drive());
        assertEquals(0, requestableCount(compare(SampleTrip.LOTS, screen.vehicle.heightMm(),
                screen.vehicle.drive(), 120, false)));
        screen.deliver(screen.generation, 0, hardware(), null);
        screen.deliver(screen.generation, 0, VehicleReadResult.unavailable(), null);
        screen.advance(5000);
        assertEquals(1, screen.expirations);
        assertEquals(VehicleProfile.UNKNOWN, screen.vehicle);
    }

    @Test public void retrySharesDeadlineWhetherFirstResponseIsEarlyOrLate() {
        for (long firstAt : new long[]{100, 2800}) {
            FakeScreen screen = new FakeScreen();
            screen.start(null, null);
            screen.now = firstAt;
            screen.deliver(screen.generation, 0, VehicleReadResult.disconnected(), null);
            Runnable retry = screen.scheduled;
            if (firstAt == 100) {
                retry.run();
                assertEquals(600, screen.now);
                assertEquals(2, screen.reads);
                screen.advance(2999);
                assertTrue(screen.reading);
            }
            assertEquals(3000, screen.session.deadline());
            screen.advance(3000);
            int reads = screen.reads;
            retry.run();
            assertEquals(firstAt == 100 ? 2 : 1, reads);
            assertEquals(reads, screen.reads);
            assertEquals(1, screen.expirations);
        }
    }

    @Test public void deadlineResultsExpireAndOldEventsCannotAffectReplacement() {
        for (VehicleReadResult result : new VehicleReadResult[]{hardware(), VehicleReadResult.unavailable()}) {
            FakeScreen screen = new FakeScreen();
            screen.start(null, null);
            int old = screen.generation;
            Runnable oldExpiry = screen.expiry;
            screen.now = 3000;
            screen.deliver(old, 0, result, null); // Result arrives before timer, at deadline.
            oldExpiry.run();
            assertEquals(1, screen.expirations);
            screen.stop();
            screen.start(VehicleReadResult.disconnected(), hardware());
            Runnable retry = screen.scheduled;
            oldExpiry.run();
            screen.deliver(old, 0, result, null);
            assertTrue(screen.reading);
            assertEquals(6000, screen.session.deadline());
            retry.run();
            assertEquals(hardware().profile(), screen.vehicle);
            screen.advance(6000);
            assertEquals(hardware().profile(), screen.vehicle);
            assertEquals(1, screen.expirations);
        }
    }

    @Test public void hardwareBeforeDeadlineKeepsRecommendationsAfterExpiryEvent() {
        for (int height : new int[]{1850, 2250}) {
            FakeScreen screen = new FakeScreen();
            screen.start(null, null);
            screen.now = 2999;
            VehicleReadResult result = VehicleReadResult.connected(VehicleProfile.fromHardware(
                    null, "synthetic AAOS", null, new Integer[]{height, 4700, 1990},
                    new Integer[]{FuelType.UNLEADED}));
            screen.deliver(screen.generation, 0, result, null);
            screen.advance(3000);
            assertFalse(screen.reading);
            assertEquals(0, screen.expirations);
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, screen.vehicle.drive());
            var options = compare(SampleTrip.LOTS, screen.vehicle.heightMm(), screen.vehicle.widthMm(),
                    screen.vehicle.drive(), 120, false, Priority.ATTACHED_FIRST);
            assertEquals(height == 1850 ? 2 : 1, requestableCount(options));
            assertEquals(Integer.valueOf(height == 1850 ? 12000 : 6000), options.get(0).expectedWon());
            assertEquals(height == 1850, options.get(0).lot().attached());
            if (height == 2250) assertEquals(Fit.RESTRICTED,
                    options.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow().fit());
        }
    }

    @Test public void expiredUnknownCanSupplementHeightAndOnlyConfirmRequestsEntrance() {
        FakeScreen screen = new FakeScreen();
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("original visit", SampleTrip.LOTS);
        screen.start(null, null);
        screen.advance(3000);
        VehicleHeight height = new VehicleHeight();
        assertTrue(height.apply(height.start(), "1850"));
        assertEquals(VehicleProfile.DriveType.NEEDS_CHECK, screen.vehicle.drive());
        var options = nav.options(height.effectiveMm(screen.vehicle), screen.vehicle.widthMm(),
                screen.vehicle.drive(), 120, false, Priority.ATTACHED_FIRST);
        assertEquals(1, requestableCount(options));
        assertEquals("대안 A · 시연 주차장", options.get(0).lot().name());
        assertEquals(Integer.valueOf(6000), options.get(0).expectedWon());
        var selection = nav.select(options.get(0)).orElseThrow();
        assertTrue(fake.requests.isEmpty());
        fake.result = NavigationPort.Result.REJECTED;
        assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(selection));
        assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
        assertEquals(1, fake.requests.size());
        assertEquals("original visit", fake.requests.get(0).visit());
        assertEquals("original visit", nav.visit().name());
        assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0.000001);
        assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0.000001);
    }
}
