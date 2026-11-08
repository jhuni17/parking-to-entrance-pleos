package kr.juchakkaji.app;

import org.junit.Test;
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
        public void setDestinationListener(Consumer<Destination> listener) { this.listener = listener; }
        public Result requestEntrance(long id, ParkingPlan.RouteRequest request) {
            requests.add(request);
            lastId = id;
            if (echo) listener.accept(new Destination("entrance", request.entrance().label(), List.of(), id));
            if (throwsFailure) throw new IllegalStateException("fake failure");
            return result;
        }
        void visit(String id, List<ParkingPlan.Lot> lots) {
            listener.accept(new Destination(id, id, lots, null));
        }
    }
    private ParkingNavigation.Selection select(ParkingNavigation nav) {
        return nav.select(nav.options(1850, false, 120, true).get(0)).orElseThrow();
    }
    @Test public void newestVisitOwnsOptionsAndInvalidatesOldSelection() {
        Fake fake = new Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("first", SampleTrip.LOTS);
        ParkingNavigation.Selection old = select(nav);
        List<ParkingPlan.Lot> newest = List.of(SampleTrip.LOTS.get(1));
        fake.visit("second", newest);
        assertEquals("second", nav.visit().id());
        assertEquals(1, nav.options(1850, false, 120, true).size());
        assertEquals(newest.get(0), nav.options(1850, false, 120, true).get(0).lot());
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
        for (ParkingPlan.Option option : nav.options(2250, true, 120, true)) {
            if (option.fit() != ParkingPlan.Fit.MATCHES_RECORDED_RULES) assertTrue(nav.select(option).isEmpty());
        }
        assertTrue(fake.requests.isEmpty());
    }
}
