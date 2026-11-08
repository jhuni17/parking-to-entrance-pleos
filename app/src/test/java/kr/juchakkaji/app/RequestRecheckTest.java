package kr.juchakkaji.app;

import android.car.FuelType;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RequestRecheckTest {
    private static VehicleProfile hardware(Integer height, boolean electric) {
        return VehicleProfile.fromHardware("synthetic", "test", 2025,
                new Integer[]{height, 4500, 1800},
                new Integer[]{electric ? FuelType.ELECTRIC : FuelType.UNLEADED});
    }

    static final class Fixture {
        final ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
        final ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        final VehicleRefreshState vehicle = new VehicleRefreshState();
        Fixture() {
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            vehicle.activate();
            update(1850, false);
        }
        void begin() {
            assertTrue(vehicle.start(0));
            nav.invalidateSelections();
        }
        void finish(Integer height, boolean electric) {
            int token = vehicle.generation();
            assertTrue(vehicle.beginRead(token, 0));
            assertEquals(VehicleReadResult.Session.Action.COMPLETE,
                    vehicle.accept(token, 0, VehicleReadResult.connected(hardware(height, electric)), 1));
        }
        void update(Integer height, boolean electric) { begin(); finish(height, electric); }
        List<ParkingPlan.Option> options(ParkingPlan.Priority priority, int minutes, boolean discount) {
            VehicleProfile v = vehicle.vehicle();
            return nav.options(v.heightMm(), v.widthMm(), v.drive(), minutes, discount, priority);
        }
        List<ParkingPlan.Option> options() { return options(ParkingPlan.Priority.ATTACHED_FIRST, 120, true); }
        RequestRecheck model() { return RequestRecheck.create(nav.lastRequest(), vehicle.reading(), options()); }
        ParkingNavigation.Receipt first() {
            assertNull(nav.lastRequest());
            assertNull(model());
            ParkingPlan.Option chosen = options().get(0);
            assertSame(SampleTrip.LOTS.get(0), chosen.lot());
            assertEquals(Integer.valueOf(0), chosen.expectedWon());
            ParkingNavigation.Selection selection = nav.select(chosen).orElseThrow();
            assertNull(nav.lastRequest());
            assertEquals(0, fake.requests.size());
            nav.confirm(selection);
            ParkingNavigation.Receipt receipt = nav.lastRequest();
            assertSame(chosen.lot(), receipt.lot());
            ParkingNavigation.Receipt historical = new ParkingNavigation.Receipt(receipt.request(), receipt.outcome());
            assertEquals(historical, receipt);
            assertEquals(receipt, historical);
            assertEquals(historical.hashCode(), receipt.hashCode());
            assertSame(selection.request(), receipt.request());
            assertEquals(SampleTrip.VISIT, receipt.request().visit());
            assertSame(chosen.lot().entrance(), receipt.request().entrance());
            return receipt;
        }
    }

    @Test public void hardwareChangeRechecksPastEntranceAndOnlyConfirmationRequestsAlternative() {
        for (NavigationPort.Result result : List.of(NavigationPort.Result.ACCEPTED, NavigationPort.Result.SIMULATED)) {
            Fixture f = new Fixture();
            f.fake.result = result;
            f.fake.echo = true;
            NavigationPort.Destination visit = f.nav.visit();
            ParkingNavigation.Receipt past = f.first();
            f.update(2250, false);
            for (ParkingPlan.Priority priority : ParkingPlan.Priority.values()) {
                List<ParkingPlan.Option> options = f.options(priority, 120, true);
                ParkingPlan.Option old = options.stream().filter(o -> o.lot() == past.lot()).findFirst().orElseThrow();
                assertEquals(ParkingPlan.Fit.RESTRICTED, old.fit());
                RequestRecheck model = RequestRecheck.create(past, false, options);
                assertEquals(RequestRecheck.State.RECHECK, model.state());
                assertEquals("표기 높이 제한 초과", model.reason());
                assertEquals("이전 요청 · 입구 재확인 필요", model.summary());
                assertTrue(model.currentText().contains("현재 요청 가능 여부: 입구 재확인 필요"));
                assertTrue(model.currentText().contains("현재 사유: 표기 높이 제한 초과"));
                assertSame(past, model.receipt());
                assertSame(past, f.nav.lastRequest());
                assertEquals(ParkingNavigation.Status.valueOf(result.name()), past.outcome());
                assertEquals(1, ParkingPlan.requestableCount(options));
                assertSame(SampleTrip.LOTS.get(1), options.get(0).lot());
                assertEquals(Integer.valueOf(6000), options.get(0).expectedWon());
            }
            EntranceConfirmation preview = EntranceConfirmation.create(f.nav, f.options().get(0), 120, true).orElseThrow();
            assertEquals(Integer.valueOf(6000), preview.expectedWon());
            ParkingNavigation.Selection alternative = preview.selection();
            RecommendationExplanation explanation = RecommendationExplanation.create(
                    f.vehicle.vehicle(), false, f.options());
            assertTrue(explanation.vehicleDetails().contains("2250mm · 차량 속성값"));
            assertEquals("코엑스 지하주차장 · 높이 초과", explanation.cardHeadline());
            assertFalse(explanation.cardHeadline().contains("\n"));
            assertEquals(1, f.fake.requests.size());
            assertSame(past, f.nav.lastRequest());
            f.nav.confirm(alternative);
            assertEquals(2, f.fake.requests.size());
            assertEquals(SampleTrip.VISIT, f.fake.requests.get(1).visit());
            assertEquals(37.51400, f.fake.requests.get(1).entrance().latitude(), 0.0);
            assertEquals(127.06100, f.fake.requests.get(1).entrance().longitude(), 0.0);
            assertEquals(ParkingNavigation.Status.STALE, f.nav.confirm(alternative));
            assertEquals(2, f.fake.requests.size());
            ParkingNavigation.Receipt next = f.nav.lastRequest();
            assertSame(SampleTrip.LOTS.get(1), next.lot());
            assertSame(visit, f.nav.visit());
            f.fake.listener.accept(new NavigationPort.Destination("echo", "entrance", List.of(), f.fake.lastId));
            assertSame(visit, f.nav.visit());
            assertSame(next, f.nav.lastRequest());
            f.fake.visit("new", SampleTrip.LOTS);
            assertNull(f.model());
            assertNull(f.nav.lastRequest());
            assertEquals(2, f.fake.requests.size());
        }
    }

    @Test public void refreshDefersJudgmentAndMissingHeightOrElectricIsUncertainty() {
        Fixture f = new Fixture();
        ParkingNavigation.Receipt past = f.first();
        f.begin();
        assertEquals(RequestRecheck.State.READING, f.model().state());
        assertEquals("차량 정보 확인 중", f.model().reason());
        assertTrue(f.model().currentText().contains("판단 유보"));
        f.finish(null, false);
        assertEquals("차량 높이 확인 필요", f.model().reason());
        assertTrue(f.model().needsRecheck());
        f.update(1850, true);
        assertEquals("전기차 입차 조건 확인 필요", f.model().reason());
        assertEquals(ParkingPlan.Fit.NEEDS_CHECK, f.options().stream()
                .filter(o -> o.lot() == past.lot()).findFirst().orElseThrow().fit());
        assertTrue(f.model().needsRecheck());
        assertSame(past, f.nav.lastRequest());
        assertEquals(1, f.fake.requests.size());
    }

    @Test public void fareOrderAndDurationDoNotWarnButExclusionAndIdentityDo() {
        Fixture f = new Fixture();
        ParkingNavigation.Receipt past = f.first();
        for (ParkingPlan.Priority priority : ParkingPlan.Priority.values()) {
            RequestRecheck model = RequestRecheck.create(past, false, f.options(priority, 60, false));
            assertFalse(model.needsRecheck());
            assertEquals(RequestRecheck.State.AVAILABLE, model.state());
        }
        assertTrue(f.nav.setExcluded(f.nav.visitVersion(), past.lot(), true));
        assertEquals(RequestRecheck.State.EXCLUDED, f.model().state());
        assertEquals("이번 방문 제외", f.model().reason());
        assertTrue(f.nav.setExcluded(f.nav.visitVersion(), past.lot(), false));
        assertFalse(f.model().needsRecheck());
        f.update(2250, false);
        f.nav.setExcluded(f.nav.visitVersion(), past.lot(), true);
        f.nav.setExcluded(f.nav.visitVersion(), past.lot(), false);
        assertEquals("표기 높이 제한 초과", f.model().reason());
        ParkingPlan.Lot lot = past.lot();
        ParkingPlan.Lot copy = new ParkingPlan.Lot(lot.name(), lot.type(), lot.attached(), lot.heightLimitMm(),
                lot.evEntry(), lot.tariff(), lot.entrance(), lot.provenance(), lot.widthCondition());
        assertEquals(lot, copy);
        assertNotSame(lot, copy);
        List<ParkingPlan.Option> copied = ParkingPlan.compare(List.of(copy), 1850,
                VehicleProfile.DriveType.NON_ELECTRIC, 120, true);
        RequestRecheck unknown = RequestRecheck.create(past, false, copied);
        assertEquals(RequestRecheck.State.UNKNOWN, unknown.state());
        assertEquals("이전 시설 정보 미확인", unknown.reason());
        assertTrue(unknown.needsRecheck());
        assertEquals(RequestRecheck.State.UNKNOWN, RequestRecheck.create(past, false, List.of()).state());
        assertSame(past, f.nav.lastRequest());
        assertEquals(1, f.fake.requests.size());
    }

    @Test public void rejectedAndThrownRequestsStayFailuresAfterRecheck() {
        for (boolean throwsFailure : List.of(false, true)) {
            Fixture f = new Fixture();
            f.fake.result = NavigationPort.Result.REJECTED;
            f.fake.throwsFailure = throwsFailure;
            ParkingNavigation.Receipt past = f.first();
            assertEquals(ParkingNavigation.Status.REJECTED, past.outcome());
            assertEquals("입구 경로 요청 실패", f.model().outcomeText());
            f.update(2250, false);
            assertEquals("이전 요청 · 입구 재확인 필요 · 입구 경로 요청 실패", f.model().summary());
            assertSame(past, f.nav.lastRequest());
            assertEquals(1, f.fake.requests.size());
        }
    }
}
