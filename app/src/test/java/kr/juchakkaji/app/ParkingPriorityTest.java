package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static kr.juchakkaji.app.ParkingPlan.*;

/** Synthetic AAOS inputs and fake navigation, never SDK/runtime observations. */
public final class ParkingPriorityTest {
    private VehicleProfile hardware(Integer height, boolean electric) {
        return VehicleProfile.fromHardware("SYNTHETIC", "fixture", 2026,
                new Integer[]{height, 4800, 1900},
                new Integer[]{electric ? FuelType.ELECTRIC : FuelType.UNLEADED});
    }
    private List<Option> options(ParkingNavigation nav, VehicleProfile vehicle, boolean discount, Priority priority) {
        return nav.options(vehicle.heightMm(), vehicle.drive(), 120, discount, priority);
    }
    private RecommendationExplanation explain(VehicleProfile vehicle, List<Option> options, Priority priority) {
        return RecommendationExplanation.create(vehicle, new VehicleHeight(), false, options, priority);
    }
    private void first(List<Option> options, int index, int won) {
        assertEquals(SampleTrip.LOTS.get(index), options.get(0).lot());
        assertEquals(Integer.valueOf(won), options.get(0).expectedWon());
        assertTrue(options.get(0).requestAvailability().available());
    }
    @Test public void switchingPriorityExpiresSelectionAndOnlyNewConfirmationRequestsEntrance() {
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        fake.echo = true;
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        VehicleProfile vehicle = hardware(1850, false);
        assertEquals(Integer.valueOf(1850), vehicle.heightMm());
        assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, vehicle.drive());
        List<Option> defaults = nav.options(vehicle.heightMm(), vehicle.drive(), 120, false);
        first(defaults, 0, 12000);
        assertEquals(defaults, ParkingPlan.compare(SampleTrip.LOTS, vehicle.heightMm(), vehicle.drive(), 120, false));
        assertEquals(defaults, options(nav, vehicle, false, Priority.ATTACHED_FIRST));
        assertEquals(Integer.valueOf(6000), defaults.get(1).expectedWon());
        ParkingNavigation.Selection old = nav.select(defaults.get(0)).orElseThrow();
        nav.invalidateSelections(); // Same refresh boundary used by the detail listener.
        List<Option> fare = options(nav, vehicle, false, Priority.FARE_FIRST);
        first(fare, 1, 6000);
        assertEquals(Integer.valueOf(12000), fare.get(1).expectedWon());
        assertEquals(2, requestableCount(fare));
        String reason = explain(vehicle, fare, Priority.FARE_FIRST).headline();
        assertTrue(reason.contains("요금 우선"));
        assertFalse(reason.contains("제한"));
        assertFalse(reason.contains("코엑스"));
        assertTrue(explain(vehicle, fare, Priority.FARE_FIRST).vehicleDetails().contains("추천 기준 요금 우선"));
        first(options(nav, vehicle, false, Priority.ATTACHED_FIRST), 0, 12000);
        assertEquals(0, fake.requests.size());
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old));
        assertEquals(0, fake.requests.size());
        ParkingNavigation.Selection fresh = nav.select(fare.get(0)).orElseThrow();
        assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(fresh));
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(fresh));
        assertEquals(1, fake.requests.size());
        RouteRequest request = fake.requests.get(0);
        assertEquals(SampleTrip.VISIT, request.visit());
        assertEquals(37.51400, request.entrance().latitude(), 0.0);
        assertEquals(127.06100, request.entrance().longitude(), 0.0);
        assertEquals(SampleTrip.VISIT, nav.visit().name());
        fake.result = NavigationPort.Result.REJECTED;
        assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(nav.select(fare.get(0)).orElseThrow()));
        assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
        assertEquals(2, fake.requests.size());
    }
    @Test public void discountsHeightFuelAndMissingHeightFlowFromHardwareToExplanation() {
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        VehicleProfile normal = hardware(1850, false);
        List<Option> discounted = options(nav, normal, true, Priority.FARE_FIRST);
        first(discounted, 0, 0);
        assertEquals(Integer.valueOf(12000), discounted.get(0).baseWon());
        assertEquals("", explain(normal, discounted, Priority.FARE_FIRST).headline());
        VehicleProfile tall = hardware(2250, false);
        List<Option> limited = options(nav, tall, true, Priority.FARE_FIRST);
        first(limited, 1, 6000);
        Option coex = limited.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
        assertEquals(Fit.RESTRICTED, coex.fit());
        assertFalse(coex.requestAvailability().available());
        assertTrue(nav.select(coex).isEmpty());
        String reason = explain(tall, limited, Priority.FARE_FIRST).headline();
        assertTrue(reason.contains("높이 초과"));
        assertFalse(reason.contains("요금 우선"));
        List<Option> electric = options(nav, hardware(1850, true), true, Priority.FARE_FIRST);
        Option electricCoex = electric.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
        assertEquals(Fit.NEEDS_CHECK, electricCoex.fit());
        assertFalse(electricCoex.requestAvailability().available());
        assertTrue(nav.select(electricCoex).isEmpty());
        List<Option> missing = options(nav, hardware(null, false), true, Priority.FARE_FIRST);
        assertEquals(0, requestableCount(missing));
        for (Option option : missing) assertTrue(nav.select(option).isEmpty());
        assertEquals(0, fake.requests.size());
        assertEquals("", explain(normal, List.of(), Priority.FARE_FIRST).headline());
        assertEquals("", RecommendationExplanation.create(normal, new VehicleHeight(), true,
                limited, Priority.FARE_FIRST).headline());
        assertEquals("", explain(normal, options(nav, normal, false, Priority.ATTACHED_FIRST),
                Priority.ATTACHED_FIRST).headline());
    }
    private Lot lot(String name, boolean attached, Integer price, Integer height, EvEntry ev, boolean entrance) {
        return new Lot(name, "synthetic", attached, height, ev,
                price == null ? null : new Tariff(120, price, 0, ""),
                entrance ? new Entrance(name, 37, 127) : null, "synthetic test input");
    }
    @Test public void fareSortUsesAvailabilityNullLastZeroAndStableAttachedTies() {
        Lot unknown = lot("unknown", false, null, 2500, EvEntry.ALLOWED, true);
        Lot paid = lot("paid", false, 6000, 2500, EvEntry.ALLOWED, true);
        Lot tie = lot("tie", false, 6000, 2500, EvEntry.ALLOWED, true);
        Lot attached = lot("attached", true, 6000, 2500, EvEntry.ALLOWED, true);
        Lot free = lot("free", false, 0, 2500, EvEntry.ALLOWED, true);
        Lot noEntrance = lot("no entrance", true, 0, 2500, EvEntry.ALLOWED, false);
        Lot restricted = lot("restricted", true, 0, 1800, EvEntry.ALLOWED, true);
        Lot check = lot("check", false, 0, null, EvEntry.UNKNOWN, true);
        List<Lot> lots = List.of(unknown, paid, tie, attached, free, noEntrance, restricted, check);
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("synthetic", lots);
        VehicleProfile vehicle = hardware(1850, false);
        List<Option> fare = options(nav, vehicle, false, Priority.FARE_FIRST);
        assertEquals(List.of(free, attached, paid, tie, unknown, noEntrance, check, restricted),
                fare.stream().map(Option::lot).toList());
        assertEquals(Integer.valueOf(0), fare.get(0).expectedWon());
        assertNull(fare.get(4).expectedWon());
        assertEquals(5, requestableCount(fare));
        List<Option> legacy = options(nav, vehicle, false, Priority.ATTACHED_FIRST);
        assertEquals(legacy.stream().filter(o -> !o.requestAvailability().available()).toList(),
                fare.stream().filter(o -> !o.requestAvailability().available()).toList());
        for (Option option : fare.subList(5, 8)) {
            assertFalse(option.requestAvailability().available());
            assertTrue(nav.select(option).isEmpty());
        }
        // Deliberately supply a contradictory vehicle: presentation must trust computed verdicts/order.
        assertTrue(explain(hardware(3000, true), fare, Priority.FARE_FIRST).headline().contains("요금 우선"));
        assertEquals(0, fake.requests.size());
    }
}
