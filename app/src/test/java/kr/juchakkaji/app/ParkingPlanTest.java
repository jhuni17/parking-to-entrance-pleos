package kr.juchakkaji.app;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

public final class ParkingPlanTest {
    @Test public void purchaseConditionMakesAttachedLotFirst() {
        List<Option> options = compare(SampleTrip.LOTS, 1850, false, 120, true);
        assertEquals("코엑스 지하주차장", options.get(0).lot().name());
        assertEquals(Integer.valueOf(12000), options.get(0).baseWon());
        assertEquals(Integer.valueOf(0), options.get(0).expectedWon());
    }

    @Test public void noPurchaseConditionKeepsFullPriceAndReorders() {
        List<Option> options = compare(SampleTrip.LOTS, 1850, false, 120, false);
        assertEquals("대안 A · 시연 주차장", options.get(0).lot().name());
        assertEquals(Integer.valueOf(12000), find(options, "코엑스 지하주차장").expectedWon());
    }

    @Test public void heightRestrictionWinsOverFreeParking() {
        List<Option> options = compare(SampleTrip.LOTS, 2250, false, 120, true);
        assertEquals("대안 A · 시연 주차장", options.get(0).lot().name());
        Option attached = find(options, "코엑스 지하주차장");
        assertEquals(Fit.RESTRICTED, attached.fit());
        assertFalse(routeRequest(SampleTrip.VISIT, attached).isPresent());
    }

    @Test public void missingAndBoundaryHeightNeedConfirmation() {
        Option unknownLot = find(compare(SampleTrip.LOTS, 1850, false, 60, false),
                "대안 B · 시연 주차장");
        Option unknownVehicle = compare(SampleTrip.LOTS, null, false, 60, false).get(0);
        Option exactHeight = find(compare(SampleTrip.LOTS, 2200, false, 60, false),
                "코엑스 지하주차장");
        assertEquals(Fit.NEEDS_CHECK, unknownLot.fit());
        assertEquals(Fit.NEEDS_CHECK, unknownVehicle.fit());
        assertEquals(Fit.NEEDS_CHECK, exactHeight.fit());
        assertFalse(routeRequest(SampleTrip.VISIT, unknownLot).isPresent());
    }

    @Test public void unknownEvPolicyAndEvBanAreDifferent() {
        Option unknown = find(compare(SampleTrip.LOTS, 1850, true, 120, true), "코엑스 지하주차장");
        Lot restricted = new Lot("EV 제한 샘플", "기계식 예시", true, 2200, EvEntry.RESTRICTED,
                null, null, "테스트 데이터");
        Option banned = compare(Arrays.asList(restricted), 1850, true, 60, false).get(0);
        assertEquals(Fit.NEEDS_CHECK, unknown.fit());
        assertEquals(Fit.RESTRICTED, banned.fit());
        assertNull(banned.expectedWon());
    }

    @Test public void routePreservesVisitAndUsesChosenEntrance() {
        Option first = compare(SampleTrip.LOTS, 2250, false, 120, true).get(0);
        RouteRequest request = routeRequest(SampleTrip.VISIT, first).get();
        assertEquals("스타필드 코엑스몰", request.visit());
        assertEquals("대안 A 입구 (시연 좌표)", request.entrance().label());
        assertEquals(37.51400, request.entrance().latitude(), 0.000001);
        assertEquals(127.06100, request.entrance().longitude(), 0.000001);
    }

    @Test public void tariffRoundsPartialUnitAfterDiscount() {
        Tariff tariff = new Tariff(15, 1500, 120, "테스트 데이터");
        assertEquals(1500, tariff.quote(121, true));
        assertEquals(6000, tariff.quote(60, false));
    }

    private Option find(List<Option> options, String name) {
        return options.stream().filter(option -> option.lot().name().equals(name)).findFirst().get();
    }
}
