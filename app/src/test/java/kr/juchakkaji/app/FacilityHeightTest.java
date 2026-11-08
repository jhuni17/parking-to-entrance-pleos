package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

/** Synthetic facility copies and AAOS-shaped input; no facility change or runtime observation. */
public final class FacilityHeightTest {
    private Lot copy(Lot source, Integer height, EvEntry ev, WidthCondition width) {
        return new Lot(source.name(), source.type(), source.attached(), height, ev,
                source.tariff(), source.entrance(), source.provenance(), width);
    }
    private Lot height(Lot source, Integer height) {
        return copy(source, height, source.evEntry(), source.widthCondition());
    }
    private VehicleProfile vehicle(int height, boolean electric) {
        return VehicleProfile.fromHardware("OTHER", "Synthetic", 2025,
                new Integer[]{height, 0, 1990},
                new Integer[]{electric ? FuelType.ELECTRIC : FuelType.UNLEADED});
    }
    private List<Option> options(ParkingNavigation nav, VehicleProfile v, Priority priority) {
        return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, true, priority);
    }
    private Option named(List<Option> options, Lot lot) {
        return options.stream().filter(o -> o.lot().name().equals(lot.name())).findFirst().orElseThrow();
    }
    private void blocked(ParkingNavigation nav, Option option, Fit fit) {
        assertEquals(fit, option.fit());
        assertFalse(option.requestAvailability().available());
        assertTrue(nav.select(option).isEmpty());
        assertTrue(EntranceConfirmation.create(nav, option, 120, true).isEmpty());
        assertTrue(routeRequest(SampleTrip.VISIT, option).isEmpty());
    }
    @Test public void bothConstructorsNormalizeOnlyNonpositiveHeightAndPreserveEveryOtherField() {
        Lot source = SampleTrip.LOTS.get(0);
        WidthCondition width = new WidthCondition(2100, "합성 · 2026-11-09");
        for (Integer input : new Integer[]{null, 0, -1, Integer.MIN_VALUE, 1, 2200, 2500, Integer.MAX_VALUE}) {
            Integer expected = input == null || input <= 0 ? null : input;
            Lot full = copy(source, input, EvEntry.RESTRICTED, width);
            Lot legacy = new Lot(source.name(), source.type(), source.attached(), input,
                    EvEntry.RESTRICTED, source.tariff(), source.entrance(), source.provenance());
            for (Lot result : List.of(full, legacy)) {
                assertEquals(expected, result.heightLimitMm());
                assertEquals(source.name(), result.name());
                assertEquals(source.type(), result.type());
                assertEquals(source.attached(), result.attached());
                assertEquals(EvEntry.RESTRICTED, result.evEntry());
                assertSame(source.tariff(), result.tariff());
                assertSame(source.entrance(), result.entrance());
                assertSame(source.provenance(), result.provenance());
            }
            assertSame(width, full.widthCondition());
            assertNull(legacy.widthCondition());
        }
    }
    @Test public void missingAndInvalidFacilityHeightFlowsThroughExplanationConfirmationAndOneRequest() {
        for (Integer invalid : new Integer[]{null, 0, -1, Integer.MIN_VALUE}) {
            for (Priority priority : Priority.values()) {
                // Each outcome uses a fresh visit; construction and presentation never send requests.
                for (int outcome = 0; outcome < 4; outcome++) {
                    ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
                    fake.echo = true;
                    fake.result = outcome == 0 ? NavigationPort.Result.ACCEPTED
                            : outcome == 1 ? NavigationPort.Result.REJECTED : NavigationPort.Result.SIMULATED;
                    fake.throwsFailure = outcome == 3;
                    ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
                    Lot coex = height(SampleTrip.LOTS.get(0), invalid);
                    fake.visit(SampleTrip.VISIT, List.of(coex, SampleTrip.LOTS.get(1), SampleTrip.LOTS.get(2)));
                    NavigationPort.Destination original = nav.visit();
                    VehicleProfile v = vehicle(1850, false);
                    assertEquals(Integer.valueOf(1850), v.heightMm());
                    assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, v.drive());
                    List<Option> result = options(nav, v, priority);
                    Option unknown = named(result, coex);
                    blocked(nav, unknown, Fit.NEEDS_CHECK);
                    assertEquals("높이 여유 또는 제한 정보 확인 필요", unknown.reason());
                    assertEquals(Integer.valueOf(12000), unknown.baseWon());
                    assertEquals(Integer.valueOf(0), unknown.expectedWon());
                    assertEquals(SampleTrip.LOTS.get(1), result.get(0).lot());
                    assertEquals(Fit.MATCHES_RECORDED_RULES, result.get(0).fit());
                    assertEquals(Integer.valueOf(6000), result.get(0).baseWon());
                    assertEquals(Integer.valueOf(6000), result.get(0).expectedWon());
                    assertEquals(coex, result.get(1).lot());
                    assertEquals(SampleTrip.LOTS.get(2), result.get(2).lot());
                    assertEquals(1, requestableCount(result));
                    RecommendationExplanation explanation = RecommendationExplanation.create(v,
                            new VehicleHeight(), false, result, priority);
                    assertEquals("코엑스 지하주차장 · 높이 여유 또는 제한 정보 확인 필요", explanation.headline());
                    assertFalse(explanation.cardHeadline().contains("\n"));
                    assertFalse(explanation.headline().contains("높이 초과"));
                    assertTrue(explanation.vehicleDetails().contains("차량 높이 1850mm · 차량 속성값"));
                    EntranceConfirmation confirmation = EntranceConfirmation.create(nav, result.get(0), 120, true).orElseThrow();
                    assertEquals(Integer.valueOf(6000), confirmation.expectedWon());
                    assertTrue(confirmation.message().contains("120분 예상 6,000원"));
                    assertTrue(confirmation.discountText().contains("적용할 구매 할인 규칙 없음"));
                    assertEquals(0, fake.requests.size());
                    ParkingNavigation.Status expected = outcome == 0 ? ParkingNavigation.Status.ACCEPTED
                            : outcome == 2 ? ParkingNavigation.Status.SIMULATED : ParkingNavigation.Status.REJECTED;
                    assertEquals(expected, nav.confirm(confirmation.selection()));
                    assertEquals(expected, nav.lastRequest().outcome());
                    assertSame(original, nav.visit());
                    assertEquals(1, fake.requests.size());
                    RouteRequest request = fake.requests.get(0);
                    assertEquals(SampleTrip.VISIT, request.visit());
                    assertEquals(37.51400, request.entrance().latitude(), 0);
                    assertEquals(127.06100, request.entrance().longitude(), 0);
                    assertEquals(ParkingNavigation.Status.STALE, nav.confirm(confirmation.selection()));
                    assertEquals(1, fake.requests.size());
                }
            }
        }
    }
    @Test public void validHeightBoundariesAndIndependentExplicitRestrictionsRemainEffective() {
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, List.of(SampleTrip.LOTS.get(0)));
            int[] heights = {1850, 2200, 2250};
            Fit[] fits = {Fit.MATCHES_RECORDED_RULES, Fit.NEEDS_CHECK, Fit.RESTRICTED};
            for (int i = 0; i < heights.length; i++) {
                Option option = options(nav, vehicle(heights[i], false), priority).get(0);
                assertEquals(fits[i], option.fit());
                assertEquals(i == 0, option.requestAvailability().available());
                if (i != 0) blocked(nav, option, fits[i]);
            }
            for (int invalid : new int[]{0, -1, Integer.MIN_VALUE}) {
                Lot ev = copy(SampleTrip.LOTS.get(0), invalid, EvEntry.RESTRICTED, null);
                Lot width = copy(SampleTrip.LOTS.get(1), invalid, EvEntry.ALLOWED,
                        new WidthCondition(1980, "합성 · 2026-11-09"));
                fake.visit(SampleTrip.VISIT, List.of(ev, width));
                List<Option> result = options(nav, vehicle(1850, true), priority);
                blocked(nav, named(result, ev), Fit.RESTRICTED);
                assertEquals("등록된 전기차 입차 제한", named(result, ev).reason());
                blocked(nav, named(result, width), Fit.RESTRICTED);
                assertEquals("최대 허용 차폭 초과", named(result, width).reason());
                assertEquals(0, requestableCount(result));
            }
            assertEquals(0, fake.requests.size());
        }
    }
    @Test public void vehicleHeightChangeExpiresSelectionAndUnknownAlternativeCannotReplaceIt() {
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            Lot alternative = height(SampleTrip.LOTS.get(1), 0);
            fake.visit(SampleTrip.VISIT, List.of(SampleTrip.LOTS.get(0), alternative, SampleTrip.LOTS.get(2)));
            List<Option> before = options(nav, vehicle(1850, false), priority);
            assertEquals(1, requestableCount(before));
            assertEquals(SampleTrip.LOTS.get(0), before.get(0).lot());
            EntranceConfirmation old = EntranceConfirmation.create(nav, before.get(0), 120, true).orElseThrow();
            nav.invalidateSelections(); // Same boundary used by MainActivity.refresh on changed readings.
            List<Option> after = options(nav, vehicle(2250, false), priority);
            assertEquals(0, requestableCount(after));
            assertEquals("표기 높이 제한 초과", named(after, SampleTrip.LOTS.get(0)).reason());
            assertEquals(Fit.RESTRICTED, named(after, SampleTrip.LOTS.get(0)).fit());
            assertEquals(Fit.NEEDS_CHECK, named(after, alternative).fit());
            assertEquals("높이 여유 또는 제한 정보 확인 필요", named(after, alternative).reason());
            for (Option option : after) blocked(nav, option, option.fit());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old.selection()));
            assertEquals(0, fake.requests.size());
        }
    }
}
