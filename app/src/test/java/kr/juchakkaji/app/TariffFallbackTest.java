package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static kr.juchakkaji.app.VehicleProfile.DriveType.*;
import static org.junit.Assert.*;

/** Synthetic AAOS inputs and tariff mutations, not SDK/runtime observations. */
public final class TariffFallbackTest {
    private static final Tariff INVALID = new Tariff(0, 1500, 120, "합성 오류");

    private VehicleProfile vehicle(int height) {
        return VehicleProfile.fromHardware("OTHER", "Synthetic", 2025,
                new Integer[]{height, 0, 1900}, new Integer[]{FuelType.UNLEADED});
    }
    private Lot withTariff(Lot lot, Tariff tariff) {
        return new Lot(lot.name(), lot.type(), lot.attached(), lot.heightLimitMm(), lot.evEntry(),
                tariff, lot.entrance(), lot.provenance(), lot.widthCondition());
    }
    private List<Option> options(ParkingNavigation nav, VehicleProfile v, Priority priority) {
        return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, false, priority);
    }
    private String explanation(VehicleProfile v, List<Option> options, Priority priority) {
        RecommendationExplanation result = RecommendationExplanation.create(v, new VehicleHeight(), false, options, priority);
        assertEquals(result.headline(), result.cardHeadline());
        assertFalse(result.headline().contains("\n"));
        return result.headline();
    }
    @Test public void invalidInputsAreUnknownInQuotesAndBothComparisonAmounts() {
        for (Tariff tariff : List.of(INVALID, new Tariff(-1, 1500, 120, ""),
                new Tariff(15, -1, 120, ""), new Tariff(15, 1500, -1, ""),
                SampleTrip.LOTS.get(0).tariff())) {
            int stay = tariff == SampleTrip.LOTS.get(0).tariff() ? -1 : 120;
            for (boolean discount : new boolean[]{false, true}) {
                assertNull(tariff.quote(stay, discount));
                Option option = compare(List.of(withTariff(SampleTrip.LOTS.get(0), tariff)),
                        1850, NON_ELECTRIC, stay, discount).get(0);
                assertNull(option.baseWon());
                assertNull(option.expectedWon());
                assertEquals(Fit.MATCHES_RECORDED_RULES, option.fit());
                assertTrue(option.requestAvailability().available());
            }
        }
    }
    @Test public void validDiscountRoundingAndIntegerBoundaries() {
        Tariff tariff = new Tariff(15, 1500, 120, "합성");
        assertEquals(Integer.valueOf(6000), tariff.quote(60, false));
        assertEquals(Integer.valueOf(0), tariff.quote(120, true));
        assertEquals(Integer.valueOf(1500), tariff.quote(121, true));
        assertEquals(Integer.valueOf(0), tariff.quote(0, false));
        assertEquals(Integer.valueOf(0), new Tariff(1, 0, 0, "").quote(Integer.MAX_VALUE, false));
        assertEquals(Integer.valueOf(1500), new Tariff(Integer.MAX_VALUE, 1500, 0, "").quote(120, false));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), new Tariff(1, 1, 0, "").quote(Integer.MAX_VALUE, false));
        Tariff large = new Tariff(1, Integer.MAX_VALUE, 0, "");
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), large.quote(1, false));
        assertNull(large.quote(2, false));
        assertNull(large.quote(Integer.MAX_VALUE, false));
        Option overflow = compare(List.of(withTariff(SampleTrip.LOTS.get(0), large)),
                1850, NON_ELECTRIC, 2, false).get(0);
        assertNull(overflow.baseWon());
        assertNull(overflow.expectedWon());
    }
    @Test public void invalidAttachedTariffKeepsFitAndUsesKnownFareExplanation() {
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        Lot attached = withTariff(SampleTrip.LOTS.get(0), INVALID);
        fake.visit(SampleTrip.VISIT, List.of(attached, SampleTrip.LOTS.get(1), SampleTrip.LOTS.get(2)));
        VehicleProfile v = vehicle(1850);
        assertEquals(NON_ELECTRIC, v.drive());
        assertEquals(Integer.valueOf(1850), v.heightMm());
        for (Priority priority : Priority.values()) {
            List<Option> result = options(nav, v, priority);
            assertEquals(3, result.size());
            assertEquals(2, requestableCount(result));
            Option coex = result.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
            assertNull(coex.baseWon());
            assertNull(coex.expectedWon());
            assertEquals(Fit.MATCHES_RECORDED_RULES, coex.fit());
            assertTrue(coex.requestAvailability().available());
            assertEquals(priority == Priority.ATTACHED_FIRST ? attached : SampleTrip.LOTS.get(1), result.get(0).lot());
            assertEquals(priority == Priority.ATTACHED_FIRST ? SampleTrip.LOTS.get(1) : attached, result.get(1).lot());
            assertEquals(Integer.valueOf(6000), result.stream().filter(o -> o.lot().equals(SampleTrip.LOTS.get(1)))
                    .findFirst().orElseThrow().expectedWon());
            assertEquals(priority == Priority.ATTACHED_FIRST ? "" : "요금 우선 · 요금 확인된 대안", explanation(v, result, priority));
            assertTrue(nav.select(result.get(0)).isPresent());
        }
        assertEquals(0, fake.requests.size());
    }
    @Test public void heightChangeSelectsUnknownFareAlternativeAndOnlyConfirmationRequestsRoute() {
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            fake.echo = true;
            fake.result = NavigationPort.Result.REJECTED;
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            Lot alternative = withTariff(SampleTrip.LOTS.get(1), INVALID);
            fake.visit(SampleTrip.VISIT, List.of(SampleTrip.LOTS.get(0), alternative, SampleTrip.LOTS.get(2)));
            NavigationPort.Destination original = nav.visit();
            List<Option> before = options(nav, vehicle(1850), priority);
            assertTrue(before.get(0).lot().attached());
            assertEquals(Integer.valueOf(12000), before.get(0).expectedWon());
            ParkingNavigation.Selection stale = nav.select(before.get(0)).orElseThrow();
            nav.invalidateSelections();
            VehicleProfile tall = vehicle(2250);
            List<Option> after = options(nav, tall, priority);
            assertEquals(alternative, after.get(0).lot());
            assertNull(after.get(0).baseWon());
            assertNull(after.get(0).expectedWon());
            assertEquals(1, requestableCount(after));
            Option blocked = after.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
            assertEquals(Fit.RESTRICTED, blocked.fit());
            assertFalse(blocked.requestAvailability().available());
            assertTrue(nav.select(blocked).isEmpty());
            assertEquals("코엑스 지하주차장 · 높이 초과", explanation(tall, after, priority));
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(stale));
            ParkingNavigation.Selection selected = nav.select(after.get(0)).orElseThrow();
            assertEquals(0, fake.requests.size());
            assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(selected));
            assertSame(original, nav.visit());
            assertEquals(1, fake.requests.size());
            RouteRequest request = fake.requests.get(0);
            assertEquals(SampleTrip.VISIT, request.visit());
            assertEquals(37.51400, request.entrance().latitude(), 0);
            assertEquals(127.06100, request.entrance().longitude(), 0);
            assertEquals(new ParkingNavigation.Receipt(request, ParkingNavigation.Status.REJECTED), nav.lastRequest());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selected));
            assertEquals(1, fake.requests.size());
        }
    }
    @Test public void allUnknownFaresKeepHeightFuelWidthAndEntranceBlocks() {
        Entrance entrance = new Entrance("합성 입구", 37, 127);
        List<Lot> lots = List.of(
                new Lot("높이", "합성", true, 1800, EvEntry.ALLOWED, INVALID, entrance, "합성"),
                new Lot("연료", "합성", false, 2500, EvEntry.RESTRICTED, INVALID, entrance, "합성"),
                new Lot("차폭", "합성", false, 2500, EvEntry.ALLOWED, INVALID, entrance, "합성", new WidthCondition(1800, "합성")),
                new Lot("입구", "합성", false, 2500, EvEntry.ALLOWED, INVALID, null, "합성"),
                new Lot("좌표", "합성", false, 2500, EvEntry.ALLOWED, INVALID, new Entrance("오류", Double.NaN, 127), "합성"));
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, lots);
            List<Option> result = nav.options(1850, 1900, ELECTRIC, 120, true, priority);
            assertEquals(5, result.size());
            assertEquals(0, requestableCount(result));
            for (Option option : result) {
                assertNull(option.baseWon());
                assertNull(option.expectedWon());
                assertFalse(option.requestAvailability().available());
                assertTrue(routeRequest(SampleTrip.VISIT, option).isEmpty());
                assertTrue(nav.select(option).isEmpty());
                int index = lots.indexOf(option.lot());
                assertEquals(index < 3 ? Fit.RESTRICTED : Fit.MATCHES_RECORDED_RULES, option.fit());
                assertEquals(List.of("표기 높이 제한 초과", "등록된 전기차 입차 제한", "최대 허용 차폭 초과",
                        "입구 정보 미확인", "입구 좌표 확인 필요").get(index), option.requestAvailability().reason());
            }
            assertEquals(0, fake.requests.size());
        }
    }
    @Test public void explanationUsesSuppliedOptionsRatherThanRecomputingTariffs() {
        Lot attached = SampleTrip.LOTS.get(0); // Valid source tariff; supplied quote is unknown.
        Option alternative = new Option(SampleTrip.LOTS.get(1), Fit.MATCHES_RECORDED_RULES, "", 6000, 6000);
        Option unknown = new Option(attached, Fit.MATCHES_RECORDED_RULES, "", null, null);
        assertEquals("요금 우선 · 요금 확인된 대안", explanation(vehicle(1850), List.of(alternative, unknown), Priority.FARE_FIRST));
        Option unknownAlternative = new Option(SampleTrip.LOTS.get(1), Fit.MATCHES_RECORDED_RULES, "", null, null);
        assertEquals("", explanation(vehicle(1850), List.of(unknownAlternative, unknown), Priority.FARE_FIRST));
        Option restricted = new Option(attached, Fit.RESTRICTED, "표기 높이 제한 초과", null, null);
        assertEquals("코엑스 지하주차장 · 높이 초과", explanation(vehicle(1850), List.of(alternative, restricted), Priority.FARE_FIRST));
        Option known = new Option(attached, Fit.MATCHES_RECORDED_RULES, "", 12000, 12000);
        assertEquals("요금 우선 · 예상 요금이 낮은 대안", explanation(vehicle(1850), List.of(alternative, known), Priority.FARE_FIRST));
    }
}
