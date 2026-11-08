package kr.juchakkaji.app;

import org.junit.Test;

import static kr.juchakkaji.app.VehicleProfile.DriveType.ELECTRIC;
import static kr.juchakkaji.app.VehicleProfile.DriveType.NON_ELECTRIC;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public final class ParkingNavigationTest {
    static final class Fake implements NavigationPort {
        Consumer<Destination> listener;
        List<ParkingPlan.RouteRequest> requests = new ArrayList<>();
        Result result = Result.ACCEPTED;
        long lastId;
        boolean echo;
        boolean throwsFailure;
        Runnable duringRequest = () -> {};
        public void setDestinationListener(Consumer<Destination> listener) { this.listener = listener; }
        public Result requestEntrance(long id, ParkingPlan.RouteRequest request) {
            requests.add(request);
            lastId = id;
            if (echo) listener.accept(new Destination("entrance", request.entrance().label(), List.of(), id));
            duringRequest.run();
            if (throwsFailure) throw new IllegalStateException("fake failure");
            return result;
        }
        void visit(String id, List<ParkingPlan.Lot> lots) {
            listener.accept(new Destination(id, id, lots, null));
        }
    }
    private ParkingNavigation.Selection select(ParkingNavigation nav) {
        return nav.select(nav.options(1850, NON_ELECTRIC, 120, true).get(0)).orElseThrow();
    }
    @Test public void newestVisitOwnsOptionsAndInvalidatesOldSelection() {
        Fake fake = new Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("first", SampleTrip.LOTS);
        ParkingNavigation.Selection old = select(nav);
        List<ParkingPlan.Lot> newest = List.of(SampleTrip.LOTS.get(1));
        fake.visit("second", newest);
        assertEquals("second", nav.visit().id());
        assertEquals(1, nav.options(1850, NON_ELECTRIC, 120, true).size());
        assertEquals(newest.get(0), nav.options(1850, NON_ELECTRIC, 120, true).get(0).lot());
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old));
        assertTrue(fake.requests.isEmpty());
    }
    @Test public void confirmationAloneSendsExactlyOnceWithChosenCoordinates() {
        Fake fake = new Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("original", SampleTrip.LOTS);
        ParkingNavigation.Selection selection = select(nav);
        assertTrue(fake.requests.isEmpty());
        assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(selection));
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
        assertEquals(1, fake.requests.size());
        assertEquals("original", fake.requests.get(0).visit());
        assertEquals(SampleTrip.LOTS.get(0).entrance(), fake.requests.get(0).entrance());
    }
    @Test public void rejectionSimulationAndExceptionAreNotSuccess() {
        for (NavigationPort.Result result : NavigationPort.Result.values()) {
            Fake fake = new Fake();
            fake.result = result;
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit("visit", SampleTrip.LOTS);
            nav.confirm(select(nav));
            assertEquals(ParkingNavigation.Status.valueOf(result.name()), nav.status());
        }
        Fake fake = new Fake();
        fake.throwsFailure = true;
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("visit", SampleTrip.LOTS);
        nav.confirm(select(nav));
        assertEquals(ParkingNavigation.Status.REJECTED, nav.status());
    }
    @Test public void synchronousAndDelayedEntranceEchoPreserveVisitButNewVisitReplacesIt() {
        Fake fake = new Fake();
        fake.echo = true;
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("original", SampleTrip.LOTS);
        NavigationPort.Destination original = nav.visit();
        nav.confirm(select(nav));
        assertSame(original, nav.visit());
        fake.listener.accept(new NavigationPort.Destination("echo", "entrance", List.of(), fake.lastId));
        assertSame(original, nav.visit());
        fake.visit("entrance", List.of(SampleTrip.LOTS.get(1)));
        assertEquals("entrance", nav.visit().id());
        assertEquals(ParkingNavigation.Status.IDLE, nav.status());
        fake.listener.accept(new NavigationPort.Destination("old echo", "entrance", List.of(), fake.lastId));
        assertEquals("entrance", nav.visit().id());
    }
    @Test public void changedVehicleConditionsInvalidateOpenConfirmation() {
        Fake fake = new Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("visit", SampleTrip.LOTS);
        ParkingNavigation.Selection selection = select(nav);
        nav.invalidateSelections();
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
        assertTrue(fake.requests.isEmpty());
    }
    @Test public void restrictedAndUnknownOptionsCannotBeSelected() {
        Fake fake = new Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("visit", SampleTrip.LOTS);
        for (ParkingPlan.Option option : nav.options(2250, ELECTRIC, 120, true)) {
            if (option.fit() != ParkingPlan.Fit.MATCHES_RECORDED_RULES) assertTrue(nav.select(option).isEmpty());
        }
        assertTrue(fake.requests.isEmpty());
    }
    @Test public void simulatedAlternativeReceiptStartsEmptyAndSurvivesConditionChanges() {
        Fake fake = new Fake();
        fake.result = NavigationPort.Result.SIMULATED;
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        assertNull(nav.lastRequest());
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        ParkingPlan.Option alternative = nav.options(2250, NON_ELECTRIC, 120, true).get(0);
        assertEquals(SampleTrip.LOTS.get(1), alternative.lot());
        ParkingNavigation.Selection selection = nav.select(alternative).orElseThrow();
        assertNull(nav.lastRequest());
        assertEquals(0, fake.requests.size());
        assertEquals(ParkingNavigation.Status.SIMULATED, nav.confirm(selection));
        ParkingNavigation.Receipt receipt = nav.lastRequest();
        assertSame(selection.request(), receipt.request());
        assertEquals("스타필드 코엑스몰", receipt.request().visit());
        assertEquals("대안 A 입구 (시연 좌표)", receipt.request().entrance().label());
        assertEquals(37.51400, receipt.request().entrance().latitude(), 0.0);
        assertEquals(127.06100, receipt.request().entrance().longitude(), 0.0);
        assertEquals(ParkingNavigation.Status.SIMULATED, receipt.outcome());
        assertEquals(1, fake.requests.size());
        nav.invalidateSelections();
        nav.options(1850, ELECTRIC, 60, false);
        assertSame(receipt, nav.lastRequest());
    }

    private void assertFailedReplacement(boolean throwsFailure) {
        Fake fake = new Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        ParkingNavigation.Selection first = select(nav);
        nav.confirm(first);
        ParkingNavigation.Receipt previous = nav.lastRequest();
        assertEquals(ParkingNavigation.Status.ACCEPTED, previous.outcome());
        ParkingNavigation.Selection next = nav.select(nav.options(2250, NON_ELECTRIC, 120, true).get(0)).orElseThrow();
        assertSame(previous, nav.lastRequest());
        fake.result = NavigationPort.Result.REJECTED;
        fake.throwsFailure = throwsFailure;
        assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(next));
        ParkingNavigation.Receipt failed = nav.lastRequest();
        assertNotSame(previous, failed);
        assertSame(next.request(), failed.request());
        assertEquals(SampleTrip.VISIT, failed.request().visit());
        assertEquals(SampleTrip.LOTS.get(1).entrance(), failed.request().entrance());
        assertEquals(ParkingNavigation.Status.REJECTED, failed.outcome());
        assertEquals(2, fake.requests.size());
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(next));
        assertSame(failed, nav.lastRequest());
        assertEquals(2, fake.requests.size());
    }
    @Test public void rejectionReplacesPreviousReceiptAndDuplicateDoesNothing() {
        assertFailedReplacement(false);
    }
    @Test public void exceptionReplacesPreviousReceiptAndDuplicateDoesNothing() {
        assertFailedReplacement(true);
    }
    @Test public void echoesKeepReceiptAndNewVisitClearsIt() {
        Fake fake = new Fake();
        fake.echo = true;
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        NavigationPort.Destination original = nav.visit();
        ParkingNavigation.Selection selection = select(nav);
        nav.confirm(selection);
        ParkingNavigation.Receipt receipt = nav.lastRequest();
        assertSame(original, nav.visit());
        assertSame(selection.request(), receipt.request());
        assertEquals(ParkingNavigation.Status.ACCEPTED, receipt.outcome());
        fake.listener.accept(new NavigationPort.Destination("echo", "entrance", List.of(), fake.lastId));
        assertSame(original, nav.visit());
        assertSame(receipt, nav.lastRequest());
        fake.visit("new visit", SampleTrip.LOTS);
        assertNull(nav.lastRequest());
        assertEquals(ParkingNavigation.Status.IDLE, nav.status());
        assertEquals(1, fake.requests.size());
        fake.listener.accept(new NavigationPort.Destination("late echo", "entrance", List.of(), fake.lastId));
        assertEquals("new visit", nav.visit().name());
        assertNull(nav.lastRequest());
    }
    @Test public void visitDuringRequestCannotReceiveOldOutcome() {
        Fake fake = new Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("original", SampleTrip.LOTS);
        nav.confirm(select(nav));
        assertNotNull(nav.lastRequest());
        fake.duringRequest = () -> {
            fake.visit("new visit", SampleTrip.LOTS);
            assertNull(nav.lastRequest());
        };
        assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(select(nav)));
        assertEquals("new visit", nav.visit().name());
        assertEquals(ParkingNavigation.Status.IDLE, nav.status());
        assertNull(nav.lastRequest());
        assertEquals(2, fake.requests.size());
        assertEquals("original", fake.requests.get(1).visit());
    }

}
