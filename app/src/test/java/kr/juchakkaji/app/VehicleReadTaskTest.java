package kr.juchakkaji.app;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import static kr.juchakkaji.app.ParkingPlan.*;
import static kr.juchakkaji.app.VehicleReadResult.Session.Action.*;
import static org.junit.Assert.*;

/** Deterministic single worker queue; counts are at reader entry, never submission. */
public final class VehicleReadTaskTest {
    @Test public void exactDeadlineCancellationAndRepeatedExecution() {
        for (long start : new long[]{2999, 3000, 3001}) for (boolean cancel : new boolean[]{false, true}) {
            int[] calls = {0};
            int[] completions = {0};
            var task = new VehicleReadTask(() -> { calls[0]++; return VehicleReadResult.unavailable(); },
                    result -> completions[0]++, () -> start, 3000);
            if (cancel) task.cancel();
            task.run(); task.run(); task.cancel(); task.run();
            assertEquals(!cancel && start == 2999 ? 1 : 0, calls[0]);
            assertEquals(calls[0], completions[0]);
        }
    }

    static final class Screen {
        final VehicleRefreshState state = new VehicleRefreshState();
        final ArrayDeque<Runnable> worker = new ArrayDeque<>();
        final ArrayDeque<Runnable> main = new ArrayDeque<>();
        final List<Integer> entered = new ArrayList<>();
        final ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        final ParkingNavigation nav = new ParkingNavigation(fake, this::refresh);
        final Priority priority;
        long now;
        long retryAt;
        Runnable retry;
        VehicleReadTask task;
        Screen(Priority priority) {
            this.priority = priority;
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            state.activate();
        }
        void refresh() { nav.invalidateSelections(); }
        void cancel() { if (task != null) task.cancel(); task = null; }
        void finish() { cancel(); refresh(); }
        void expire() { if (state.expire(state.generation(), now) == EXPIRE) finish(); }
        void stop() { cancel(); state.stop(); }
        void start(Supplier<VehicleReadResult> reader) {
            assertTrue(state.start(now)); cancel(); refresh();
            read(0, reader);
        }
        void read(int attempt, Supplier<VehicleReadResult> reader) {
            int token = state.generation();
            if (state.expire(token, now) == EXPIRE) { finish(); return; }
            if (!state.beginRead(token, attempt)) return;
            cancel();
            task = new VehicleReadTask(() -> {
                entered.add(token);
                return reader.get();
            }, result -> main.add(() -> {
                var action = state.accept(token, attempt, result, now);
                if (action == RETRY) {
                    retryAt = now + 500;
                    retry = () -> { if (now >= retryAt) read(1, reader); };
                } else if (action == COMPLETE || action == EXPIRE) finish();
            }), () -> now, state.deadline());
            worker.add(task);
        }
        void drainMain() { while (!main.isEmpty()) main.remove().run(); }
        List<Option> options() {
            var v = state.vehicle();
            return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, true, priority);
        }
    }

    @Test public void slowReadSkipsExpiredMiddleAndLatestRecoversRecommendations() {
        for (Priority priority : Priority.values()) {
            var s = new Screen(priority);
            s.start(() -> VehicleRefreshStateTest.hardware(1850, false));
            s.worker.remove().run(); s.drainMain();
            assertEquals(Integer.valueOf(0), s.options().get(0).expectedWon());
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, s.state.vehicle().drive());
            var old = EntranceConfirmation.create(s.nav, s.options().get(0), 120, true).orElseThrow();
            var visit = s.nav.visit();
            s.now = 100;
            s.start(() -> {
                // This reader owns the only worker until 6200. Main events can still run.
                assertEquals(3100, s.state.deadline());
                s.now = 3100; s.expire();
                s.start(() -> { fail("expired middle reader entered"); return null; });
                assertEquals(3, s.state.generation());
                assertEquals(6100, s.state.deadline());
                s.now = 6100; s.expire();
                s.start(() -> { s.now = 6300; return VehicleRefreshStateTest.hardware(2250, false); });
                assertEquals(4, s.state.generation());
                assertEquals(9100, s.state.deadline());
                s.now = 6200;
                return VehicleRefreshStateTest.hardware(1850, false);
            });
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(old.selection()));
            s.worker.remove().run(); s.drainMain();
            assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
            assertTrue(s.state.reading());
            s.worker.remove().run(); // expired middle: no reader, no completion
            assertTrue(s.main.isEmpty());
            assertEquals(List.of(1, 2), s.entered);
            s.worker.remove().run(); s.drainMain();
            assertEquals(List.of(1, 2, 4), s.entered);
            assertEquals(6300, s.now);
            assertEquals(9100, s.state.deadline());
            assertEquals(4, s.state.generation());
            assertEquals(VehicleRefreshState.Outcome.CONNECTED, s.state.outcome());
            assertEquals(Integer.valueOf(2250), s.state.vehicle().heightMm());
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, s.state.vehicle().drive());
            assertSame(visit, s.nav.visit());
            assertEquals(Fit.RESTRICTED, s.options().stream().filter(o -> o.lot() == SampleTrip.LOTS.get(0)).findFirst().orElseThrow().fit());
            assertEquals(1, requestableCount(s.options()));
            assertSame(SampleTrip.LOTS.get(1), s.options().get(0).lot());
            assertEquals(Integer.valueOf(6000), s.options().get(0).expectedWon());
            var preview = EntranceConfirmation.create(s.nav, s.options().get(0), 120, true).orElseThrow();
            assertTrue(s.fake.requests.isEmpty());
            s.fake.echo = true;
            assertEquals(ParkingNavigation.Status.ACCEPTED, s.nav.confirm(preview.selection()));
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(preview.selection()));
            assertEquals(1, s.fake.requests.size());
            assertEquals(SampleTrip.VISIT, s.fake.requests.get(0).visit());
            assertEquals(37.51400, s.fake.requests.get(0).entrance().latitude(), 0);
            assertEquals(127.06100, s.fake.requests.get(0).entrance().longitude(), 0);
            assertSame(visit, s.nav.visit());
        }
    }

    @Test public void stopCancelsQueuedReadAndLateStartedResultCannotOverwriteReturn() {
        var s = new Screen(Priority.ATTACHED_FIRST);
        s.start(() -> { fail("stopped queued reader entered"); return null; });
        s.stop(); s.worker.remove().run();
        assertTrue(s.entered.isEmpty()); assertTrue(s.main.isEmpty());
        s.state.activate();
        s.start(() -> {
            s.stop(); s.now = 100; s.state.activate();
            s.start(() -> VehicleRefreshStateTest.hardware(2250, false));
            return VehicleRefreshStateTest.hardware(1850, false);
        });
        s.worker.remove().run(); // late completion waits in main queue
        s.worker.remove().run(); s.drainMain();
        assertEquals(List.of(2, 3), s.entered);
        assertEquals(Integer.valueOf(2250), s.state.vehicle().heightMm());
        assertEquals(3, s.state.generation());
    }

    @Test public void queuedRetryExpiresWithoutEnteringReaderAndKeepsFixedDeadline() {
        var s = new Screen(Priority.ATTACHED_FIRST);
        s.start(VehicleReadResult::disconnected);
        s.worker.remove().run(); s.drainMain();
        assertEquals(500, s.retryAt);
        s.now = 499; s.retry.run(); assertTrue(s.worker.isEmpty());
        s.now = 500; s.retry.run(); s.retry.run();
        assertEquals(1, s.worker.size()); assertEquals(3000, s.state.deadline());
        s.now = 3000; s.worker.remove().run(); // even before expiry timer delivery
        assertEquals(List.of(1), s.entered); assertTrue(s.main.isEmpty());
        s.expire();
        assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
        assertEquals(VehicleRefreshState.Outcome.EXPIRED, s.state.outcome());
        assertFalse(s.state.reading());
    }

    @Test public void onlyFirstDisconnectedRetriesAndTerminalResultsDoNotRetry() {
        for (VehicleReadResult result : new VehicleReadResult[]{VehicleReadResult.disconnected(),
                VehicleReadResult.unavailable(), VehicleRefreshStateTest.hardware(1850, false)}) {
            var s = new Screen(Priority.FARE_FIRST);
            s.start(() -> result); s.worker.remove().run(); s.drainMain();
            if (result.state() == VehicleReadResult.State.DISCONNECTED) {
                s.now = 500; s.retry.run(); s.worker.remove().run(); s.drainMain();
                s.retry.run(); assertTrue(s.worker.isEmpty());
                assertEquals(List.of(1, 1), s.entered);
            } else { assertNull(s.retry); assertEquals(List.of(1), s.entered); }
            assertEquals(3000, s.state.deadline()); assertFalse(s.state.reading());
        }
    }
}
