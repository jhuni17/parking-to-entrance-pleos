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
        int generation;
        int reads;
        boolean reading;
        VehicleProfile vehicle = VehicleProfile.UNKNOWN;
        Runnable scheduled;

        void start(VehicleReadResult first, VehicleReadResult second) {
            int token = ++generation;
            session.start(token);
            vehicle = VehicleProfile.UNKNOWN;
            reading = true;
            scheduled = null;
            read(token, 0, first, second);
        }

        void read(int token, int attempt, VehicleReadResult result, VehicleReadResult second) {
            if (!session.isCurrent(token)) return;
            reads++;
            deliver(token, attempt, result, second);
        }

        void deliver(int token, int attempt, VehicleReadResult result, VehicleReadResult second) {
            switch (session.accept(token, attempt, result)) {
                case RETRY -> scheduled = () -> read(token, 1, second, null);
                case COMPLETE -> {
                    vehicle = result.profile();
                    reading = false;
                }
                case IGNORE -> { }
            }
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
        screen.stop();
        screen.start(VehicleReadResult.disconnected(), VehicleReadResult.disconnected());
        Runnable newTimer = screen.scheduled;
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
        session.start(7);
        assertEquals(RETRY, session.accept(7, 0, VehicleReadResult.disconnected()));
        assertEquals(IGNORE, session.accept(7, 0, hardware()));
        assertEquals(COMPLETE, session.accept(7, 1, hardware()));
        assertEquals(IGNORE, session.accept(7, 1, hardware()));
    }
}
