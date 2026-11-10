package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static kr.juchakkaji.app.VehicleProfile.DriveType.*;
import static org.junit.Assert.*;

/** Synthetic tariff copies and AAOS-shaped inputs; no facility policy or SDK observation. */
public final class DiscountRuleTest {
    private static final String MISSING = "구매 할인 조건 미확인 · 할인 미적용";

    private VehicleProfile vehicle(Integer height, boolean electric) {
        return VehicleProfile.fromHardware("OTHER", "Synthetic", 2025,
                new Integer[]{height, 0, 1900},
                new Integer[]{electric ? FuelType.ELECTRIC : FuelType.UNLEADED});
    }

    private List<Lot> lots(String rule) {
        Lot original = SampleTrip.LOTS.get(0);
        Tariff tariff = original.tariff();
        Lot copy = new Lot(original.name(), original.type(), original.attached(), original.heightLimitMm(),
                original.evEntry(), new Tariff(tariff.unitMinutes(), tariff.wonPerUnit(),
                tariff.purchaseFreeMinutes(), rule), original.entrance(), original.provenance(), original.widthCondition());
        return List.of(copy, SampleTrip.LOTS.get(1), SampleTrip.LOTS.get(2));
    }

    private List<Option> options(ParkingNavigation nav, VehicleProfile v, Priority priority) {
        return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, true, priority);
    }

    private Option attached(List<Option> options) {
        return options.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
    }

    private void explanation(VehicleProfile v, List<Option> options, Priority priority, String expected) {
        RecommendationExplanation text = RecommendationExplanation.create(v, new VehicleHeight(), false, options, priority);
        assertEquals(expected, text.headline());
        assertEquals(text.headline(), text.cardHeadline());
        assertFalse(text.cardHeadline().contains("\n"));
        assertTrue(text.vehicleDetails().contains("차량 속성값") || v.heightMm() == null);
    }

    @Test public void missingConditionsNeverSubtractFreeMinutesAndNormalizeSafely() {
        for (String rule : new String[]{null, "", "  \t\n", "\u2003"}) {
            Tariff tariff = new Tariff(15, 1500, 120, rule);
            assertEquals("", tariff.purchaseRule());
            assertFalse(tariff.appliesPurchaseDiscount(true));
            assertEquals(Integer.valueOf(12000), tariff.quote(120, true));
            assertEquals(tariff.quote(120, false), tariff.quote(120, true));
            assertEquals(MISSING, tariff.discountText(true));
            assertEquals(MISSING, tariff.discountText(false));
        }
        Tariff valid = new Tariff(15, 1500, 120, "  합성 구매 조건  ");
        assertEquals("합성 구매 조건", valid.purchaseRule());
        assertTrue(valid.appliesPurchaseDiscount(true));
        assertFalse(valid.appliesPurchaseDiscount(false));
        assertEquals(Integer.valueOf(0), valid.quote(120, true));
        assertEquals(Integer.valueOf(1500), valid.quote(121, true));
        assertEquals(Integer.valueOf(12000), valid.quote(120, false));
        Tariff none = new Tariff(15, 1500, 0, "합성 조건");
        assertFalse(none.appliesPurchaseDiscount(true));
        assertEquals(Integer.valueOf(12000), none.quote(120, true));
        assertTrue(none.discountText(true).contains("적용할 구매 할인 규칙 없음"));
        assertEquals(Integer.valueOf(0), new Tariff(1, 0, 0, null).quote(Integer.MAX_VALUE, true));
        for (Tariff invalid : List.of(new Tariff(0, 1500, 120, null), new Tariff(15, -1, 120, null),
                new Tariff(15, 1500, -1, null))) {
            assertNull(invalid.quote(120, true));
            assertNull(invalid.quote(120, false));
        }
        assertNull(valid.quote(-1, true));
        assertNull(new Tariff(1, Integer.MAX_VALUE, 0, null).quote(2, true));
    }

    @Test public void hardwareToAmountsOrderExplanationAndConfirmationUsesSameRule() {
        for (String rule : new String[]{null, "", "  "}) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, lots(rule));
            VehicleProfile v = vehicle(1850, false);
            assertEquals(Integer.valueOf(1850), v.heightMm());
            assertEquals(NON_ELECTRIC, v.drive());
            for (Priority priority : Priority.values()) {
                List<Option> result = options(nav, v, priority);
                Option coex = attached(result);
                assertEquals(3, result.size());
                assertEquals(2, requestableCount(result));
                assertEquals(Fit.MATCHES_RECORDED_RULES, coex.fit());
                assertTrue(coex.requestAvailability().available());
                assertEquals(Integer.valueOf(12000), coex.baseWon());
                assertEquals(coex.baseWon(), coex.expectedWon());
                assertEquals(priority == Priority.ATTACHED_FIRST ? coex.lot() : SampleTrip.LOTS.get(1), result.get(0).lot());
                assertEquals(priority == Priority.ATTACHED_FIRST ? SampleTrip.LOTS.get(1) : coex.lot(), result.get(1).lot());
                Option alternative = result.stream().filter(o -> o.lot() == SampleTrip.LOTS.get(1)).findFirst().orElseThrow();
                assertEquals(Integer.valueOf(6000), alternative.expectedWon());
                explanation(v, result, priority, priority == Priority.ATTACHED_FIRST ? "" : "요금 우선 · 예상 요금이 낮은 대안");
                EntranceConfirmation preview = EntranceConfirmation.create(nav, coex, 120, true).orElseThrow();
                assertEquals(coex.lot().tariff().discountText(true), preview.discountText());
                assertEquals(MISSING, preview.discountText());
                assertEquals(coex.baseWon(), preview.expectedWon());
                assertTrue(preview.message().contains("120분 예상 12,000원"));
                assertTrue(EntranceConfirmation.create(nav, alternative, 120, true).orElseThrow()
                        .discountText().contains("적용할 구매 할인 규칙 없음"));
            }
            assertEquals(0, fake.requests.size());
        }
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, lots("합성 구매 조건"));
        for (Priority priority : Priority.values()) {
            Option first = options(nav, vehicle(1850, false), priority).get(0);
            assertTrue(first.lot().attached());
            assertEquals(Integer.valueOf(0), first.expectedWon());
            EntranceConfirmation preview = EntranceConfirmation.create(nav, first, 120, true).orElseThrow();
            assertEquals(first.lot().tariff().discountText(true), preview.discountText());
            assertTrue(preview.message().contains("120분 예상 0원"));
        }
        assertEquals(0, fake.requests.size());
    }

    @Test public void heightInvalidatesOldSelectionAndOnlyExplicitConfirmationRequestsOnce() {
        for (Priority priority : Priority.values()) {
            for (int outcome = 0; outcome < 4; outcome++) {
                ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
                fake.echo = true;
                fake.result = outcome == 0 ? NavigationPort.Result.ACCEPTED
                        : outcome == 1 ? NavigationPort.Result.SIMULATED : NavigationPort.Result.REJECTED;
                fake.throwsFailure = outcome == 3;
                ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
                fake.visit(SampleTrip.VISIT, lots(null));
                NavigationPort.Destination original = nav.visit();
                EntranceConfirmation old = EntranceConfirmation.create(nav,
                        options(nav, vehicle(1850, false), priority).get(0), 120, true).orElseThrow();
                nav.invalidateSelections();
                VehicleProfile tall = vehicle(2250, false);
                List<Option> result = options(nav, tall, priority);
                Option coex = attached(result);
                assertEquals(Fit.RESTRICTED, coex.fit());
                assertFalse(coex.requestAvailability().available());
                assertTrue(nav.select(coex).isEmpty());
                assertEquals(SampleTrip.LOTS.get(1), result.get(0).lot());
                assertEquals(Integer.valueOf(6000), result.get(0).expectedWon());
                assertEquals(1, requestableCount(result));
                explanation(tall, result, priority, "코엑스 지하주차장 · 높이 초과");
                assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old.selection()));
                EntranceConfirmation preview = EntranceConfirmation.create(nav, result.get(0), 120, true).orElseThrow();
                assertTrue(preview.message().contains("120분 예상 6,000원"));
                assertEquals(0, fake.requests.size());
                ParkingNavigation.Status expected = outcome == 0 ? ParkingNavigation.Status.ACCEPTED
                        : outcome == 1 ? ParkingNavigation.Status.SIMULATED : ParkingNavigation.Status.REJECTED;
                assertEquals(expected, nav.confirm(preview.selection()));
                assertSame(original, nav.visit());
                assertEquals(expected, nav.lastRequest().outcome());
                assertEquals(1, fake.requests.size());
                RouteRequest request = fake.requests.get(0);
                assertEquals(SampleTrip.VISIT, request.visit());
                assertEquals(37.51400, request.entrance().latitude(), 0);
                assertEquals(127.06100, request.entrance().longitude(), 0);
                assertEquals(ParkingNavigation.Status.STALE, nav.confirm(preview.selection()));
                assertEquals(1, fake.requests.size());
            }
        }
    }

    @Test public void electricUncertaintyAndMissingHeightStillBlockSelection() {
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, lots(null));
        for (Priority priority : Priority.values()) {
            VehicleProfile electric = vehicle(1850, true);
            assertEquals(ELECTRIC, electric.drive());
            Option coex = attached(options(nav, electric, priority));
            assertEquals(Fit.NEEDS_CHECK, coex.fit());
            assertTrue(nav.select(coex).isEmpty());
            List<Option> missing = options(nav, vehicle(null, false), priority);
            assertEquals(0, requestableCount(missing));
            for (Option option : missing) assertTrue(nav.select(option).isEmpty());
        }
        assertEquals(0, fake.requests.size());
    }
}
