package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

/** Synthetic AAOS and fictional permitted vehicle widths; no runtime observations. */
public final class VehicleWidthTest {
    private Lot lot(boolean attached, WidthCondition width, Integer height, EvEntry ev, Entrance entrance) {
        return new Lot(attached ? "가상 부설" : "가상 대안", "합성", attached, height, ev,
                new Tariff(120, attached ? 0 : 6000, 0, ""), entrance,
                "합성 fixture · 2026-11-09", width);
    }
    private Lot lot(boolean attached, Integer maximum) {
        return lot(attached, new WidthCondition(maximum, "합성 · 2026-11-09"), 2200,
                EvEntry.ALLOWED, new Entrance("가상 입구", attached ? 37.5 : 37.6, 127.1));
    }
    private List<Lot> lots() { return List.of(lot(true, 2000), lot(false, 2200)); }
    private VehicleProfile vehicle(Integer width) {
        return hardware(new Integer[]{1850, 0, width});
    }
    private VehicleProfile hardware(Integer[] dimensions) {
        return VehicleProfile.fromHardware("OTHER", "Synthetic", 2025, dimensions,
                new Integer[]{FuelType.UNLEADED});
    }
    private List<Option> options(ParkingNavigation nav, VehicleProfile v, Priority priority) {
        return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, false, priority);
    }
    private Option attached(List<Option> options) {
        return options.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
    }
    @Test public void widthChangeFlowsFromHardwareThroughBothPrioritiesAndConfirmedNavigation() {
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            fake.echo = true;
            fake.result = NavigationPort.Result.REJECTED;
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit("원래 방문지", lots());
            NavigationPort.Destination original = nav.visit();
            VehicleProfile before = vehicle(1990);
            assertEquals(Integer.valueOf(1850), before.heightMm());
            assertEquals(Integer.valueOf(1990), before.widthMm());
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, before.drive());
            List<Option> first = options(nav, before, priority);
            assertTrue(first.get(0).lot().attached());
            assertEquals(2, requestableCount(first));
            ParkingNavigation.Selection stale = nav.select(first.get(0)).orElseThrow();
            nav.invalidateSelections(); // MainActivity.refresh expires selections on a new reading.
            VehicleProfile after = vehicle(2010);
            List<Option> changed = options(nav, after, priority);
            assertFalse(changed.get(0).lot().attached());
            assertEquals(Integer.valueOf(6000), changed.get(0).expectedWon());
            Option blocked = attached(changed);
            assertEquals(Fit.RESTRICTED, blocked.fit());
            assertEquals("최대 허용 차폭 초과", blocked.reason());
            assertEquals(Integer.valueOf(0), blocked.baseWon());
            assertEquals(Integer.valueOf(0), blocked.expectedWon());
            assertTrue(nav.select(blocked).isEmpty());
            assertTrue(routeRequest(original.name(), blocked).isEmpty());
            assertEquals(1, requestableCount(changed));
            RecommendationExplanation explanation = RecommendationExplanation.create(after,
                    new VehicleHeight(), false, changed, priority);
            assertEquals("가상 부설 · 최대 허용 차폭 초과", explanation.headline());
            assertFalse(explanation.headline().contains("\n"));
            assertTrue(explanation.vehicleDetails().contains("차량 너비 2010mm · 차량 속성값"));
            assertTrue(blocked.lot().widthDetails().contains("2000mm"));
            assertTrue(blocked.lot().widthDetails().contains("통로 폭/통과 안전 판단 아님"));
            assertEquals(0, fake.requests.size());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(stale));
            ParkingNavigation.Selection selection = nav.select(changed.get(0)).orElseThrow();
            assertEquals(0, fake.requests.size());
            assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(selection));
            assertSame(original, nav.visit());
            assertEquals(1, fake.requests.size());
            RouteRequest request = fake.requests.get(0);
            assertEquals("원래 방문지", request.visit());
            assertEquals(37.6, request.entrance().latitude(), 0);
            assertEquals(127.1, request.entrance().longitude(), 0);
            assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
            assertEquals(1, fake.requests.size());
        }
    }
    @Test public void inclusiveBoundaryAndUnknownWidthsRemainDistinctFromUnregistered() {
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit("합성", lots());
        assertEquals(Fit.MATCHES_RECORDED_RULES,
                attached(options(nav, vehicle(2000), Priority.ATTACHED_FIRST)).fit());
        for (Integer[] input : new Integer[][]{null, {}, {1850}, {1850, 0, null},
                {1850, 0, 0}, {1850, 0, -1}}) {
            VehicleProfile v = hardware(input);
            assertNull(v.widthMm());
            List<Option> result = options(nav, v, Priority.ATTACHED_FIRST);
            assertEquals(0, requestableCount(result));
            for (Option option : result) {
                assertEquals(Fit.NEEDS_CHECK, option.fit());
                assertEquals("차량 너비 확인 필요", option.reason());
                assertTrue(nav.select(option).isEmpty());
            }
        }
        for (Integer max : new Integer[]{null, 0, -1}) {
            Option option = compare(List.of(lot(true, max)), 1850, 1990,
                    VehicleProfile.DriveType.NON_ELECTRIC, 120, false, Priority.ATTACHED_FIRST).get(0);
            assertEquals(Fit.NEEDS_CHECK, option.fit());
            assertEquals("시설 최대 허용 차폭 확인 필요", option.reason());
            assertEquals(0, requestableCount(List.of(option)));
        }
        Lot legacy = new Lot("기존", "합성", true, 2200, EvEntry.ALLOWED,
                new Tariff(120, 0, 0, ""), new Entrance("입구", 37, 127), "합성");
        Option option = compare(List.of(legacy), 1850, VehicleProfile.DriveType.NON_ELECTRIC, 120, false).get(0);
        assertNull(legacy.widthCondition());
        assertEquals(Fit.MATCHES_RECORDED_RULES, option.fit());
        assertEquals(1, requestableCount(List.of(option)));
        assertTrue(legacy.widthDetails().startsWith("차폭 조건 미등록"));
        assertTrue(legacy.widthDetails().contains("뜻하지 않음"));
        for (Lot sample : SampleTrip.LOTS) assertNull(sample.widthCondition());
        assertEquals(0, fake.requests.size());
    }
    @Test public void independentCatalogSourcesAndExactModelRule() {
        VehicleProfile catalog = VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5 N", 2025,
                new Integer[]{1850}, new Integer[]{FuelType.UNLEADED});
        assertEquals(Integer.valueOf(1940), catalog.widthMm());
        assertFalse(catalog.catalogHeight());
        assertTrue(catalog.catalogWidth());
        String details = RecommendationExplanation.create(catalog, false, List.of()).vehicleDetails();
        assertTrue(details.contains("차량 높이 1850mm · 차량 속성값"));
        assertTrue(details.contains("차량 너비 1940mm · 제조사 기본 제원"));
        assertTrue(details.contains("너비 출처 " + VehicleProfile.CATALOG_SOURCE));
        assertFalse(details.contains("높이 출처"));
        VehicleProfile hardware = VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5 N", 2025,
                new Integer[]{1850, 0, 2010}, new Integer[]{FuelType.UNLEADED});
        assertEquals(Integer.valueOf(2010), hardware.widthMm());
        assertFalse(hardware.catalogWidth());
        assertFalse(RecommendationExplanation.create(hardware, false, List.of()).vehicleDetails().contains("너비 출처"));
        for (VehicleProfile v : List.of(catalog, hardware)) {
            List<Option> result = compare(lots(), v.heightMm(), v.widthMm(), v.drive(), 120, false, Priority.ATTACHED_FIRST);
            assertEquals(v == catalog, result.get(0).lot().attached());
        }
        for (VehicleProfile v : List.of(
                VehicleProfile.fromHardware("OTHER", "IONIQ 5 N", 2025, null, null),
                VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5", 2025, null, null),
                VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5 N", 2024, null, null))) {
            assertNull(v.widthMm());
            assertFalse(v.catalogWidth());
        }
    }
    @Test public void widthMatchDoesNotBypassExistingBlocksAndExplanationUsesSuppliedVerdict() {
        List<Lot> blocked = List.of(
                lot(true, new WidthCondition(2200, "합성"), 1800, EvEntry.ALLOWED, new Entrance("입구", 37, 127)),
                lot(false, new WidthCondition(2200, "합성"), 2200, EvEntry.UNKNOWN, new Entrance("입구", 37, 127)),
                lot(false, new WidthCondition(2200, "합성"), 2200, EvEntry.ALLOWED, new Entrance("입구", Double.NaN, 127)));
        assertEquals(Fit.RESTRICTED, compare(List.of(blocked.get(0)), 1850, 1990,
                VehicleProfile.DriveType.NON_ELECTRIC, 120, false, Priority.ATTACHED_FIRST).get(0).fit());
        Option ev = compare(List.of(blocked.get(1)), 1850, 1990,
                VehicleProfile.DriveType.ELECTRIC, 120, false, Priority.ATTACHED_FIRST).get(0);
        assertEquals(Fit.NEEDS_CHECK, ev.fit());
        assertEquals("전기차 입차 조건 확인 필요", ev.reason());
        Option coordinates = compare(List.of(blocked.get(2)), 1850, 1990,
                VehicleProfile.DriveType.NON_ELECTRIC, 120, false, Priority.ATTACHED_FIRST).get(0);
        assertEquals(Fit.MATCHES_RECORDED_RULES, coordinates.fit());
        assertEquals(0, requestableCount(List.of(ev, coordinates)));
        Option supplied = new Option(lot(true, 2200), Fit.RESTRICTED, "전달된 판정", 0, 0);
        Option alternative = new Option(lot(false, 2200), Fit.MATCHES_RECORDED_RULES, "", 6000, 6000);
        assertEquals("가상 부설 · 전달된 판정", RecommendationExplanation.create(vehicle(1990),
                false, List.of(alternative, supplied)).headline());
    }
}
