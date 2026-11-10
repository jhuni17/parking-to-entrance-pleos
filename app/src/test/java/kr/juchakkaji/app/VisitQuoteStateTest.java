package kr.juchakkaji.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static kr.juchakkaji.app.VehicleProfile.DriveType.NON_ELECTRIC;

public final class VisitQuoteStateTest {
    private final ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
    private final VisitQuoteState quote = new VisitQuoteState(120);
    private final ParkingNavigation nav = new ParkingNavigation(fake, this::refresh);
    private final VehicleProfile vehicle = new VehicleProfile("synthetic AAOS", 1850, 1900, NON_ELECTRIC, false);

    private void refresh() {
        quote.syncVisit(nav.visitVersion());
        nav.invalidateSelections();
    }
    private List<ParkingPlan.Option> options(VehicleProfile profile) {
        return nav.options(profile.heightMm(), profile.drive(), quote.stayMinutes(), quote.purchaseDiscount());
    }
    private ParkingPlan.Option coex() { return options(vehicle).get(0); }
    private void discount() { assertTrue(quote.apply(quote.open(), 120, true)); refresh(); }
    private void assertFullPrice() {
        assertFalse(quote.purchaseDiscount());
        assertEquals(120, quote.stayMinutes());
        assertEquals(Integer.valueOf(12000), coex().expectedWon());
        assertEquals(Integer.valueOf(6000), options(vehicle).get(1).expectedWon());
    }

    @Test public void consecutiveVisitsResetDiscountAndExpireSelectionWithoutRequesting() {
        fake.visit("A", SampleTrip.LOTS);
        assertEquals(1, nav.visitVersion());
        discount();
        assertEquals(Integer.valueOf(0), coex().expectedWon());
        ParkingNavigation.Selection old = nav.select(coex()).orElseThrow();
        VisitQuoteState.Token oldWindow = quote.open();
        fake.visit("B", SampleTrip.LOTS);
        assertEquals(2, nav.visitVersion());
        assertFullPrice();
        assertFalse(quote.apply(oldWindow, 60, true));
        assertFullPrice();
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old));
        assertEquals(0, fake.requests.size());
        ParkingNavigation.Selection current = nav.select(coex()).orElseThrow();
        assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(current));
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(current));
        assertEquals(1, fake.requests.size());
        assertEquals("B", fake.requests.get(0).visit());
        assertEquals(37.51200, fake.requests.get(0).entrance().latitude(), 0.0);
        assertEquals(127.06000, fake.requests.get(0).entrance().longitude(), 0.0);
        assertEquals(2, nav.visitVersion());
    }

    @Test public void refreshExpirationAndCorrelatedEchoesPreserveAssumptionsButSameIdIsNewVisit() {
        fake.visit("A", SampleTrip.LOTS);
        discount();
        long version = nav.visitVersion();
        refresh();
        nav.invalidateSelections();
        assertTrue(quote.purchaseDiscount());
        assertEquals(version, nav.visitVersion());
        assertEquals(0, fake.requests.size());
        fake.echo = true;
        assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(nav.select(coex()).orElseThrow()));
        assertEquals(version, nav.visitVersion());
        assertEquals("A", nav.visit().name());
        assertTrue(quote.purchaseDiscount());
        fake.listener.accept(new NavigationPort.Destination("entrance", "entrance", List.of(), fake.lastId));
        refresh();
        assertEquals(version, nav.visitVersion());
        assertEquals("A", nav.visit().id());
        assertEquals(Integer.valueOf(0), coex().expectedWon());
        assertEquals(1, fake.requests.size());
        fake.visit("A", SampleTrip.LOTS);
        assertEquals(version + 1, nav.visitVersion());
        assertFullPrice();
        fake.listener.accept(new NavigationPort.Destination("late echo", "entrance", List.of(), fake.lastId));
        assertEquals(version + 1, nav.visitVersion());
        assertEquals("A", nav.visit().name());
        assertFullPrice();
        assertEquals(1, fake.requests.size());
    }

    @Test public void currentWindowAppliesCancelDoesNothingAndRecreationRestoresOnlyDuration() {
        fake.visit("A", SampleTrip.LOTS);
        VisitQuoteState.Token old = quote.open();
        fake.visit("B", SampleTrip.LOTS);
        assertFalse(quote.apply(old, 60, true));
        assertFullPrice();
        // Cancel has no model write, just as the negative dialog listener is null.
        quote.open();
        assertFullPrice();
        assertTrue(quote.apply(quote.open(), 60, true));
        refresh();
        assertEquals(60, quote.stayMinutes());
        assertTrue(quote.purchaseDiscount());
        assertEquals(Integer.valueOf(0), coex().expectedWon());
        quote.open(); // Cancel the newly opened window: preserve the applied values.
        assertEquals(60, quote.stayMinutes());
        assertTrue(quote.purchaseDiscount());
        VisitQuoteState recreated = new VisitQuoteState(quote.stayMinutes());
        recreated.syncVisit(1);
        assertEquals(60, recreated.stayMinutes());
        assertFalse(recreated.purchaseDiscount());
        assertFalse(recreated.apply(quote.open(), 120, true));
        assertEquals(Integer.valueOf(6000), ParkingPlan.compare(SampleTrip.LOTS, vehicle.heightMm(),
                vehicle.drive(), recreated.stayMinutes(), recreated.purchaseDiscount()).get(0).expectedWon());
        assertEquals(0, fake.requests.size());
    }

    @Test public void discountCannotBypassRestrictionsAndRejectionIsRecordedAsFailure() {
        fake.visit("B", SampleTrip.LOTS);
        for (boolean discounted : new boolean[]{false, true}) {
            assertTrue(quote.apply(quote.open(), 120, discounted));
            refresh();
            VehicleProfile tall = new VehicleProfile("synthetic tall", 2250, null, NON_ELECTRIC, false);
            ParkingPlan.Option restricted = options(tall).stream()
                    .filter(option -> option.lot().equals(SampleTrip.LOTS.get(0))).findFirst().orElseThrow();
            assertEquals(ParkingPlan.Fit.RESTRICTED, restricted.fit());
            assertTrue(nav.select(restricted).isEmpty());
            VehicleProfile missing = new VehicleProfile("synthetic missing", null, null, NON_ELECTRIC, false);
            assertEquals(0, ParkingPlan.requestableCount(options(missing)));
            for (ParkingPlan.Option option : options(missing)) assertTrue(nav.select(option).isEmpty());
        }
        assertEquals(0, fake.requests.size());
        fake.result = NavigationPort.Result.REJECTED;
        ParkingNavigation.Selection selection = nav.select(coex()).orElseThrow();
        assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(selection));
        assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
        assertEquals(ParkingNavigation.Status.REJECTED, nav.status());
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
        assertEquals(1, fake.requests.size());
        assertEquals(1, nav.visitVersion());
    }
}
