package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static kr.juchakkaji.app.VehicleProfile.DriveType.*;

public final class VehicleHeightTest {
    private VehicleProfile hardware(int height) {
        return VehicleProfile.fromHardware("synthetic", "test", 2025,
                new Integer[]{height, 0, 1900}, new Integer[]{FuelType.UNLEADED});
    }
    private List<ParkingPlan.Option> options(VehicleProfile vehicle, VehicleHeight height) {
        return ParkingPlan.compare(SampleTrip.LOTS, height.effectiveMm(vehicle), vehicle.drive(), 120, false);
    }
    private ParkingPlan.Option coex(List<ParkingPlan.Option> options) {
        return options.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
    }
    @Test public void hardwareSupplementFlowsThroughExplanationAndConfirmedNavigation() {
        VehicleProfile original = hardware(1850);
        VehicleHeight height = new VehicleHeight();
        long session = height.start();
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("original visit", SampleTrip.LOTS);
        var before = nav.options(height.effectiveMm(original), original.drive(), 120, false);
        assertTrue(before.get(0).lot().attached());
        assertEquals(Integer.valueOf(12000), before.get(0).expectedWon());
        var old = nav.select(before.get(0)).orElseThrow();
        assertTrue(height.apply(session, "2250"));
        nav.invalidateSelections(); // MainActivity.refresh shares this update boundary.
        var after = nav.options(height.effectiveMm(original), original.drive(), 120, false);
        assertFalse(after.get(0).lot().attached());
        assertEquals(Integer.valueOf(6000), after.get(0).expectedWon());
        assertEquals(ParkingPlan.Fit.RESTRICTED, coex(after).fit());
        assertFalse(coex(after).requestAvailability().available());
        assertFalse(nav.select(coex(after)).isPresent());
        assertEquals(Integer.valueOf(1850), original.heightMm());
        assertEquals(Integer.valueOf(2250), height.confirmedMm());
        assertEquals(Integer.valueOf(2250), height.effectiveMm(original));
        String details = RecommendationExplanation.create(original, height, false, after).vehicleDetails();
        assertTrue(details.contains("차량 높이 1850mm · 차량 속성값"));
        assertTrue(details.contains("사용자 확인 총높이 2250mm · 사용자 직접 확인값"));
        assertTrue(details.contains("판단 적용 높이 2250mm"));
        assertFalse(details.contains("2250mm · 차량 속성값"));
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old));
        assertTrue(fake.requests.isEmpty());
        var beforeDelete = nav.select(after.get(0)).orElseThrow();
        assertTrue(height.clear(session));
        nav.invalidateSelections();
        RecommendationExplanation.create(original, height, false, options(original, height));
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(beforeDelete));
        assertTrue(fake.requests.isEmpty());
        assertTrue(height.apply(session, "2250"));
        nav.invalidateSelections();
        after = nav.options(height.effectiveMm(original), original.drive(), 120, false);
        var selected = nav.select(after.get(0)).orElseThrow();
        fake.result = NavigationPort.Result.REJECTED;
        assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(selected));
        assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selected));
        assertEquals(1, fake.requests.size());
        assertEquals("original visit", fake.requests.get(0).visit());
        assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0.000001);
        assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0.000001);
        assertTrue(height.clear(session));
        nav.invalidateSelections();
        RecommendationExplanation.create(original, height, false, options(original, height));
        assertEquals(1, fake.requests.size());
    }
    @Test public void missingHeightRecoveryDeletionAndBoundaryPreserveUnknownFuel() {
        VehicleProfile unknown = VehicleProfile.fromHardware(null, null, null, null, null);
        VehicleHeight height = new VehicleHeight();
        long session = height.start();
        assertEquals(0, ParkingPlan.requestableCount(options(unknown, height)));
        assertTrue(height.apply(session, "1850"));
        var recovered = options(unknown, height);
        assertEquals(1, ParkingPlan.requestableCount(recovered));
        assertFalse(recovered.get(0).lot().attached());
        assertEquals(ParkingPlan.Fit.NEEDS_CHECK, coex(recovered).fit());
        assertTrue(coex(recovered).reason().contains("연료"));
        String details = RecommendationExplanation.create(unknown, height, false, recovered).vehicleDetails();
        assertTrue(details.contains("연료 유형 확인 필요"));
        assertFalse(details.contains("비전기차"));
        assertTrue(height.clear(session));
        assertNull(height.effectiveMm(unknown));
        assertEquals(0, ParkingPlan.requestableCount(options(unknown, height)));
        assertTrue(height.apply(session, "2200"));
        assertEquals(ParkingPlan.Fit.NEEDS_CHECK, coex(options(hardware(1850), height)).fit());
    }
    @Test public void catalogAndHigherHardwareKeepTheirOriginalSources() {
        VehicleProfile catalog = VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5 N", 2025,
                null, new Integer[]{FuelType.ELECTRIC});
        VehicleHeight height = new VehicleHeight();
        assertTrue(height.apply(height.start(), "2250"));
        assertEquals(Integer.valueOf(1585), catalog.heightMm());
        assertEquals(Integer.valueOf(2250), height.effectiveMm(catalog));
        String details = RecommendationExplanation.create(catalog, height, false, options(catalog, height)).vehicleDetails();
        assertTrue(details.contains("1585mm · 제조사 기본 제원"));
        assertTrue(details.contains(VehicleProfile.CATALOG_SOURCE));
        assertTrue(details.contains("사용자 확인 총높이 2250mm"));
        assertTrue(details.contains("판단 적용 높이 2250mm"));
        assertEquals(Integer.valueOf(2400), height.effectiveMm(hardware(2400)));
        String higher = RecommendationExplanation.create(hardware(2400), height, false,
                options(hardware(2400), height)).vehicleDetails();
        assertTrue(higher.contains("2400mm · 차량 속성값"));
        assertTrue(higher.contains("사용자 확인 총높이 2250mm"));
        assertTrue(higher.contains("판단 적용 높이 2400mm"));
    }
    @Test public void inputPolicyInvalidAndCancelledInputDoNotMutateAndSessionsExpire() {
        VehicleHeight height = new VehicleHeight();
        long session = height.start();
        assertTrue(height.apply(session, "1000"));
        assertTrue(height.apply(session, "4000"));
        for (String invalid : new String[]{"", "0", "-1", "999", "4001", "1850.0", "text", "2147483648", " 1850"}) {
            assertFalse(invalid, height.apply(session, invalid));
            assertEquals(Integer.valueOf(4000), height.confirmedMm());
        }
        assertNull(VehicleHeight.parse(null));
        VehicleHeight.parse("2250"); // Draft validation/cancel has no apply operation.
        assertEquals(Integer.valueOf(4000), height.confirmedMm());
        height.stop();
        assertFalse(height.apply(session, "1850"));
        assertFalse(height.clear(session));
        long next = height.start();
        assertNull(height.confirmedMm());
        assertNull(height.effectiveMm(VehicleProfile.UNKNOWN));
        assertFalse(height.apply(session, "2250"));
        assertFalse(height.clear(session));
        assertTrue(height.apply(next, "1850"));
    }
}
