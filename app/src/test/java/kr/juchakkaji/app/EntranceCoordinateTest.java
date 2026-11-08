package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

/** Synthetic AAOS inputs and coordinate-only fixture copies; no SDK observations. */
public final class EntranceCoordinateTest {
    private VehicleProfile vehicle(Integer height, int fuel) {
        return VehicleProfile.fromHardware("OTHER", "Synthetic", 2025,
                height == null ? null : new Integer[]{height, 0, 1900}, new Integer[]{fuel});
    }
    private Lot copy(Lot lot, Entrance entrance) {
        return new Lot(lot.name(), lot.type(), lot.attached(), lot.heightLimitMm(),
                lot.evEntry(), lot.tariff(), entrance, lot.provenance());
    }
    private Lot coordinates(int index, double latitude, double longitude) {
        Lot lot = SampleTrip.LOTS.get(index);
        return copy(lot, new Entrance(lot.entrance().label(), latitude, longitude));
    }
    private List<Option> options(ParkingNavigation nav, VehicleProfile vehicle, Priority priority) {
        return nav.options(vehicle.heightMm(), vehicle.drive(), 120, true, priority);
    }
    private Option attached(List<Option> options) {
        return options.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
    }
    @Test public void invalidNumbersAreBlockedThroughEveryRequestPath() {
        double[][] invalid = {
                {Double.NaN, 127}, {Double.POSITIVE_INFINITY, 127}, {Double.NEGATIVE_INFINITY, 127},
                {Math.nextUp(90.0), 127}, {Math.nextDown(-90.0), 127},
                {37, Double.NaN}, {37, Double.POSITIVE_INFINITY}, {37, Double.NEGATIVE_INFINITY},
                {37, Math.nextUp(180.0)}, {37, Math.nextDown(-180.0)}
        };
        for (double[] pair : invalid) {
            Lot invalidLot = coordinates(0, pair[0], pair[1]);
            assertFalse(invalidLot.entrance().hasValidCoordinates());
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, List.of(invalidLot));
            for (Priority priority : Priority.values()) {
                List<Option> options = options(nav, vehicle(1850, FuelType.UNLEADED), priority);
                Option option = options.get(0);
                assertEquals(Fit.MATCHES_RECORDED_RULES, option.fit());
                assertEquals(Integer.valueOf(0), option.expectedWon());
                assertEquals("입구 좌표 확인 필요", option.requestAvailability().reason());
                assertEquals(0, requestableCount(options));
                assertTrue(routeRequest(SampleTrip.VISIT, option).isEmpty());
                assertTrue(nav.select(option).isEmpty());
            }
            assertEquals(0, fake.requests.size());
        }
    }
    @Test public void inclusiveBoundariesAndZeroArePreservedWithoutSwappingOrClamping() {
        for (double latitude : new double[]{-90, 0, 90}) {
            for (double longitude : new double[]{-180, 0, 180}) {
                Lot lot = coordinates(0, latitude, longitude);
                assertTrue(lot.entrance().hasValidCoordinates());
                Option option = compare(List.of(lot), 1850, VehicleProfile.DriveType.NON_ELECTRIC,
                        120, true).get(0);
                Entrance requested = routeRequest(SampleTrip.VISIT, option).orElseThrow().entrance();
                assertSame(lot.entrance(), requested);
                assertEquals(latitude, requested.latitude(), 0.0);
                assertEquals(longitude, requested.longitude(), 0.0);
                assertEquals(1, requestableCount(List.of(option)));
            }
        }
    }
    @Test public void bothPrioritiesExplainCoordinateFailureAndOnlyConfirmationRequestsAlternative() {
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            fake.echo = true;
            fake.result = NavigationPort.Result.REJECTED;
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, List.of(coordinates(0, Double.NaN, 127.06000),
                    SampleTrip.LOTS.get(1), SampleTrip.LOTS.get(2)));
            NavigationPort.Destination original = nav.visit();
            VehicleProfile vehicle = vehicle(1850, FuelType.UNLEADED);
            assertEquals(Integer.valueOf(1850), vehicle.heightMm());
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, vehicle.drive());
            List<Option> options = options(nav, vehicle, priority);
            assertEquals(SampleTrip.LOTS.get(1), options.get(0).lot());
            assertEquals(Integer.valueOf(6000), options.get(0).expectedWon());
            assertEquals(1, requestableCount(options));
            Option coex = attached(options);
            assertEquals(Fit.MATCHES_RECORDED_RULES, coex.fit());
            assertEquals(Integer.valueOf(12000), coex.baseWon());
            assertEquals(Integer.valueOf(0), coex.expectedWon());
            assertTrue(routeRequest(SampleTrip.VISIT, coex).isEmpty());
            assertTrue(nav.select(coex).isEmpty());
            RecommendationExplanation explanation = RecommendationExplanation.create(vehicle,
                    new VehicleHeight(), false, options, priority);
            assertEquals("코엑스 지하주차장 · 입구 좌표 확인 필요", explanation.headline());
            assertFalse(explanation.headline().contains("금지"));
            assertFalse(explanation.headline().contains("제한"));
            assertTrue(explanation.vehicleDetails().contains("1850mm · 차량 속성값"));
            assertEquals(0, fake.requests.size());
            ParkingNavigation.Selection selection = nav.select(options.get(0)).orElseThrow();
            assertEquals(0, fake.requests.size());
            assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(selection));
            assertSame(original, nav.visit());
            assertEquals(1, fake.requests.size());
            RouteRequest request = fake.requests.get(0);
            assertEquals(SampleTrip.VISIT, request.visit());
            assertEquals(37.51400, request.entrance().latitude(), 0.0);
            assertEquals(127.06100, request.entrance().longitude(), 0.0);
            assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(selection));
            assertEquals(1, fake.requests.size());
        }
    }
    @Test public void tallVehicleAndInvalidAlternativeLeaveNoRequestsAndExistingVerdictsRemain() {
        ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        fake.visit(SampleTrip.VISIT, List.of(SampleTrip.LOTS.get(0),
                coordinates(1, 37.51400, Double.NaN), SampleTrip.LOTS.get(2)));
        for (Priority priority : Priority.values()) {
            List<Option> before = options(nav, vehicle(1850, FuelType.UNLEADED), priority);
            assertEquals(1, requestableCount(before));
            nav.invalidateSelections();
            List<Option> tall = options(nav, vehicle(2250, FuelType.UNLEADED), priority);
            assertEquals(Fit.RESTRICTED, attached(tall).fit());
            assertEquals("표기 높이 제한 초과", attached(tall).requestAvailability().reason());
            Option alternative = tall.stream().filter(o -> o.lot().equals(nav.visit().lots().get(1)))
                    .findFirst().orElseThrow();
            assertEquals(Fit.MATCHES_RECORDED_RULES, alternative.fit());
            assertEquals("입구 좌표 확인 필요", alternative.requestAvailability().reason());
            assertEquals(0, requestableCount(tall));
            assertEquals("요청 가능한 입구 없음 · 요청 가능한 후보 0곳", candidateSummary(tall));
            for (Option option : tall) {
                assertTrue(nav.select(option).isEmpty());
                assertTrue(routeRequest(SampleTrip.VISIT, option).isEmpty());
            }
            List<Option> missingHeight = options(nav, vehicle(null, FuelType.UNLEADED), priority);
            assertEquals(Fit.NEEDS_CHECK, attached(missingHeight).fit());
            assertEquals(0, requestableCount(missingHeight));
            List<Option> electric = options(nav, vehicle(1850, FuelType.ELECTRIC), priority);
            assertEquals(Fit.NEEDS_CHECK, attached(electric).fit());
            assertEquals("전기차 입차 조건 확인 필요", attached(electric).requestAvailability().reason());
            assertEquals(0, requestableCount(electric));
            for (List<Option> blocked : List.of(missingHeight, electric)) {
                for (Option option : blocked) {
                    assertTrue(nav.select(option).isEmpty());
                    assertTrue(routeRequest(SampleTrip.VISIT, option).isEmpty());
                }
            }
            Lot noEntrance = copy(SampleTrip.LOTS.get(1), null);
            fake.visit(SampleTrip.VISIT, List.of(noEntrance));
            Option missing = options(nav, vehicle(1850, FuelType.UNLEADED), priority).get(0);
            assertEquals(Fit.MATCHES_RECORDED_RULES, missing.fit());
            assertEquals("입구 정보 미확인", missing.requestAvailability().reason());
            assertEquals(0, requestableCount(List.of(missing)));
            assertTrue(nav.select(missing).isEmpty());
            fake.visit(SampleTrip.VISIT, List.of(SampleTrip.LOTS.get(0),
                    coordinates(1, 37.51400, Double.NaN), SampleTrip.LOTS.get(2)));
        }
        assertEquals(0, fake.requests.size());
    }
}
