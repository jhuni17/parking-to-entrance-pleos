package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

/** Synthetic hardware inputs and fake navigation; no SDK runtime observation. */
public final class OptionGenerationTest {
    private VehicleProfile hardware(Integer height, int fuel) {
        return VehicleProfile.fromHardware(null, "Synthetic", null,
                new Integer[]{height, 4700, 1990}, new Integer[]{fuel});
    }
    private List<Option> options(ParkingNavigation nav, VehicleProfile v, Priority priority) {
        return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, true, priority);
    }
    private Option coex(List<Option> options) {
        return options.stream().filter(o -> o.lot() == SampleTrip.LOTS.get(0)).findFirst().orElseThrow();
    }
    private void blocked(ParkingNavigation nav, Option option) {
        assertTrue(nav.select(option).isEmpty());
        assertTrue(EntranceConfirmation.create(nav, option, 120, true).isEmpty());
    }

    @Test public void hardwareRefreshExpiresBothSelectionAndOptionBeforeRecalculation() {
        for (Priority priority : Priority.values()) {
            var fake = new ParkingNavigationTest.Fake();
            var nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            var initialVehicle = hardware(1850, FuelType.UNLEADED);
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, initialVehicle.drive());
            var initial = options(nav, initialVehicle, priority);
            Option old = coex(initial);
            assertSame(old, initial.get(0));
            assertEquals(Integer.valueOf(0), old.expectedWon());
            assertEquals(2, requestableCount(initial));
            var selection = nav.select(old).orElseThrow();
            nav.invalidateSelections();
            blocked(nav, old); // Must fail even before the next options call.
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
            assertEquals(0, fake.requests.size());

            var tallVehicle = hardware(2250, FuelType.UNLEADED);
            var tall = options(nav, tallVehicle, priority);
            assertEquals(Fit.RESTRICTED, coex(tall).fit());
            assertFalse(coex(tall).requestAvailability().available());
            blocked(nav, coex(tall));
            blocked(nav, old);
            assertSame(SampleTrip.LOTS.get(1), tall.get(0).lot());
            assertEquals(Integer.valueOf(6000), tall.get(0).expectedWon());
            assertEquals(1, requestableCount(tall));
            var explanation = RecommendationExplanation.create(tallVehicle, new VehicleHeight(), false, tall, priority);
            assertTrue(explanation.cardHeadline().contains("높이 초과"));
            var preview = EntranceConfirmation.create(nav, tall.get(0), 120, true).orElseThrow();
            assertTrue(preview.message().contains("120분 예상 6,000원"));
            assertEquals(0, fake.requests.size());
            fake.echo = true;
            long version = nav.visitVersion();
            var visit = nav.visit();
            assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(preview.selection()));
            fake.listener.accept(new NavigationPort.Destination("echo", "entrance", List.of(), fake.lastId));
            assertSame(visit, nav.visit());
            assertEquals(version, nav.visitVersion());
            assertTrue(nav.select(tall.get(0)).isPresent()); // echoes do not expire issuances
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(preview.selection()));
            assertEquals(1, fake.requests.size());
            assertEquals(SampleTrip.VISIT, fake.requests.get(0).visit());
            assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0.0);
            assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0.0);
        }
    }

    @Test public void electricAndMissingHeightCannotBeBypassedByOldOption() {
        for (Priority priority : Priority.values()) {
            for (VehicleProfile changed : new VehicleProfile[]{hardware(1850, FuelType.ELECTRIC), hardware(null, FuelType.UNLEADED)}) {
                var fake = new ParkingNavigationTest.Fake();
                var nav = new ParkingNavigation(fake, () -> {});
                fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
                Option old = coex(options(nav, hardware(1850, FuelType.UNLEADED), priority));
                nav.invalidateSelections();
                var current = options(nav, changed, priority);
                assertEquals(Fit.NEEDS_CHECK, coex(current).fit());
                assertFalse(coex(current).requestAvailability().available());
                assertEquals(changed.heightMm() == null ? 0 : 1, requestableCount(current));
                blocked(nav, old);
                blocked(nav, coex(current));
                if (changed.heightMm() == null) current.forEach(o -> blocked(nav, o));
                assertEquals(0, fake.requests.size());
            }
        }
    }

    @Test public void issuanceUsesIdentityOwnerAndGenerationAcrossAllOverloads() {
        var fake = new ParkingNavigationTest.Fake();
        var nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        Option old = nav.options(1850, VehicleProfile.DriveType.NON_ELECTRIC, 120, true).get(0);
        nav.invalidateSelections();
        Option current = nav.options(1850, VehicleProfile.DriveType.NON_ELECTRIC, 120, true, Priority.ATTACHED_FIRST).get(0);
        assertEquals(old, current);
        assertNotSame(old, current);
        blocked(nav, old);
        Option repeated = options(nav, hardware(1850, FuelType.UNLEADED), Priority.ATTACHED_FIRST).get(0);
        assertEquals(current, repeated);
        assertNotSame(current, repeated);
        assertTrue(nav.select(current).isPresent());
        assertTrue(nav.select(repeated).isPresent());
        blocked(nav, new Option(current.lot(), current.fit(), current.reason(), current.baseWon(), current.expectedWon(), current.excluded()));
        blocked(nav, compare(SampleTrip.LOTS, 1850, VehicleProfile.DriveType.NON_ELECTRIC, 120, true).get(0));
        var otherFake = new ParkingNavigationTest.Fake();
        var other = new ParkingNavigation(otherFake, () -> {});
        otherFake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        Option foreign = options(other, hardware(1850, FuelType.UNLEADED), Priority.ATTACHED_FIRST).get(0);
        assertEquals(current, foreign);
        blocked(nav, foreign);
        blocked(other, current);
        assertTrue(other.select(foreign).isPresent());
        blocked(nav, null);
        assertEquals(0, fake.requests.size());
        assertEquals(0, otherFake.requests.size());
    }

    @Test public void sameFacilityNewVisitAndExclusionRestoreExpireEarlierIssuances() {
        var fake = new ParkingNavigationTest.Fake();
        var nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        Option old = options(nav, hardware(1850, FuelType.UNLEADED), Priority.ATTACHED_FIRST).get(0);
        var selection = nav.select(old).orElseThrow();
        long previousVersion = nav.visitVersion();
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS); // exact same ID, name and Lot objects
        assertEquals(previousVersion + 1, nav.visitVersion());
        blocked(nav, old);
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
        Option fresh = options(nav, hardware(1850, FuelType.UNLEADED), Priority.ATTACHED_FIRST).get(0);
        assertEquals(old, fresh);
        assertTrue(nav.select(fresh).isPresent());
        assertTrue(nav.setExcluded(nav.visitVersion(), fresh.lot(), true));
        blocked(nav, fresh);
        var excluded = options(nav, hardware(1850, FuelType.UNLEADED), Priority.ATTACHED_FIRST);
        assertTrue(coex(excluded).excluded());
        Option alternative = excluded.get(0);
        var beforeRestore = nav.select(alternative).orElseThrow();
        assertTrue(nav.setExcluded(nav.visitVersion(), fresh.lot(), false));
        blocked(nav, alternative);
        blocked(nav, fresh); // restoring the lot cannot revive its earlier Option
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(beforeRestore));
        var restored = options(nav, hardware(1850, FuelType.UNLEADED), Priority.ATTACHED_FIRST);
        assertFalse(coex(restored).excluded());
        assertTrue(nav.select(coex(restored)).isPresent());
        assertEquals(0, fake.requests.size());
    }

    @Test public void freshAlternativeKeepsFailureAndSimulationOutcomesAndSingleConsumption() {
        for (boolean exception : new boolean[]{false, true}) {
            for (NavigationPort.Result result : NavigationPort.Result.values()) {
                var fake = new ParkingNavigationTest.Fake();
                fake.result = result;
                fake.throwsFailure = exception;
                var nav = new ParkingNavigation(fake, () -> {});
                fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
                Option old = options(nav, hardware(1850, FuelType.UNLEADED), Priority.ATTACHED_FIRST).get(0);
                nav.invalidateSelections();
                blocked(nav, old);
                var current = options(nav, hardware(2250, FuelType.UNLEADED), Priority.ATTACHED_FIRST);
                var preview = EntranceConfirmation.create(nav, current.get(0), 120, true).orElseThrow();
                assertEquals(0, fake.requests.size());
                var expected = exception ? ParkingNavigation.Status.REJECTED : ParkingNavigation.Status.valueOf(result.name());
                assertEquals(expected, nav.confirm(preview.selection()));
                assertEquals(expected, nav.status());
                assertEquals(expected, nav.lastRequest().outcome());
                assertEquals(ParkingNavigation.Status.STALE, nav.confirm(preview.selection()));
                assertEquals(1, fake.requests.size());
            }
        }
    }
}
