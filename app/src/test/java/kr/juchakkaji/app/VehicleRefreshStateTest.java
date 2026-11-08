package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static kr.juchakkaji.app.VehicleReadResult.Session.Action.*;
import static org.junit.Assert.*;

/** Synthetic reader/clock, using the Activity's exact state transitions without sleep. */
public final class VehicleRefreshStateTest {
    static VehicleReadResult hardware(int height, boolean electric) {
        return VehicleReadResult.connected(VehicleProfile.fromHardware(null, "Synthetic", null,
                new Integer[]{height, 4700, 1990}, new Integer[]{electric ? FuelType.ELECTRIC : FuelType.UNLEADED}));
    }
    static final class Screen {
        final VehicleRefreshState state = new VehicleRefreshState();
        final VehicleHeight height = new VehicleHeight();
        final VisitQuoteState quote = new VisitQuoteState(120);
        final ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        final ParkingNavigation nav = new ParkingNavigation(fake, this::refresh);
        Priority priority;
        long now;
        int reads;
        Runnable retry;
        Runnable expiry;
        Screen(Priority priority) {
            this.priority = priority;
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            quote.apply(quote.open(), 120, true);
            onStart();
        }
        void refresh() { quote.syncVisit(nav.visitVersion()); nav.invalidateSelections(); }
        void onStart() { height.start(); state.activate(); start(); }
        void stop() { height.stop(); state.stop(); }
        boolean start() {
            if (!state.start(now)) return false;
            refresh();
            int token = state.generation();
            expiry = () -> { if (state.expire(token, now) == EXPIRE) refresh(); };
            read(token, 0);
            return true;
        }
        void read(int token, int attempt) {
            if (state.expire(token, now) == EXPIRE) refresh();
            if (state.beginRead(token, attempt)) reads++;
        }
        void deliver(int token, int attempt, VehicleReadResult result) {
            var action = state.accept(token, attempt, result, now);
            if (action == RETRY) {
                long due = now + VehicleReadResult.Session.RETRY_DELAY_MS;
                retry = () -> { if (now >= due) read(token, 1); };
            } else if (action == COMPLETE || action == EXPIRE) refresh();
        }
        void complete(VehicleReadResult result) { deliver(state.generation(), 0, result); }
        List<Option> options() {
            var v = state.vehicle();
            return nav.options(height.effectiveMm(v), v.widthMm(), v.drive(), quote.stayMinutes(), quote.purchaseDiscount(), priority);
        }
        RecommendationExplanation explanation() {
            return RecommendationExplanation.create(state.vehicle(), height, state.reading(), options(), priority);
        }
        Option coex() { return options().stream().filter(o -> o.lot() == SampleTrip.LOTS.get(0)).findFirst().orElseThrow(); }
    }
    @Test public void unknownThenManualRecoveryPreservesVisitAndFareForBothPriorities() {
        for (Priority priority : Priority.values()) {
            var s = new Screen(priority);
            s.complete(VehicleReadResult.unavailable());
            assertEquals(0, requestableCount(s.options()));
            var visit = s.nav.visit();
            assertTrue(s.start());
            assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
            s.complete(hardware(1850, false));
            assertFalse(s.state.reading());
            assertEquals(2, requestableCount(s.options()));
            assertSame(SampleTrip.LOTS.get(0), s.options().get(0).lot());
            assertEquals(Integer.valueOf(0), s.options().get(0).expectedWon());
            assertSame(visit, s.nav.visit());
            assertEquals(120, s.quote.stayMinutes());
            assertTrue(s.quote.purchaseDiscount());
            assertNull(s.height.confirmedMm());
            assertTrue(s.explanation().vehicleDetails().contains("1850mm · 차량 속성값"));
            assertTrue(s.fake.requests.isEmpty());
        }
    }
    @Test public void changedHeightFuelAndSupplementRecomputeAndOnlyNewConfirmationRequests() {
        for (Priority priority : Priority.values()) for (int scenario = 0; scenario < 3; scenario++) {
            var s = new Screen(priority);
            s.complete(hardware(1850, false));
            var old = s.nav.select(s.options().get(0)).orElseThrow();
            if (scenario == 2) assertTrue(s.height.apply(s.height.session(), "2250"));
            assertTrue(s.start());
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(old));
            assertTrue(s.fake.requests.isEmpty());
            s.complete(hardware(scenario == 0 ? 2250 : 1850, scenario == 1));
            assertEquals(scenario == 1 ? Fit.NEEDS_CHECK : Fit.RESTRICTED, s.coex().fit());
            assertEquals(Integer.valueOf(scenario == 0 ? 2250 : 1850), s.state.vehicle().heightMm());
            assertEquals(Integer.valueOf(scenario == 1 ? 1850 : 2250), s.height.effectiveMm(s.state.vehicle()));
            assertEquals(1, requestableCount(s.options()));
            assertSame(SampleTrip.LOTS.get(1), s.options().get(0).lot());
            assertEquals(Integer.valueOf(6000), s.options().get(0).expectedWon());
            assertEquals("코엑스 지하주차장 · " + (scenario == 1 ? "전기차 입차 조건 미확인" : "높이 초과"), s.explanation().cardHeadline());
            if (scenario == 2) {
                assertTrue(s.explanation().vehicleDetails().contains("사용자 확인 총높이 2250mm"));
                assertTrue(s.explanation().vehicleDetails().contains("판단 적용 높이 2250mm"));
            }
            var preview = EntranceConfirmation.create(s.nav, s.options().get(0), s.quote.stayMinutes(), s.quote.purchaseDiscount()).orElseThrow();
            assertTrue(s.fake.requests.isEmpty());
            s.fake.echo = true;
            assertEquals(ParkingNavigation.Status.ACCEPTED, s.nav.confirm(preview.selection()));
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(preview.selection()));
            assertEquals(1, s.fake.requests.size());
            assertEquals(SampleTrip.VISIT, s.fake.requests.get(0).visit());
            assertEquals(37.51400, s.fake.requests.get(0).entrance().latitude(), 0);
            assertEquals(127.06100, s.fake.requests.get(0).entrance().longitude(), 0);
            assertEquals(SampleTrip.VISIT, s.nav.visit().name());
        }
    }
    @Test public void duplicateStartsFixedDeadlineRetryAndOldEventsAreRejected() {
        var s = new Screen(Priority.ATTACHED_FIRST);
        int old = s.state.generation();
        Runnable oldExpiry = s.expiry;
        assertFalse(s.start());
        assertEquals(old, s.state.generation());
        assertEquals(3000, s.state.deadline());
        assertEquals(1, s.reads);
        s.complete(VehicleReadResult.disconnected());
        Runnable oldRetry = s.retry;
        s.now = 499; oldRetry.run(); assertEquals(1, s.reads);
        s.now = 500; oldRetry.run(); oldRetry.run(); assertEquals(2, s.reads);
        s.deliver(old, 1, VehicleReadResult.disconnected());
        assertFalse(s.state.reading());
        assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
        s.now = 1000; assertTrue(s.start());
        int current = s.state.generation();
        assertEquals(4000, s.state.deadline());
        oldRetry.run(); oldExpiry.run(); s.deliver(old, 1, hardware(1850, false));
        assertEquals(current, s.state.generation());
        assertEquals(3, s.reads);
        s.now = 3999; s.expiry.run(); assertTrue(s.state.reading());
        s.now = 4000; s.complete(hardware(1850, false)); s.expiry.run();
        assertFalse(s.state.reading()); assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
        assertEquals(0, requestableCount(s.options()));
        assertTrue(s.start());
        current = s.state.generation();
        s.now = 6800; s.complete(VehicleReadResult.disconnected());
        var lateRetry = s.retry;
        s.now = 7000; s.expiry.run();
        s.now = 7300; lateRetry.run(); assertEquals(4, s.reads);
        s.deliver(current, 1, hardware(1850, false));
        assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
        assertTrue(s.start()); current = s.state.generation();
        s.stop(); s.complete(hardware(1850, false)); s.expiry.run();
        assertFalse(s.start()); assertFalse(s.state.reading());
        assertFalse(s.state.beginRead(current, 0));
        s.onStart(); s.deliver(current, 0, hardware(1850, false));
        assertTrue(s.state.reading()); assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
    }
    @Test public void manualRefreshPreservesInputsButNewScreenAndVisitKeepExistingResetPolicy() {
        for (Priority priority : Priority.values()) {
            var s = new Screen(priority);
            s.complete(hardware(1850, false));
            s.height.apply(s.height.session(), "1900");
            s.nav.setExcluded(s.nav.visitVersion(), SampleTrip.LOTS.get(0), true);
            s.start(); s.complete(hardware(1850, false));
            assertEquals(Integer.valueOf(1900), s.height.confirmedMm());
            assertEquals(priority, s.priority);
            assertEquals(120, s.quote.stayMinutes()); assertTrue(s.quote.purchaseDiscount());
            assertTrue(s.coex().excluded());
            assertSame(SampleTrip.LOTS.get(1), s.options().get(0).lot());
            assertEquals(Integer.valueOf(6000), s.options().get(0).expectedWon());
            assertTrue(s.explanation().cardHeadline().contains("이번 방문 제외"));
            s.stop(); s.onStart(); assertEquals(Integer.valueOf(1900), s.height.confirmedMm());
            assertEquals(priority, s.priority);
            assertEquals(120, s.quote.stayMinutes()); assertTrue(s.quote.purchaseDiscount());
            assertNull(new Screen(priority).height.confirmedMm());
            s.complete(hardware(1850, false)); assertTrue(s.coex().excluded());
            s.fake.visit("new visit", SampleTrip.LOTS);
            assertFalse(s.coex().excluded()); assertFalse(s.quote.purchaseDiscount());
            assertEquals(120, s.quote.stayMinutes());
        }
    }
    @Test public void exactInitialDeadlineAndRecoveryIgnoreOldExpiryAndDuplicateResults() {
        var s = new Screen(Priority.ATTACHED_FIRST);
        int old = s.state.generation();
        var oldExpiry = s.expiry;
        s.now = 2999; oldExpiry.run(); assertTrue(s.state.reading());
        s.now = 3000; oldExpiry.run(); assertFalse(s.state.reading());
        assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
        assertEquals(0, requestableCount(s.options()));
        assertTrue(s.start());
        s.complete(hardware(1850, false));
        var recovered = s.state.vehicle();
        s.deliver(old, 0, hardware(2250, false)); oldExpiry.run();
        s.complete(VehicleReadResult.disconnected());
        s.now = 6000; s.expiry.run();
        assertSame(recovered, s.state.vehicle());
        assertFalse(s.state.reading()); assertEquals(2, s.reads);
        assertNull(s.retry);
        assertEquals(2, requestableCount(s.options()));
    }
    @Test public void nonDefaultFareAssumptionsAndHeightSessionSurviveManualStart() {
        var s = new Screen(Priority.FARE_FIRST);
        s.complete(hardware(1850, false));
        assertTrue(s.quote.apply(s.quote.open(), 60, false));
        long heightSession = s.height.session();
        assertTrue(s.height.apply(heightSession, "2250"));
        s.start(); s.complete(hardware(1850, false));
        assertEquals(60, s.quote.stayMinutes()); assertFalse(s.quote.purchaseDiscount());
        assertEquals(Priority.FARE_FIRST, s.priority);
        assertEquals(heightSession, s.height.session());
        s.stop(); s.onStart(); s.complete(hardware(1850, false));
        assertNotEquals(heightSession, s.height.session());
        assertEquals(60, s.quote.stayMinutes()); assertFalse(s.quote.purchaseDiscount());
        assertEquals(Priority.FARE_FIRST, s.priority);
        assertEquals(Integer.valueOf(2250), s.height.confirmedMm());
        assertEquals(Integer.valueOf(3000), s.options().get(0).expectedWon());
        assertEquals(Fit.RESTRICTED, s.coex().fit());
        assertTrue(s.fake.requests.isEmpty());
    }
    @Test public void rejectedAndThrowingNavigationRemainFailuresAfterRefresh() {
        for (boolean throwsFailure : new boolean[]{false, true}) {
            var s = new Screen(Priority.FARE_FIRST);
            s.complete(hardware(1850, false));
            assertTrue(s.height.apply(s.height.session(), "2250"));
            s.refresh(); s.stop(); s.onStart(); s.complete(hardware(1850, false));
            var preview = EntranceConfirmation.create(s.nav, s.options().get(0), 120, true).orElseThrow();
            s.fake.result = NavigationPort.Result.REJECTED; s.fake.throwsFailure = throwsFailure;
            assertTrue(s.fake.requests.isEmpty());
            assertEquals(ParkingNavigation.Status.REJECTED, s.nav.confirm(preview.selection()));
            assertEquals(ParkingNavigation.Status.REJECTED, s.nav.lastRequest().outcome());
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(preview.selection()));
            assertEquals(1, s.fake.requests.size());
        }
    }
    @Test public void sameScreenReturnKeepsRoofHeightAcrossFreshReadFailureAndExpiry() {
        for (Priority priority : Priority.values()) for (int scenario = 0; scenario < 4; scenario++) {
            var s = new Screen(priority);
            s.complete(hardware(1850, false));
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, s.state.vehicle().drive());
            assertTrue(s.height.apply(s.height.session(), "2250"));
            s.refresh(); // MainActivity's successful height apply calls refresh.
            var oldOption = s.options().get(0);
            var oldSelection = s.nav.select(oldOption).orElseThrow();
            var visit = s.nav.visit();
            int oldRead = s.state.generation();
            Runnable oldExpiry = s.expiry;
            s.stop(); s.onStart();
            assertTrue(s.state.reading());
            assertTrue(s.nav.select(oldOption).isEmpty());
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(oldSelection));
            s.deliver(oldRead, 0, hardware(1000, true));
            oldExpiry.run();
            assertTrue(s.state.reading());
            assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
            if (scenario == 0) s.complete(hardware(1850, false));
            else if (scenario == 1) s.complete(hardware(2600, false));
            else if (scenario == 2) s.complete(VehicleReadResult.unavailable());
            else { s.now = 3000; s.expiry.run(); }
            var original = s.state.vehicle();
            assertFalse(s.state.reading());
            assertEquals(Integer.valueOf(2250), s.height.confirmedMm());
            assertEquals(Integer.valueOf(scenario == 1 ? 2600 : 2250), s.height.effectiveMm(original));
            assertEquals(scenario < 2 ? Integer.valueOf(scenario == 1 ? 2600 : 1850) : null, original.heightMm());
            assertEquals(scenario < 2 ? VehicleProfile.DriveType.NON_ELECTRIC : VehicleProfile.DriveType.NEEDS_CHECK, original.drive());
            assertEquals(Fit.RESTRICTED, s.coex().fit());
            assertFalse(s.coex().requestAvailability().available());
            assertTrue(s.nav.select(s.coex()).isEmpty());
            assertEquals(scenario == 1 ? 0 : 1, requestableCount(s.options()));
            String details = s.explanation().vehicleDetails();
            assertTrue(details.contains("사용자 확인 총높이 2250mm · 사용자 직접 확인값"));
            assertTrue(details.contains("판단 적용 높이 " + (scenario == 1 ? 2600 : 2250) + "mm"));
            if (scenario < 2) assertTrue(details.contains(original.heightMm() + "mm · 차량 속성값"));
            else {
                assertTrue(details.contains("차량 높이 확인 필요"));
                assertTrue(details.contains("연료 유형 확인 필요"));
            }
            s.now = 6000; oldExpiry.run();
            s.deliver(oldRead, 0, hardware(1000, true));
            assertSame(original, s.state.vehicle());
            assertSame(visit, s.nav.visit());
            assertEquals(priority, s.priority);
            assertEquals(120, s.quote.stayMinutes()); assertTrue(s.quote.purchaseDiscount());
            assertTrue(s.fake.requests.isEmpty());
            if (scenario == 1) continue;
            var alternative = s.options().get(0);
            assertSame(SampleTrip.LOTS.get(1), alternative.lot());
            assertEquals(Integer.valueOf(6000), alternative.expectedWon());
            var preview = EntranceConfirmation.create(s.nav, alternative, 120, true).orElseThrow();
            assertTrue(s.fake.requests.isEmpty());
            s.fake.result = NavigationPort.Result.SIMULATED;
            assertEquals(ParkingNavigation.Status.SIMULATED, s.nav.confirm(preview.selection()));
            assertEquals(ParkingNavigation.Status.SIMULATED, s.nav.lastRequest().outcome());
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(preview.selection()));
            assertEquals(1, s.fake.requests.size());
            assertEquals(SampleTrip.VISIT, s.fake.requests.get(0).visit());
            assertEquals(37.51400, s.fake.requests.get(0).entrance().latitude(), 0);
            assertEquals(127.06100, s.fake.requests.get(0).entrance().longitude(), 0);
            if (scenario == 0) {
                assertTrue(s.height.clear(s.height.session()));
                s.refresh();
                assertNull(s.height.confirmedMm());
                assertEquals(Integer.valueOf(1850), s.height.effectiveMm(original));
                assertEquals(2, requestableCount(s.options()));
                assertSame(SampleTrip.LOTS.get(0), s.options().get(0).lot());
                assertEquals(Integer.valueOf(0), s.options().get(0).expectedWon());
                assertEquals(1, s.fake.requests.size());
            }
        }
    }

    @Test public void returnRejectsPreviousDisconnectedRetryAndExpiry() {
        var s = new Screen(Priority.ATTACHED_FIRST);
        assertTrue(s.height.apply(s.height.session(), "2250")); s.refresh();
        int old = s.state.generation();
        s.complete(VehicleReadResult.disconnected());
        Runnable retry = s.retry;
        Runnable expiry = s.expiry;
        s.stop(); s.now = 1000; s.onStart();
        int current = s.state.generation();
        int reads = s.reads;
        retry.run();
        s.now = 3000; expiry.run();
        s.deliver(old, 1, hardware(1000, true));
        assertEquals(reads, s.reads);
        assertEquals(current, s.state.generation());
        assertTrue(s.state.reading());
        assertEquals(VehicleProfile.UNKNOWN, s.state.vehicle());
        assertEquals(Integer.valueOf(2250), s.height.confirmedMm());
        s.complete(hardware(1850, false));
        assertEquals(1, requestableCount(s.options()));
        assertTrue(s.fake.requests.isEmpty());
    }

}
