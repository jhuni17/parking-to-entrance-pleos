package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static kr.juchakkaji.app.VehicleProfile.DriveType.*;
import static org.junit.Assert.*;

/** Synthetic AAOS inputs and facility copies, not SDK/runtime observations. */
public final class EvPolicyFallbackTest {
    private VehicleProfile vehicle(Integer[] fuels) {
        return VehicleProfile.fromHardware("OTHER", "Synthetic", 2025,
                new Integer[]{1850, 0, 1900}, fuels);
    }
    private VehicleProfile electric() { return vehicle(new Integer[]{FuelType.ELECTRIC}); }
    private Lot policy(Lot lot, EvEntry policy) {
        return new Lot(lot.name(), lot.type(), lot.attached(), lot.heightLimitMm(), policy,
                lot.tariff(), lot.entrance(), lot.provenance(), lot.widthCondition());
    }
    private List<Option> options(ParkingNavigation nav, VehicleProfile v, Priority priority) {
        return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, true, priority);
    }
    private void blocked(ParkingNavigation nav, Option option) {
        assertFalse(option.requestAvailability().available());
        assertTrue(routeRequest(SampleTrip.VISIT, option).isEmpty());
        assertTrue(nav.select(option).isEmpty());
        assertTrue(EntranceConfirmation.create(nav, option, 120, true).isEmpty());
    }
    @Test public void bothConstructorsNormalizeOnlyMissingPolicy() {
        Lot source = SampleTrip.LOTS.get(0);
        for (EvEntry input : new EvEntry[]{null, EvEntry.ALLOWED, EvEntry.RESTRICTED, EvEntry.UNKNOWN}) {
            EvEntry expected = input == null ? EvEntry.UNKNOWN : input;
            assertSame(expected, policy(source, input).evEntry());
            Lot shortForm = new Lot(source.name(), source.type(), source.attached(), source.heightLimitMm(),
                    input, source.tariff(), source.entrance(), source.provenance());
            assertSame(expected, shortForm.evEntry());
            assertNull(shortForm.widthCondition());
        }
    }
    @Test public void hardwareFuelPolicyMatrix() {
        Integer[][] fuels = { {FuelType.ELECTRIC}, null, {}, {FuelType.ELECTRIC, FuelType.UNLEADED},
                {null}, {FuelType.UNKNOWN}, {FuelType.UNLEADED} };
        for (int row = 0; row < fuels.length; row++) {
            VehicleProfile v = vehicle(fuels[row]);
            assertEquals(row == 0 ? ELECTRIC : row == fuels.length - 1 ? NON_ELECTRIC : NEEDS_CHECK, v.drive());
            assertEquals(Integer.valueOf(1850), v.heightMm());
            for (EvEntry entry : new EvEntry[]{null, EvEntry.UNKNOWN, EvEntry.ALLOWED, EvEntry.RESTRICTED}) {
                ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
                ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
                fake.visit(SampleTrip.VISIT, List.of(policy(SampleTrip.LOTS.get(0), entry)));
                Option o = options(nav, v, Priority.ATTACHED_FIRST).get(0);
                Fit expected = v.drive() == NON_ELECTRIC || entry == EvEntry.ALLOWED ? Fit.MATCHES_RECORDED_RULES
                        : v.drive() == ELECTRIC && entry == EvEntry.RESTRICTED ? Fit.RESTRICTED : Fit.NEEDS_CHECK;
                assertEquals(expected, o.fit());
                assertEquals(expected == Fit.MATCHES_RECORDED_RULES, o.requestAvailability().available());
                if (expected == Fit.MATCHES_RECORDED_RULES) {
                    assertTrue(EntranceConfirmation.create(nav, o, 120, true).isPresent());
                } else blocked(nav, o);
                assertEquals(0, fake.requests.size());
            }
        }
    }
    @Test public void fuelChangeKeepsFreeQuoteButOnlyNewAlternativeConfirmationRequests() {
        for (Priority priority : Priority.values()) {
            // Accepted, rejected and throwing adapters all use the same confirmation boundary.
            for (int outcome = 0; outcome < 3; outcome++) {
                ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
                fake.echo = true;
                fake.result = outcome == 0 ? NavigationPort.Result.ACCEPTED : NavigationPort.Result.REJECTED;
                fake.throwsFailure = outcome == 2;
                ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
                Lot coex = policy(SampleTrip.LOTS.get(0), null);
                fake.visit(SampleTrip.VISIT, List.of(coex, SampleTrip.LOTS.get(1), SampleTrip.LOTS.get(2)));
                NavigationPort.Destination visit = nav.visit();
                VehicleProfile nonElectric = vehicle(new Integer[]{FuelType.UNLEADED});
                List<Option> before = options(nav, nonElectric, priority);
                assertEquals(coex, before.get(0).lot());
                assertEquals(Integer.valueOf(0), before.get(0).expectedWon());
                assertEquals(2, requestableCount(before));
                EntranceConfirmation old = EntranceConfirmation.create(nav, before.get(0), 120, true).orElseThrow();
                nav.invalidateSelections(); // Same invalidation as a vehicle update in MainActivity.
                assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old.selection()));
                assertEquals(0, fake.requests.size());
                VehicleProfile ev = electric();
                List<Option> after = options(nav, ev, priority);
                assertEquals(SampleTrip.LOTS.get(1), after.get(0).lot());
                assertEquals(Integer.valueOf(6000), after.get(0).expectedWon());
                assertEquals(1, requestableCount(after));
                Option unavailable = after.stream().filter(o -> o.lot().equals(coex)).findFirst().orElseThrow();
                assertEquals(Fit.NEEDS_CHECK, unavailable.fit());
                assertEquals(Integer.valueOf(12000), unavailable.baseWon());
                assertEquals(Integer.valueOf(0), unavailable.expectedWon());
                blocked(nav, unavailable);
                RecommendationExplanation explanation = RecommendationExplanation.create(ev, new VehicleHeight(), false, after, priority);
                assertEquals("코엑스 지하주차장 · 전기차 입차 조건 미확인", explanation.headline());
                assertEquals(explanation.headline(), explanation.cardHeadline());
                assertFalse(explanation.cardHeadline().contains("\n"));
                assertFalse(explanation.headline().contains("금지"));
                assertTrue(explanation.vehicleDetails().contains("1850mm · 차량 속성값"));
                EntranceConfirmation preview = EntranceConfirmation.create(nav, after.get(0), 120, true).orElseThrow();
                assertEquals(Integer.valueOf(6000), preview.expectedWon());
                assertEquals(SampleTrip.VISIT, preview.visit());
                assertEquals(0, fake.requests.size());
                ParkingNavigation.Status expected = outcome == 0 ? ParkingNavigation.Status.ACCEPTED : ParkingNavigation.Status.REJECTED;
                assertEquals(expected, nav.confirm(preview.selection()));
                assertEquals(1, fake.requests.size());
                RouteRequest request = fake.requests.get(0);
                assertEquals(SampleTrip.VISIT, request.visit());
                assertEquals(37.51400, request.entrance().latitude(), 0);
                assertEquals(127.06100, request.entrance().longitude(), 0);
                assertSame(visit, nav.visit());
                assertEquals(expected, nav.status());
                assertEquals(new ParkingNavigation.Receipt(request, expected), nav.lastRequest());
                assertEquals(ParkingNavigation.Status.STALE, nav.confirm(preview.selection()));
                assertEquals(1, fake.requests.size());
            }
        }
    }
    @Test public void allMissingPoliciesAndIndependentExistingBlocksPreventSelection() {
        List<Lot> missing = SampleTrip.LOTS.stream().map(lot -> policy(lot, null)).toList();
        Entrance valid = new Entrance("합성", 37, 127);
        Tariff fare = SampleTrip.LOTS.get(0).tariff();
        List<Lot> independent = List.of(
                new Lot("height", "synthetic", true, 1800, null, fare, valid, "synthetic"),
                new Lot("width", "synthetic", true, 2500, null, fare, valid, "synthetic", new WidthCondition(1800, "synthetic")),
                new Lot("restricted", "synthetic", true, 2500, EvEntry.RESTRICTED, fare, valid, "synthetic"),
                new Lot("entrance", "synthetic", true, 2500, EvEntry.ALLOWED, fare, null, "synthetic"),
                new Lot("coordinates", "synthetic", true, 2500, EvEntry.ALLOWED, fare, new Entrance("invalid", Double.NaN, 127), "synthetic"));
        for (Priority priority : Priority.values()) {
            ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
            ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
            fake.visit(SampleTrip.VISIT, missing);
            List<Option> result = options(nav, electric(), priority);
            assertEquals(0, requestableCount(result));
            for (Option option : result) {
                assertEquals(Fit.NEEDS_CHECK, option.fit());
                blocked(nav, option);
            }
            for (int i = 0; i < independent.size(); i++) {
                fake.visit(SampleTrip.VISIT, List.of(independent.get(i)));
                List<Option> single = options(nav, electric(), priority);
                assertEquals(0, requestableCount(single));
                Option option = single.get(0);
                assertEquals(i < 3 ? Fit.RESTRICTED : Fit.MATCHES_RECORDED_RULES, option.fit());
                assertEquals(List.of("표기 높이 제한 초과", "최대 허용 차폭 초과", "등록된 전기차 입차 제한",
                        "입구 정보 미확인", "입구 좌표 확인 필요").get(i), option.requestAvailability().reason());
                blocked(nav, option);
            }
            assertEquals(0, fake.requests.size());
        }
    }
}
