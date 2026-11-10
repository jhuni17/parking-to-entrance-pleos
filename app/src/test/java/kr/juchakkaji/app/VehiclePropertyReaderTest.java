package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.EnumMap;
import static kr.juchakkaji.app.VehiclePropertyReader.*;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

/** Synthetic source, never constructs Car/CarPropertyValue or injects a finished profile. */
public final class VehiclePropertyReaderTest {
    static final class Fake implements Source {
        final EnumMap<Property, Response> responses = new EnumMap<>(Property.class);
        final EnumMap<Property, Integer> calls = new EnumMap<>(Property.class);
        Property failure;
        Fake() {
            put(Property.MAKE, "OTHER"); put(Property.MODEL, "Synthetic"); put(Property.YEAR, 2025);
            put(Property.DIMENSIONS, new Integer[]{1850, 4700, 1990});
            put(Property.FUEL, new Integer[]{FuelType.UNLEADED});
        }
        void put(Property p, Object value) { responses.put(p, new Response(true, value)); }
        public Response read(Property p) {
            calls.merge(p, 1, Integer::sum);
            if (p == failure) throw new IllegalStateException("synthetic property failure");
            return responses.get(p);
        }
        VehicleProfile profile() {
            VehicleProfile profile = VehiclePropertyReader.read(this);
            for (Property p : Property.values()) assertEquals(p.name(), Integer.valueOf(1), calls.get(p));
            return profile;
        }
    }
    private static Object wrong(Property p) {
        return switch (p) {
            case MAKE, MODEL -> 17;
            case YEAR -> "2025";
            case DIMENSIONS, FUEL -> new int[]{1850, 4700, 1990};
        };
    }
    private static Fake malformedModel(int height) {
        Fake f = new Fake(); f.put(Property.MODEL, 17);
        f.put(Property.DIMENSIONS, new Integer[]{height, 4700, 1990}); return f;
    }
    private static Fake catalog() {
        Fake f = new Fake(); f.put(Property.MAKE, "HYUNDAI"); f.put(Property.MODEL, "IONIQ 5 N");
        f.responses.remove(Property.DIMENSIONS); return f;
    }
    @Test public void everyPropertyFailureIsIsolatedAndReadExactlyOnce() {
        for (Property p : Property.values()) for (int mode = 0; mode < 5; mode++) {
            Fake f = new Fake();
            switch (mode) {
                case 0 -> f.responses.remove(p); // null response
                case 1 -> f.responses.put(p, new Response(false, f.responses.get(p).value()));
                case 2 -> f.put(p, wrong(p));
                case 3 -> f.failure = p;
                case 4 -> f.put(p, null);
                default -> throw new AssertionError();
            }
            VehicleProfile v = f.profile();
            var state = new VehicleRefreshState();
            state.activate(); assertTrue(state.start(0));
            assertTrue(state.beginRead(state.generation(), 0));
            var result = VehicleReadResult.connected(v);
            assertEquals(VehicleReadResult.State.CONNECTED, result.state());
            assertEquals(VehicleReadResult.Session.Action.COMPLETE,
                    state.accept(state.generation(), 0, result, 1));
            assertSame(v, state.vehicle()); assertFalse(state.reading());
            assertFalse(state.beginRead(state.generation(), 1));
            assertEquals(VehicleReadResult.Session.Action.IGNORE, state.expire(state.generation(), 3000));
            assertEquals(p == Property.MODEL ? "" : "Synthetic", v.model());
            assertEquals(p == Property.DIMENSIONS ? null : Integer.valueOf(1850), v.heightMm());
            assertEquals(p == Property.DIMENSIONS ? null : Integer.valueOf(1990), v.widthMm());
            assertEquals(p == Property.FUEL ? VehicleProfile.DriveType.NEEDS_CHECK
                    : VehicleProfile.DriveType.NON_ELECTRIC, v.drive());
            assertFalse(v.catalogHeight()); assertFalse(v.catalogWidth());
            // Identity values also have an observable effect on the exact catalog match.
            if (p == Property.MAKE || p == Property.MODEL || p == Property.YEAR) {
                Fake c = catalog();
                c.responses.put(p, f.responses.get(p)); c.failure = f.failure;
                VehicleProfile missingIdentity = c.profile();
                assertNull(missingIdentity.heightMm()); assertNull(missingIdentity.widthMm());
                assertFalse(missingIdentity.catalogHeight()); assertFalse(missingIdentity.catalogWidth());
            }
        }
    }
    @Test public void normalAndCatalogValuesPreserveTypesAndProvenance() {
        VehicleProfile normal = new Fake().profile();
        assertEquals("Synthetic", normal.model()); assertEquals(Integer.valueOf(1850), normal.heightMm());
        assertEquals(Integer.valueOf(1990), normal.widthMm());
        assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, normal.drive());
        Fake c = catalog(); VehicleProfile supplemented = c.profile();
        assertEquals(Integer.valueOf(1585), supplemented.heightMm());
        assertEquals(Integer.valueOf(1940), supplemented.widthMm());
        assertTrue(supplemented.catalogHeight()); assertTrue(supplemented.catalogWidth());
        String details = RecommendationExplanation.create(supplemented, false, java.util.List.of()).vehicleDetails();
        assertTrue(details.contains("제조사 기본 제원")); assertTrue(details.contains(VehicleProfile.CATALOG_SOURCE));
        c = catalog(); c.put(Property.DIMENSIONS, new Integer[]{1850, 4700, 1990});
        VehicleProfile hardware = c.profile();
        assertEquals(Integer.valueOf(1850), hardware.heightMm()); assertEquals(Integer.valueOf(1990), hardware.widthMm());
        assertFalse(hardware.catalogHeight()); assertFalse(hardware.catalogWidth());
        Fake electric = new Fake(); electric.put(Property.FUEL, new Integer[]{FuelType.ELECTRIC});
        assertEquals(VehicleProfile.DriveType.ELECTRIC, electric.profile().drive());
        for (Property p : new Property[]{Property.DIMENSIONS, Property.FUEL}) {
            Fake arrays = new Fake(); arrays.put(p, new String[]{"1850", "4700", "1990"});
            VehicleProfile v = arrays.profile();
            if (p == Property.DIMENSIONS) assertNull(v.heightMm());
            else assertEquals(VehicleProfile.DriveType.NEEDS_CHECK, v.drive());
        }
    }
    private static void complete(VehicleRefreshStateTest.Screen s, Fake f) {
        VehicleReadResult result = VehicleReadResult.connected(f.profile());
        assertEquals(VehicleReadResult.State.CONNECTED, result.state());
        assertEquals(VehicleReadResult.Session.Action.COMPLETE,
                s.state.accept(s.state.generation(), 0, result, s.now));
        s.refresh();
        assertFalse(s.state.reading()); assertNull(s.retry);
        assertFalse(s.state.beginRead(s.state.generation(), 1));
        assertEquals(VehicleReadResult.Session.Action.IGNORE,
                s.state.expire(s.state.generation(), 3000));
    }
    @Test public void malformedModelStillDrivesFareExplanationAndSingleConfirmedRoute() {
        for (Priority priority : Priority.values()) for (int outcome = 0; outcome < 4; outcome++) {
            var s = new VehicleRefreshStateTest.Screen(priority);
            complete(s, malformedModel(1850));
            assertEquals("", s.state.vehicle().model());
            assertEquals(Integer.valueOf(1990), s.state.vehicle().widthMm());
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, s.state.vehicle().drive());
            assertEquals(2, requestableCount(s.options()));
            assertSame(SampleTrip.LOTS.get(0), s.options().get(0).lot());
            assertEquals(Integer.valueOf(0), s.options().get(0).expectedWon());
            assertEquals(Fit.MATCHES_RECORDED_RULES, s.coex().fit());
            assertEquals("", s.explanation().cardHeadline());
            assertTrue(s.explanation().vehicleDetails().contains("1850mm · 차량 속성값"));
            var oldOption = s.options().get(0);
            var old = EntranceConfirmation.create(s.nav, oldOption, 120, true).orElseThrow();
            assertEquals(Integer.valueOf(0), old.expectedWon());
            assertTrue(s.fake.requests.isEmpty());
            assertTrue(s.start());
            assertTrue(s.nav.select(oldOption).isEmpty());
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(old.selection()));
            complete(s, malformedModel(2250));
            assertEquals(Fit.RESTRICTED, s.coex().fit());
            assertFalse(s.coex().requestAvailability().available()); assertTrue(s.nav.select(s.coex()).isEmpty());
            assertEquals(1, requestableCount(s.options()));
            assertSame(SampleTrip.LOTS.get(1), s.options().get(0).lot());
            assertEquals(Integer.valueOf(6000), s.options().get(0).expectedWon());
            assertEquals("코엑스 지하주차장 · 높이 초과", s.explanation().cardHeadline());
            var preview = EntranceConfirmation.create(s.nav, s.options().get(0), 120, true).orElseThrow();
            assertEquals(Integer.valueOf(6000), preview.expectedWon());
            assertTrue(s.fake.requests.isEmpty());
            s.fake.echo = true;
            s.fake.result = outcome == 0 ? NavigationPort.Result.ACCEPTED
                    : outcome == 1 ? NavigationPort.Result.SIMULATED : NavigationPort.Result.REJECTED;
            s.fake.throwsFailure = outcome == 3;
            var expected = outcome == 0 ? ParkingNavigation.Status.ACCEPTED
                    : outcome == 1 ? ParkingNavigation.Status.SIMULATED : ParkingNavigation.Status.REJECTED;
            assertEquals(expected, s.nav.confirm(preview.selection()));
            assertEquals(expected, s.nav.lastRequest().outcome());
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(preview.selection()));
            assertEquals(1, s.fake.requests.size());
            assertEquals(SampleTrip.VISIT, s.fake.requests.get(0).visit());
            assertEquals(SampleTrip.VISIT, s.nav.visit().name());
            assertEquals(37.51400, s.fake.requests.get(0).entrance().latitude(), 0);
            assertEquals(127.06100, s.fake.requests.get(0).entrance().longitude(), 0);
        }
    }
    @Test public void missingDimensionsBlockAndBadFuelRemainsUnknown() {
        for (Priority priority : Priority.values()) for (int mode = 0; mode < 4; mode++) {
            var s = new VehicleRefreshStateTest.Screen(priority);
            Fake f = new Fake();
            if (mode == 0) f.responses.put(Property.DIMENSIONS, new Response(false, new Integer[]{1850, 4700, 1990}));
            if (mode == 1) f.put(Property.DIMENSIONS, new int[]{1850, 4700, 1990});
            if (mode == 2) f.put(Property.FUEL, "UNLEADED");
            if (mode == 3) f.put(Property.FUEL, new Integer[]{FuelType.ELECTRIC});
            complete(s, f);
            assertEquals(Fit.NEEDS_CHECK, s.coex().fit());
            if (mode < 2) {
                assertNull(s.state.vehicle().heightMm()); assertNull(s.state.vehicle().widthMm());
                assertEquals(0, requestableCount(s.options()));
                assertTrue(s.options().stream().allMatch(o -> o.fit() == Fit.NEEDS_CHECK));
                assertTrue(s.explanation().vehicleDetails().contains("차량 높이 확인 필요"));
                assertTrue(s.explanation().vehicleDetails().contains("차량 너비 확인 필요"));
            } else {
                assertEquals(Integer.valueOf(1850), s.state.vehicle().heightMm());
                assertEquals(mode == 2 ? VehicleProfile.DriveType.NEEDS_CHECK : VehicleProfile.DriveType.ELECTRIC,
                        s.state.vehicle().drive());
                assertEquals(1, requestableCount(s.options()));
                assertSame(SampleTrip.LOTS.get(1), s.options().get(0).lot());
                assertEquals(Integer.valueOf(6000), s.options().get(0).expectedWon());
                assertTrue(s.explanation().vehicleDetails().contains(mode == 2 ? "연료 유형 확인 필요" : "연료 유형 전기차"));
                assertTrue(s.explanation().cardHeadline().contains(mode == 2 ? "연료 유형 확인 필요" : "전기차 입차 조건 미확인"));
            }
            assertTrue(s.fake.requests.isEmpty());
        }
    }
}
