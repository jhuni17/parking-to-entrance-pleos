package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

/** Synthetic AAOS → plan → user decision → confirmation → fake navigation. */
public final class VisitExclusionTest {
    private VehicleProfile hardware(int height) {
        return VehicleProfile.fromHardware(null, "Synthetic", null,
                new Integer[]{height, 4700, 1990}, new Integer[]{FuelType.UNLEADED});
    }
    private List<Option> options(ParkingNavigation nav, int height, Priority priority) {
        VehicleProfile v = hardware(height);
        assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, v.drive());
        return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, true, priority);
    }
    private Option lot(List<Option> options, int index) {
        return options.stream().filter(o -> o.lot() == SampleTrip.LOTS.get(index)).findFirst().orElseThrow();
    }
    @Test public void bothPrioritiesPreserveVerdictsAndOnlyNewConfirmationRequests() {
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            int[] changes = {0};
            ParkingNavigation nav = new ParkingNavigation(fake, () -> changes[0]++);
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            long version = nav.visitVersion();
            List<Option> initial = options(nav, 1850, priority);
            assertEquals(SampleTrip.LOTS.get(0), initial.get(0).lot());
            assertEquals(Integer.valueOf(0), initial.get(0).expectedWon());
            assertEquals(2, requestableCount(initial));
            var old = nav.select(initial.get(0)).orElseThrow();
            assertTrue(nav.setExcluded(version, SampleTrip.LOTS.get(0), true));
            assertEquals(2, changes[0]);
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old));
            assertTrue(nav.select(initial.get(0)).isEmpty()); // pre-exclusion Option cannot bypass state
            List<Option> excluded = options(nav, 1850, priority);
            assertEquals(SampleTrip.LOTS.get(1), excluded.get(0).lot());
            assertEquals(Integer.valueOf(6000), excluded.get(0).expectedWon());
            assertEquals(1, requestableCount(excluded));
            Option coex = lot(excluded, 0);
            assertTrue(coex.excluded());
            assertEquals(Fit.MATCHES_RECORDED_RULES, coex.fit());
            assertEquals(initial.get(0).reason(), coex.reason());
            assertEquals(Integer.valueOf(12000), coex.baseWon());
            assertEquals(Integer.valueOf(0), coex.expectedWon());
            assertEquals("이번 방문 제외", coex.requestAvailability().reason());
            assertTrue(routeRequest(SampleTrip.VISIT, coex).isEmpty());
            assertTrue(nav.select(coex).isEmpty());
            assertTrue(EntranceConfirmation.create(nav, coex, 120, true).isEmpty());
            assertEquals("코엑스 지하주차장 · 이번 방문 제외",
                    RecommendationExplanation.create(hardware(1850), new VehicleHeight(), false, excluded, priority).cardHeadline());
            var beforeRestore = nav.select(excluded.get(0)).orElseThrow();
            assertTrue(nav.setExcluded(version, coex.lot(), false));
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(beforeRestore));
            assertEquals(initial, options(nav, 1850, priority));
            assertTrue(nav.setExcluded(version, coex.lot(), true));
            var beforeRefresh = nav.select(options(nav, 1850, priority).get(0)).orElseThrow();
            nav.invalidateSelections();
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(beforeRefresh));
            nav.options(1850, 1990, hardware(1850).drive(), 60, false, priority);
            assertTrue(lot(options(nav, 1850, priority), 0).excluded());
            var preview = EntranceConfirmation.create(nav, options(nav, 1850, priority).get(0), 120, true).orElseThrow();
            assertTrue(fake.requests.isEmpty());
            fake.echo = true;
            assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(preview.selection()));
            fake.listener.accept(new NavigationPort.Destination("echo", "entrance", List.of(), fake.lastId));
            assertEquals(version, nav.visitVersion());
            assertTrue(lot(options(nav, 1850, priority), 0).excluded());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(preview.selection()));
            assertEquals(1, fake.requests.size());
            assertEquals(SampleTrip.VISIT, fake.requests.get(0).visit());
            assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0.0);
            assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0.0);
        }
    }
    @Test public void tallVehicleAndAllExcludedKeepRowsAndRestriction() {
        for (Priority priority : Priority.values()) {
            var fake = new ParkingNavigationTest.Fake();
            var nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            long version = nav.visitVersion();
            nav.setExcluded(version, SampleTrip.LOTS.get(1), true);
            assertEquals(1, requestableCount(options(nav, 1850, priority)));
            nav.invalidateSelections();
            var tall = options(nav, 2250, priority);
            assertEquals(3, tall.size());
            assertEquals(0, requestableCount(tall));
            assertEquals(Fit.RESTRICTED, lot(tall, 0).fit());
            nav.setExcluded(version, SampleTrip.LOTS.get(1), false);
            tall = options(nav, 2250, priority);
            assertEquals(1, requestableCount(tall));
            assertEquals(Integer.valueOf(6000), tall.get(0).expectedWon());
            assertEquals(Fit.RESTRICTED, lot(tall, 0).fit());
            nav.setExcluded(version, SampleTrip.LOTS.get(0), true);
            nav.setExcluded(version, SampleTrip.LOTS.get(1), true);
            var all = options(nav, 1850, priority);
            assertEquals(3, all.size());
            assertEquals(0, requestableCount(all));
            assertTrue(all.stream().allMatch(o -> nav.select(o).isEmpty()));
            assertTrue(fake.requests.isEmpty());
        }
    }
    @Test public void visitIdentityRejectsOldDialogsAndForeignFacilitiesAndStartsEmpty() {
        var fake = new ParkingNavigationTest.Fake();
        var nav = new ParkingNavigation(fake, () -> {});
        assertFalse(nav.setExcluded(0, SampleTrip.LOTS.get(0), true));
        fake.visit("same", SampleTrip.LOTS);
        long old = nav.visitVersion();
        nav.setExcluded(old, SampleTrip.LOTS.get(0), true);
        fake.visit("same", SampleTrip.LOTS); // same ID/name without correlation is a new visit
        assertEquals(2, requestableCount(options(nav, 1850, Priority.ATTACHED_FIRST)));
        assertFalse(nav.setExcluded(old, SampleTrip.LOTS.get(0), true));
        nav.setExcluded(nav.visitVersion(), SampleTrip.LOTS.get(0), true);
        assertFalse(nav.setExcluded(old, SampleTrip.LOTS.get(0), false));
        Lot original = SampleTrip.LOTS.get(0);
        Lot copy = new Lot(original.name(), original.type(), original.attached(), original.heightLimitMm(),
                original.evEntry(), original.tariff(), original.entrance(), original.provenance());
        assertFalse(nav.setExcluded(nav.visitVersion(), copy, true));
        assertTrue(lot(options(nav, 1850, Priority.FARE_FIRST), 0).excluded());
        var freshFake = new ParkingNavigationTest.Fake();
        var fresh = new ParkingNavigation(freshFake, () -> {});
        freshFake.visit("same", SampleTrip.LOTS);
        assertEquals(2, requestableCount(options(fresh, 1850, Priority.FARE_FIRST)));
        assertTrue(fake.requests.isEmpty());
    }
    @Test public void rejectionAndExceptionRemainFailuresAfterExclusion() {
        for (boolean exception : new boolean[]{false, true}) {
            var fake = new ParkingNavigationTest.Fake();
            fake.result = NavigationPort.Result.REJECTED;
            fake.throwsFailure = exception;
            var nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            nav.setExcluded(nav.visitVersion(), SampleTrip.LOTS.get(0), true);
            var selection = EntranceConfirmation.create(nav, options(nav, 1850, Priority.FARE_FIRST).get(0), 120, true).orElseThrow().selection();
            assertTrue(fake.requests.isEmpty());
            assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(selection));
            assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
            assertEquals(1, fake.requests.size());
        }
    }
}
