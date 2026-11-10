package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import java.util.Set;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

/** Synthetic hardware inputs and fake navigation only; no SDK/runtime observations. */
public final class CompoundFitTest {
    private static final FitReason HEIGHT = new FitReason(Fit.RESTRICTED, "표기 높이 제한 초과");
    private static final FitReason EV_LIMIT = new FitReason(Fit.RESTRICTED, "등록된 전기차 입차 제한");
    private static final FitReason WIDTH = new FitReason(Fit.RESTRICTED, "최대 허용 차폭 초과");
    private static final FitReason EV_UNKNOWN = new FitReason(Fit.NEEDS_CHECK, "전기차 입차 조건 확인 필요");

    private VehicleProfile hardware(Integer height, Integer width, Integer fuel) {
        return VehicleProfile.fromHardware("OTHER", "Synthetic", 2025,
                new Integer[]{height, 0, width}, fuel == null ? null : new Integer[]{fuel});
    }
    private List<Option> options(ParkingNavigation nav, VehicleProfile vehicle, Priority priority) {
        return nav.options(vehicle.heightMm(), vehicle.widthMm(), vehicle.drive(), 120, true, priority);
    }
    private Option coex(List<Option> options) {
        return options.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
    }
    private Lot fixture(EvEntry ev, WidthCondition width, Entrance entrance) {
        return new Lot("합성 시설", "합성", true, 2200, ev,
                new Tariff(15, 1500, 120, "합성 할인"), entrance,
                "합성 fixture · 2026-11-09", width);
    }
    private Option evaluate(Lot lot, VehicleProfile vehicle, Set<Lot> excluded) {
        return compare(List.of(lot), vehicle.heightMm(), vehicle.widthMm(), vehicle.drive(),
                120, true, Priority.ATTACHED_FIRST, excluded).get(0);
    }
    private void blocked(Option option, List<FitReason> expected, Fit fit) {
        assertEquals(expected, option.reasons());
        assertEquals(expected.size(), Set.copyOf(option.reasons()).size());
        assertEquals(fit, option.fit());
        assertFalse(option.requestAvailability().available());
        assertTrue(routeRequest(SampleTrip.VISIT, option).isEmpty());
    }
    @Test public void coexCollectsRestrictionAndUncertaintyOnceAndProtectsSnapshot() {
        VehicleProfile v = hardware(2250, 1990, FuelType.ELECTRIC);
        Option option = coex(compare(SampleTrip.LOTS, v.heightMm(), v.widthMm(), v.drive(),
                120, true, Priority.ATTACHED_FIRST));
        blocked(option, List.of(HEIGHT, EV_UNKNOWN), Fit.RESTRICTED);
        assertEquals(HEIGHT.text(), option.reason());
        assertEquals(Integer.valueOf(12000), option.baseWon());
        assertEquals(Integer.valueOf(0), option.expectedWon());
        assertEquals("차량 판정: RESTRICTED\n제한: 표기 높이 제한 초과\n확인 필요: 전기차 입차 조건 확인 필요",
                option.vehicleFitDetails());
        assertThrows(UnsupportedOperationException.class, () -> option.reasons().add(WIDTH));
        assertThrows(UnsupportedOperationException.class, () -> option.reasons().clear());
        java.util.ArrayList<FitReason> input = new java.util.ArrayList<>(option.reasons());
        Option snapshot = new Option(option.lot(), option.fit(), option.reason(), option.baseWon(),
                option.expectedWon(), false, input);
        input.clear();
        assertEquals(option.reasons(), snapshot.reasons());
    }
    @Test public void allThreeRestrictionsAndMixedUncertaintySurviveIndependently() {
        VehicleProfile v = hardware(2250, 1990, FuelType.ELECTRIC);
        Entrance entrance = new Entrance("합성 입구", 37.5, 127.1);
        WidthCondition width = new WidthCondition(1980, "합성 · 2026-11-09");
        Option restricted = evaluate(fixture(EvEntry.RESTRICTED, width, entrance), v, Set.of());
        blocked(restricted, List.of(HEIGHT, EV_LIMIT, WIDTH), Fit.RESTRICTED);
        assertEquals(HEIGHT.text(), restricted.reason());
        assertEquals("차량 판정: RESTRICTED\n제한: 표기 높이 제한 초과\n제한: 등록된 전기차 입차 제한\n제한: 최대 허용 차폭 초과",
                restricted.vehicleFitDetails());
        Option mixed = evaluate(fixture(EvEntry.UNKNOWN, width, entrance), v, Set.of());
        blocked(mixed, List.of(HEIGHT, WIDTH, EV_UNKNOWN), Fit.RESTRICTED);
        assertEquals(HEIGHT.text(), mixed.reason());
        assertTrue(mixed.vehicleFitDetails().contains("확인 필요: " + EV_UNKNOWN.text()));
    }
    @Test public void missingVehicleFieldsAndNonVehicleBlocksRemainDistinct() {
        Entrance entrance = new Entrance("합성 입구", 37.5, 127.1);
        WidthCondition width = new WidthCondition(2000, "합성 · 2026-11-09");
        VehicleProfile missing = hardware(null, null, null);
        assertNull(missing.heightMm());
        assertNull(missing.widthMm());
        assertEquals(VehicleProfile.DriveType.NEEDS_CHECK, missing.drive());
        Option unknown = evaluate(fixture(EvEntry.UNKNOWN, width, entrance), missing, Set.of());
        blocked(unknown, List.of(new FitReason(Fit.NEEDS_CHECK, "차량 너비 확인 필요"),
                new FitReason(Fit.NEEDS_CHECK, "차량 높이 확인 필요"),
                new FitReason(Fit.NEEDS_CHECK, "차량 연료 유형 확인 필요")), Fit.NEEDS_CHECK);
        assertEquals("차량 판정: NEEDS_CHECK\n확인 필요: 차량 너비 확인 필요\n확인 필요: 차량 높이 확인 필요\n확인 필요: 차량 연료 유형 확인 필요",
                unknown.vehicleFitDetails());
        Option unregistered = evaluate(fixture(EvEntry.UNKNOWN, null, entrance), missing, Set.of());
        assertEquals(List.of(new FitReason(Fit.NEEDS_CHECK, "차량 높이 확인 필요"),
                new FitReason(Fit.NEEDS_CHECK, "차량 연료 유형 확인 필요")), unregistered.reasons());
        VehicleProfile known = hardware(1850, 1990, FuelType.UNLEADED);
        for (Entrance input : new Entrance[]{null, new Entrance("오류", Double.NaN, 127.1)}) {
            Option option = evaluate(fixture(EvEntry.ALLOWED, null, input), known, Set.of());
            assertEquals(Fit.MATCHES_RECORDED_RULES, option.fit());
            assertEquals(List.of(), option.reasons());
            assertFalse(option.requestAvailability().available());
            assertTrue(routeRequest(SampleTrip.VISIT, option).isEmpty());
            assertEquals(input == null ? "입구 정보 미확인" : "입구 좌표 확인 필요",
                    option.requestAvailability().reason());
        }
        Lot lot = fixture(EvEntry.UNKNOWN, width, entrance);
        Option excluded = evaluate(lot, hardware(2250, 1990, FuelType.ELECTRIC), Set.of(lot));
        blocked(excluded, List.of(HEIGHT, EV_UNKNOWN), Fit.RESTRICTED);
        assertEquals("이번 방문 제외", excluded.requestAvailability().reason());
        Lot allowed = fixture(EvEntry.ALLOWED, null, entrance);
        Option excludedAllowed = evaluate(allowed, known, Set.of(allowed));
        assertEquals(Fit.MATCHES_RECORDED_RULES, excludedAllowed.fit());
        assertTrue(excludedAllowed.reasons().isEmpty());
        assertFalse(excludedAllowed.requestAvailability().available());
    }
    @Test public void fullTransitionWithoutConfirmationNeverRequests() {
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            VehicleProfile[] vehicles = {hardware(2250, 1990, FuelType.ELECTRIC),
                    hardware(1850, 1990, FuelType.ELECTRIC), hardware(1850, 1990, FuelType.UNLEADED)};
            long[] counts = {1, 1, 2};
            ParkingNavigation.Selection previous = null;
            for (int i = 0; i < vehicles.length; i++) {
                nav.invalidateSelections();
                List<Option> candidates = options(nav, vehicles[i], priority);
                assertEquals(counts[i], requestableCount(candidates));
                assertEquals(Integer.valueOf(i == 2 ? 0 : 6000), candidates.get(0).expectedWon());
                for (Option option : candidates) option.vehicleFitDetails();
                RecommendationExplanation.create(vehicles[i], new VehicleHeight(), false, candidates, priority);
                if (previous != null) assertEquals(ParkingNavigation.Status.STALE, nav.confirm(previous));
                previous = EntranceConfirmation.create(nav, candidates.get(0), 120, true).orElseThrow().selection();
                assertEquals(0, fake.requests.size());
            }
        }
    }

    @Test public void hardwareTransitionsKeepPolicyUncertaintyAndRequireFreshExplicitConfirmation() {
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            fake.echo = true;
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            NavigationPort.Destination original = nav.visit();
            VehicleProfile tall = hardware(2250, 1990, FuelType.ELECTRIC);
            List<Option> first = options(nav, tall, priority);
            assertEquals(List.of(SampleTrip.LOTS.get(1), SampleTrip.LOTS.get(2), SampleTrip.LOTS.get(0)),
                    first.stream().map(Option::lot).toList());
            assertEquals(1, requestableCount(first));
            blocked(coex(first), List.of(HEIGHT, EV_UNKNOWN), Fit.RESTRICTED);
            RecommendationExplanation explanation = RecommendationExplanation.create(tall,
                    new VehicleHeight(), false, first, priority);
            assertEquals("코엑스 지하주차장 · 높이 초과", explanation.cardHeadline());
            assertFalse(explanation.cardHeadline().contains("\n"));
            assertEquals(Integer.valueOf(6000), first.get(0).expectedWon());
            EntranceConfirmation old = EntranceConfirmation.create(nav, first.get(0), 120, true).orElseThrow();
            assertEquals(0, fake.requests.size());

            nav.invalidateSelections(); // Same refresh boundary as MainActivity; no hardware UI control.
            VehicleProfile lower = hardware(1850, 1990, FuelType.ELECTRIC);
            List<Option> second = options(nav, lower, priority);
            assertEquals(List.of(SampleTrip.LOTS.get(1), SampleTrip.LOTS.get(0), SampleTrip.LOTS.get(2)),
                    second.stream().map(Option::lot).toList());
            blocked(coex(second), List.of(EV_UNKNOWN), Fit.NEEDS_CHECK);
            assertEquals("차량 판정: NEEDS_CHECK\n확인 필요: 전기차 입차 조건 확인 필요", coex(second).vehicleFitDetails());
            assertEquals(1, requestableCount(second));
            assertEquals(Integer.valueOf(6000), second.get(0).expectedWon());
            assertEquals("코엑스 지하주차장 · 전기차 입차 조건 미확인",
                    RecommendationExplanation.create(lower, new VehicleHeight(), false, second, priority).cardHeadline());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old.selection()));
            EntranceConfirmation fresh = EntranceConfirmation.create(nav, second.get(0), 120, true).orElseThrow();
            assertEquals(SampleTrip.VISIT, fresh.visit());
            assertTrue(fresh.message().contains("120분 예상 6,000원"));
            assertEquals(0, fake.requests.size());
            assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(fresh.selection()));
            assertSame(original, nav.visit());
            assertEquals(1, fake.requests.size());
            assertEquals(SampleTrip.VISIT, fake.requests.get(0).visit());
            assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0);
            assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0);
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(fresh.selection()));
            assertEquals(1, fake.requests.size());

            nav.invalidateSelections();
            VehicleProfile conventional = hardware(1850, 1990, FuelType.UNLEADED);
            List<Option> third = options(nav, conventional, priority);
            assertEquals(List.of(SampleTrip.LOTS.get(0), SampleTrip.LOTS.get(1), SampleTrip.LOTS.get(2)),
                    third.stream().map(Option::lot).toList());
            assertEquals(2, requestableCount(third));
            assertEquals(Fit.MATCHES_RECORDED_RULES, coex(third).fit());
            assertTrue(coex(third).reasons().isEmpty());
            assertEquals("차량 판정: MATCHES_RECORDED_RULES · 등록된 입차 조건 기준 충족", coex(third).vehicleFitDetails());
            assertEquals(Integer.valueOf(12000), coex(third).baseWon());
            assertEquals(Integer.valueOf(0), third.get(0).expectedWon());
            assertEquals("", RecommendationExplanation.create(conventional, new VehicleHeight(), false, third, priority).cardHeadline());
            assertEquals(1, fake.requests.size());
        }
    }
}
