package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

/** Synthetic AAOS and fake navigation; no SDK/runtime observation. */
public final class StayDurationTest {
    private final VisitQuoteState quote = new VisitQuoteState(120);
    private final ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
    private final ParkingNavigation nav = new ParkingNavigation(fake, this::refresh);

    private void refresh() {
        quote.syncVisit(nav.visitVersion());
        nav.invalidateSelections();
    }
    private void apply(String minutes) {
        assertTrue(quote.apply(quote.open(), minutes, true));
        refresh(); // Same success path as MainActivity.
    }
    private VehicleProfile hardware(Integer height, int fuel) {
        VehicleProfile vehicle = VehicleProfile.fromHardware(null, null, null,
                new Integer[]{height, 4700, 1990, 2100}, new Integer[]{fuel});
        assertEquals(height, vehicle.heightMm());
        assertEquals(Integer.valueOf(1990), vehicle.widthMm());
        assertEquals(fuel == FuelType.ELECTRIC ? VehicleProfile.DriveType.ELECTRIC
                : VehicleProfile.DriveType.NON_ELECTRIC, vehicle.drive());
        return vehicle;
    }
    private List<Option> options(VehicleProfile vehicle, Priority priority) {
        return nav.options(vehicle.heightMm(), vehicle.widthMm(), vehicle.drive(),
                quote.stayMinutes(), quote.purchaseDiscount(), priority);
    }
    private RecommendationExplanation explanation(VehicleProfile vehicle, List<Option> options, Priority priority) {
        return RecommendationExplanation.create(vehicle, new VehicleHeight(), false, options, priority);
    }
    private Option coex(List<Option> options) {
        return options.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
    }
    private Option alternative(List<Option> options) {
        return options.stream().filter(o -> o.lot().equals(SampleTrip.LOTS.get(1))).findFirst().orElseThrow();
    }
    private EntranceConfirmation preview(Option option) {
        EntranceConfirmation result = EntranceConfirmation.create(nav, option,
                quote.stayMinutes(), quote.purchaseDiscount()).orElseThrow();
        assertEquals(quote.stayMinutes(), result.stayMinutes());
        assertEquals(quote.purchaseDiscount(), result.purchaseDiscount());
        assertEquals(option.expectedWon(), result.expectedWon());
        assertTrue(result.message().contains(quote.durationText()));
        return result;
    }

    @Test public void inputBoundariesPreserveBothFieldsOnEveryFailure() {
        for (String valid : List.of("1", "90", "121", "180", "241", "360")) {
            assertTrue(valid, quote.apply(quote.open(), valid, true));
            assertEquals(Integer.parseInt(valid), quote.stayMinutes());
            assertTrue(quote.purchaseDiscount());
            assertEquals(valid + "분 예상", quote.durationText());
            for (String invalid : List.of("", "0", "-1", "361", "1.5", "abc", "2147483648", " 90", "+90")) {
                assertFalse(invalid, quote.apply(quote.open(), invalid, false));
                assertEquals(Integer.parseInt(valid), quote.stayMinutes());
                assertTrue(quote.purchaseDiscount());
            }
            assertFalse(quote.apply(quote.open(), (String) null, false));
            for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 361, Integer.MAX_VALUE}) {
                assertFalse(quote.apply(quote.open(), invalid, false));
                assertEquals(Integer.parseInt(valid), quote.stayMinutes());
                assertTrue(quote.purchaseDiscount());
            }
        }
        VisitQuoteState plain = new VisitQuoteState(90);
        assertFalse(plain.apply(plain.open(), "361", true));
        assertEquals(90, plain.stayMinutes());
        assertFalse(plain.purchaseDiscount());
        assertFalse(plain.apply(quote.open(), "120", true));
        assertFalse(plain.apply(null, "120", true));
        for (int valid : new int[]{1, 360}) assertTrue(plain.apply(plain.open(), valid, false));
        for (int restored : new int[]{Integer.MIN_VALUE, -1, 0, 361, Integer.MAX_VALUE}) {
            VisitQuoteState state = new VisitQuoteState(restored);
            assertEquals(120, state.stayMinutes());
            assertFalse(state.purchaseDiscount());
        }
        for (int restored : new int[]{1, 90, 121, 180, 241, 360}) {
            VisitQuoteState state = new VisitQuoteState(restored);
            assertEquals(restored, state.stayMinutes());
            assertFalse(state.purchaseDiscount());
        }
    }

    @Test public void discountEndAndFareCrossoverFlowThroughExplanationAndPreviewWithoutRequests() {
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        VehicleProfile vehicle = hardware(1850, FuelType.UNLEADED);
        int[][] cases = {{120, 0, 6000}, {121, 1500, 6500}, {180, 6000, 9000},
                {240, 12000, 12000}, {241, 13500, 12500}};
        for (int[] expected : cases) {
            apply(Integer.toString(expected[0]));
            for (Priority priority : Priority.values()) {
                List<Option> candidates = options(vehicle, priority);
                assertEquals(Integer.valueOf(expected[1]), coex(candidates).expectedWon());
                assertEquals(Integer.valueOf(expected[2]), alternative(candidates).expectedWon());
                boolean crossover = expected[0] == 241 && priority == Priority.FARE_FIRST;
                assertEquals(crossover ? alternative(candidates) : coex(candidates), candidates.get(0));
                assertEquals(crossover ? coex(candidates) : alternative(candidates), candidates.get(1));
                assertEquals(2, requestableCount(candidates));
                RecommendationExplanation reason = explanation(vehicle, candidates, priority);
                assertEquals(crossover ? "요금 우선 · 예상 요금이 낮은 대안" : "", reason.headline());
                assertEquals("요청 가능한 후보 2곳", reason.candidateSummary());
                preview(candidates.get(0));
                assertEquals(0, fake.requests.size());
            }
        }
        for (String minutes : List.of("90", "121", "241")) {
            apply(minutes);
            assertEquals(minutes + "분 예상", quote.durationText());
            assertTrue(preview(options(vehicle, Priority.FARE_FIRST).get(0)).message()
                    .contains(minutes + "분 예상"));
        }
        assertEquals(0, fake.requests.size());
    }

    @Test public void threeHourHardwareRestrictionsStillBlockSelection() {
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        apply("180");
        for (Priority priority : Priority.values()) {
            VehicleProfile normal = hardware(1850, FuelType.UNLEADED);
            assertEquals(Integer.valueOf(6000), options(normal, priority).get(0).expectedWon());
            VehicleProfile tall = hardware(2250, FuelType.UNLEADED);
            List<Option> candidates = options(tall, priority);
            assertEquals(Fit.RESTRICTED, coex(candidates).fit());
            assertFalse(coex(candidates).requestAvailability().available());
            assertTrue(nav.select(coex(candidates)).isEmpty());
            assertEquals(alternative(candidates), candidates.get(0));
            assertEquals(Integer.valueOf(9000), candidates.get(0).expectedWon());
            assertEquals(1, requestableCount(candidates));
            assertTrue(explanation(tall, candidates, priority).headline().contains("높이 초과"));
            assertTrue(preview(candidates.get(0)).message().contains("180분 예상 9,000원"));
            VehicleProfile electric = hardware(1850, FuelType.ELECTRIC);
            candidates = options(electric, priority);
            assertEquals(Fit.NEEDS_CHECK, coex(candidates).fit());
            assertTrue(nav.select(coex(candidates)).isEmpty());
            assertEquals(1, requestableCount(candidates));
            assertEquals(Integer.valueOf(9000), candidates.get(0).expectedWon());
            VehicleProfile missing = hardware(null, FuelType.UNLEADED);
            candidates = options(missing, priority);
            assertEquals(0, requestableCount(candidates));
            for (Option option : candidates) assertTrue(nav.select(option).isEmpty());
        }
        assertEquals(0, fake.requests.size());
    }

    @Test public void durationSurvivesRefreshEchoNewVisitAndRestoreButOldWindowCannotWrite() {
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        VehicleProfile vehicle = hardware(1850, FuelType.UNLEADED);
        EntranceConfirmation old = preview(options(vehicle, Priority.ATTACHED_FIRST).get(0));
        apply("241");
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old.selection()));
        assertEquals(0, fake.requests.size());
        // Vehicle reread and priority change both refresh without a new visit.
        refresh();
        options(hardware(1850, FuelType.UNLEADED), Priority.ATTACHED_FIRST);
        refresh();
        List<Option> candidates = options(vehicle, Priority.FARE_FIRST);
        assertEquals(241, quote.stayMinutes());
        assertTrue(quote.purchaseDiscount());
        quote.open(); // Cancel: no model write.
        assertEquals(241, quote.stayMinutes());
        assertTrue(quote.purchaseDiscount());
        EntranceConfirmation fresh = preview(candidates.get(0));
        assertEquals(SampleTrip.LOTS.get(1), fresh.lot());
        assertTrue(fresh.message().contains("241분 예상 12,500원"));
        assertEquals(0, fake.requests.size());
        fake.echo = true;
        assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(fresh.selection()));
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(fresh.selection()));
        assertEquals(1, fake.requests.size());
        assertEquals(SampleTrip.VISIT, fake.requests.get(0).visit());
        assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0.0);
        assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0.0);
        fake.listener.accept(new NavigationPort.Destination("echo", "입구", List.of(), fake.lastId));
        refresh();
        assertEquals(SampleTrip.VISIT, nav.visit().name());
        assertEquals(241, quote.stayMinutes());
        assertTrue(quote.purchaseDiscount());
        VisitQuoteState.Token previousWindow = quote.open();
        fake.visit("새 방문", SampleTrip.LOTS);
        assertEquals(241, quote.stayMinutes());
        assertFalse(quote.purchaseDiscount());
        assertFalse(quote.apply(previousWindow, "180", true));
        assertFalse(quote.apply(previousWindow, 90, true));
        assertEquals(241, quote.stayMinutes());
        assertFalse(quote.purchaseDiscount());
        VisitQuoteState recreated = new VisitQuoteState(quote.stayMinutes());
        recreated.syncVisit(1);
        assertEquals(241, recreated.stayMinutes());
        assertFalse(recreated.purchaseDiscount());
    }

    @Test public void newFareAlternativeRejectionAndExceptionAreFailuresAndConsumedOnce() {
        for (boolean throwsFailure : new boolean[]{false, true}) {
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            apply("241");
            List<Option> candidates = options(hardware(1850, FuelType.UNLEADED), Priority.FARE_FIRST);
            EntranceConfirmation shown = preview(candidates.get(0));
            fake.result = NavigationPort.Result.REJECTED;
            fake.throwsFailure = throwsFailure;
            int before = fake.requests.size();
            assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(shown.selection()));
            assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(shown.selection()));
            assertEquals(before + 1, fake.requests.size());
        }
    }
}
