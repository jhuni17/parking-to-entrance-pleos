package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class RecommendationExplanationTest {
    private VehicleProfile hardware(Integer height, Integer[] fuels) {
        return VehicleProfile.fromHardware("OTHER", "Test", 2025,
                height == null ? null : new Integer[]{height, 0, 1900}, fuels);
    }
    private VehicleProfile conventional(int height) {
        return hardware(height, new Integer[]{FuelType.UNLEADED});
    }
    private List<ParkingPlan.Option> options(VehicleProfile vehicle) {
        return ParkingPlan.compare(SampleTrip.LOTS, vehicle.heightMm(), vehicle.drive(), 120, false);
    }
    private RecommendationExplanation explain(VehicleProfile vehicle) {
        return RecommendationExplanation.create(vehicle, false, options(vehicle));
    }
    private ParkingPlan.Option attached(List<ParkingPlan.Option> options) {
        return options.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
    }
    @Test public void normalHardwareKeepsAttachedPriceAndAddsDetailsOnly() {
        VehicleProfile vehicle = conventional(1850);
        List<ParkingPlan.Option> options = options(vehicle);
        assertEquals(SampleTrip.LOTS.get(0), options.get(0).lot());
        assertEquals(Integer.valueOf(12000), options.get(0).expectedWon());
        assertEquals(Integer.valueOf(6000), options.get(1).expectedWon());
        assertEquals(ParkingPlan.Fit.MATCHES_RECORDED_RULES, options.get(0).fit());
        assertEquals(2, ParkingPlan.requestableCount(options));
        RecommendationExplanation result = RecommendationExplanation.create(vehicle, false, options);
        assertEquals("", result.headline());
        assertTrue(result.vehicleDetails().contains("1850mm · 차량 속성값"));
        assertTrue(result.vehicleDetails().contains("연료 유형 비전기차"));
    }
    @Test public void tallHardwareExplainsExistingRestrictionAndLimit() {
        VehicleProfile vehicle = conventional(2250);
        List<ParkingPlan.Option> options = options(vehicle);
        assertEquals(SampleTrip.LOTS.get(1), options.get(0).lot());
        assertEquals(ParkingPlan.Fit.RESTRICTED, attached(options).fit());
        assertFalse(attached(options).requestAvailability().available());
        assertEquals(Integer.valueOf(2200), attached(options).lot().heightLimitMm());
        assertEquals(1, ParkingPlan.requestableCount(options));
        assertEquals("코엑스 지하주차장 · 높이 초과", explain(vehicle).headline());
        assertTrue(explain(vehicle).vehicleDetails().contains("2250mm"));
    }
    @Test public void electricUnknownPolicyAndUnknownFuelAreNotBansOrConventional() {
        for (Integer[] fuels : new Integer[][]{ {FuelType.ELECTRIC}, null }) {
            VehicleProfile vehicle = hardware(1850, fuels);
            List<ParkingPlan.Option> options = options(vehicle);
            assertEquals(SampleTrip.LOTS.get(1), options.get(0).lot());
            assertEquals(ParkingPlan.Fit.NEEDS_CHECK, attached(options).fit());
            assertFalse(attached(options).requestAvailability().available());
            assertEquals(1, ParkingPlan.requestableCount(options));
            RecommendationExplanation result = explain(vehicle);
            assertFalse(result.headline().contains("금지"));
            assertFalse(result.headline().contains("제한"));
            if (fuels == null) {
                assertEquals(VehicleProfile.DriveType.NEEDS_CHECK, vehicle.drive());
                assertTrue(result.vehicleDetails().contains("연료 유형 확인 필요"));
                assertFalse(result.vehicleDetails().contains("비전기차"));
                assertEquals("코엑스 지하주차장 · 차량 연료 유형 확인 필요", result.headline());
            } else {
                assertEquals(VehicleProfile.DriveType.ELECTRIC, vehicle.drive());
                assertTrue(result.vehicleDetails().contains("연료 유형 전기차"));
                assertEquals("코엑스 지하주차장 · 전기차 입차 조건 미확인", result.headline());
            }
        }
    }
    @Test public void exactCatalogFallbackIsReplacedByValidHardware() {
        for (Integer height : new Integer[]{null, 2250}) {
            VehicleProfile vehicle = VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5 N", 2025,
                    height == null ? null : new Integer[]{height, 0, 1940},
                    new Integer[]{FuelType.ELECTRIC});
            RecommendationExplanation result = explain(vehicle);
            assertEquals(Integer.valueOf(height == null ? 1585 : 2250), vehicle.heightMm());
            assertEquals(height == null, vehicle.catalogHeight());
            assertEquals(SampleTrip.LOTS.get(1), options(vehicle).get(0).lot());
            assertEquals(1, ParkingPlan.requestableCount(options(vehicle)));
            if (height == null) {
                assertTrue(result.vehicleDetails().contains("1585mm · 제조사 기본 제원"));
                assertTrue(result.vehicleDetails().contains(VehicleProfile.CATALOG_SOURCE));
                assertTrue(result.headline().contains("전기차 입차 조건 미확인"));
                assertEquals(ParkingPlan.Fit.NEEDS_CHECK, attached(options(vehicle)).fit());
            } else {
                assertTrue(result.vehicleDetails().contains("2250mm · 차량 속성값"));
                assertFalse(result.vehicleDetails().contains("제조사 기본 제원"));
                assertFalse(result.vehicleDetails().contains(VehicleProfile.CATALOG_SOURCE));
                assertTrue(result.headline().contains("높이 초과"));
                assertEquals(ParkingPlan.Fit.RESTRICTED, attached(options(vehicle)).fit());
            }
        }
    }
    @Test public void updatesClearOldValuesAndOnlyConfirmationRequestsSelectedEntrance() {
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("original", SampleTrip.LOTS);
        VehicleProfile normal = conventional(1850);
        RecommendationExplanation initial = RecommendationExplanation.create(normal, false,
                nav.options(normal.heightMm(), normal.drive(), 120, false));
        assertEquals("", initial.headline());
        assertTrue(initial.vehicleDetails().contains("1850mm"));
        RecommendationExplanation loading = RecommendationExplanation.create(normal, true, options(normal));
        assertEquals("", loading.headline());
        assertEquals("차량 정보 확인 중", loading.vehicleDetails());
        assertFalse(loading.vehicleDetails().contains("1850"));
        VehicleProfile tall = conventional(2250);
        List<ParkingPlan.Option> options = nav.options(tall.heightMm(), tall.drive(), 120, false);
        RecommendationExplanation current = RecommendationExplanation.create(tall, false, options);
        assertTrue(current.headline().contains("높이 초과"));
        // Even with old inputs still supplied, reading suppresses all previous evidence.
        current = RecommendationExplanation.create(tall, true, options);
        assertEquals("", current.headline());
        assertEquals("차량 정보 확인 중", current.vehicleDetails());
        assertFalse(current.vehicleDetails().contains("2250"));
        VehicleProfile unknown = hardware(null, null);
        options = nav.options(unknown.heightMm(), unknown.drive(), 120, false);
        current = RecommendationExplanation.create(unknown, false, options);
        assertEquals("", current.headline());
        assertTrue(current.vehicleDetails().contains("차량 높이 확인 필요"));
        assertFalse(current.vehicleDetails().contains("2250"));
        assertEquals(0, ParkingPlan.requestableCount(options));
        for (ParkingPlan.Option option : options) assertTrue(nav.select(option).isEmpty());
        fake.visit("empty", List.of());
        current = RecommendationExplanation.create(unknown, false, nav.options(null, unknown.drive(), 120, false));
        assertEquals("시설 정보 없음 · 요청 가능한 후보 0곳", current.candidateSummary());
        assertEquals("", current.headline());
        assertEquals(0, fake.requests.size());
        fake.visit("new visit", SampleTrip.LOTS);
        options = nav.options(tall.heightMm(), tall.drive(), 120, false);
        RecommendationExplanation.create(tall, false, options);
        ParkingNavigation.Selection selection = nav.select(options.get(0)).orElseThrow();
        assertEquals(0, fake.requests.size());
        assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(selection));
        assertEquals(1, fake.requests.size());
        assertEquals("new visit", fake.requests.get(0).visit());
        assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0.0);
        assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0.0);
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
        assertEquals(1, fake.requests.size());
    }
    @Test public void explanationUsesSuppliedVerdictAndOrderWithoutRecalculation() {
        ParkingPlan.Option denied = new ParkingPlan.Option(SampleTrip.LOTS.get(0),
                ParkingPlan.Fit.NEEDS_CHECK, "입구 운영 확인 필요", 12000, 12000);
        ParkingPlan.Option alternative = new ParkingPlan.Option(SampleTrip.LOTS.get(1),
                ParkingPlan.Fit.MATCHES_RECORDED_RULES, "등록된 입차 조건 기준 충족", 6000, 6000);
        assertEquals("코엑스 지하주차장 · 입구 운영 확인 필요",
                RecommendationExplanation.create(conventional(1850), false, List.of(alternative, denied)).headline());
        assertEquals("", RecommendationExplanation.create(conventional(1850), false,
                List.of(denied, alternative)).headline());
    }
}
