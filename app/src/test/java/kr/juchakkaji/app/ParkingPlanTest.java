package kr.juchakkaji.app;

import org.junit.Test;

import static kr.juchakkaji.app.VehicleProfile.DriveType.ELECTRIC;
import static kr.juchakkaji.app.VehicleProfile.DriveType.NON_ELECTRIC;

import java.util.Arrays;
import java.util.List;

import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

public final class ParkingPlanTest {
    @Test public void purchaseConditionMakesAttachedLotFirst() {
        List<Option> options = compare(SampleTrip.LOTS, 1850, NON_ELECTRIC, 120, true);
        assertEquals("코엑스 지하주차장", options.get(0).lot().name());
        assertEquals(Integer.valueOf(12000), options.get(0).baseWon());
        assertEquals(Integer.valueOf(0), options.get(0).expectedWon());
    }

    @Test public void noPurchaseConditionShowsFullPriceAndKeepsUsableOnSiteOptionFirst() {
        List<Option> options = compare(SampleTrip.LOTS, 1850, NON_ELECTRIC, 120, false);
        assertEquals("코엑스 지하주차장", options.get(0).lot().name());
        assertEquals(Integer.valueOf(12000), find(options, "코엑스 지하주차장").expectedWon());
    }

    @Test public void heightRestrictionWinsOverFreeParking() {
        List<Option> options = compare(SampleTrip.LOTS, 2250, NON_ELECTRIC, 120, true);
        assertEquals("대안 A · 시연 주차장", options.get(0).lot().name());
        assertEquals(Integer.valueOf(6000), options.get(0).expectedWon());
        Option attached = find(options, "코엑스 지하주차장");
        assertEquals(Fit.RESTRICTED, attached.fit());
        assertFalse(routeRequest(SampleTrip.VISIT, attached).isPresent());
    }

    @Test public void missingAndBoundaryHeightNeedConfirmation() {
        Option unknownLot = find(compare(SampleTrip.LOTS, 1850, NON_ELECTRIC, 60, false),
                "대안 B · 시연 주차장");
        Option unknownVehicle = compare(SampleTrip.LOTS, null, NON_ELECTRIC, 60, false).get(0);
        Option exactHeight = find(compare(SampleTrip.LOTS, 2200, NON_ELECTRIC, 60, false),
                "코엑스 지하주차장");
        assertEquals(Fit.NEEDS_CHECK, unknownLot.fit());
        assertEquals(Fit.NEEDS_CHECK, unknownVehicle.fit());
        assertEquals(Fit.NEEDS_CHECK, exactHeight.fit());
        assertFalse(routeRequest(SampleTrip.VISIT, unknownLot).isPresent());
    }

    @Test public void unknownEvPolicyAndEvBanAreDifferent() {
        Option unknown = find(compare(SampleTrip.LOTS, 1850, ELECTRIC, 120, true), "코엑스 지하주차장");
        Lot restricted = new Lot("EV 제한 샘플", "기계식 예시", true, 2200, EvEntry.RESTRICTED,
                null, null, "테스트 데이터");
        Option banned = compare(Arrays.asList(restricted), 1850, ELECTRIC, 60, false).get(0);
        assertEquals(Fit.NEEDS_CHECK, unknown.fit());
        assertEquals(Fit.RESTRICTED, banned.fit());
        assertNull(banned.expectedWon());
    }

    @Test public void routePreservesVisitAndUsesChosenEntrance() {
        Option first = compare(SampleTrip.LOTS, 2250, NON_ELECTRIC, 120, true).get(0);
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

    @Test public void entranceReadyLotPrecedesFreeLotWithoutEntrance() {
        Lot free = new Lot("입구 없는 무료 시설", "합성", true, 2500, EvEntry.ALLOWED,
                new Tariff(60, 0, 0, ""), null, "합성 테스트");
        Lot paid = new Lot("입구 있는 유료 시설", "합성", false, 2500, EvEntry.ALLOWED,
                new Tariff(60, 3000, 0, ""), new Entrance("합성 입구", 37, 127), "합성 테스트");
        List<Option> options = compare(Arrays.asList(free, paid), 1850, NON_ELECTRIC, 120, true);
        assertEquals(paid, options.get(0).lot());
        assertEquals(Integer.valueOf(6000), options.get(0).expectedWon());
        Option unavailable = options.get(1);
        assertEquals(Integer.valueOf(0), unavailable.expectedWon());
        assertEquals(Fit.MATCHES_RECORDED_RULES, unavailable.fit());
        assertEquals("입구 정보 미확인", unavailable.requestAvailability().reason());
        assertFalse(unavailable.requestAvailability().available());
        assertFalse(routeRequest(SampleTrip.VISIT, unavailable).isPresent());
        assertTrue(routeRequest(SampleTrip.VISIT, options.get(0)).isPresent());
        assertEquals(1, requestableCount(options));
        assertEquals("요청 가능한 후보 1곳", candidateSummary(options));
    }

    @Test public void allUnavailableLotsHaveNoRequestableEntrance() {
        Lot missing = new Lot("입구 누락", "합성", true, 2500, EvEntry.ALLOWED,
                null, null, "합성 테스트");
        Lot restricted = new Lot("높이 제한", "합성", true, 1800, EvEntry.ALLOWED,
                null, new Entrance("합성 입구", 37, 127), "합성 테스트");
        Lot unknown = new Lot("높이 미확인", "합성", true, null, EvEntry.UNKNOWN,
                null, new Entrance("합성 입구", 37, 127), "합성 테스트");
        List<Option> options = compare(Arrays.asList(missing, restricted, unknown), 1850, NON_ELECTRIC, 120, true);
        assertEquals(0, requestableCount(options));
        assertEquals("요청 가능한 입구 없음 · 요청 가능한 후보 0곳", candidateSummary(options));
        assertEquals(Fit.MATCHES_RECORDED_RULES, find(options, "입구 누락").fit());
        assertEquals("입구 정보 미확인", find(options, "입구 누락").requestAvailability().reason());
        assertEquals("표기 높이 제한 초과", find(options, "높이 제한").requestAvailability().reason());
        assertEquals("높이 여유 또는 제한 정보 확인 필요", find(options, "높이 미확인").requestAvailability().reason());
        for (Option option : options) {
            assertFalse(option.requestAvailability().available());
            assertFalse(routeRequest(SampleTrip.VISIT, option).isPresent());
        }
    }

    @Test public void emptyLotsHaveNoFacilityInformation() {
        List<Option> options = compare(java.util.Collections.emptyList(), 1850, NON_ELECTRIC, 120, true);
        assertTrue(options.isEmpty());
        assertEquals(0, requestableCount(options));
        assertEquals("시설 정보 없음 · 요청 가능한 후보 0곳", candidateSummary(options));
        assertFalse(options.stream().anyMatch(option -> routeRequest(SampleTrip.VISIT, option).isPresent()));
    }

    private Option find(List<Option> options, String name) {
        return options.stream().filter(option -> option.lot().name().equals(name)).findFirst().get();
    }
}
