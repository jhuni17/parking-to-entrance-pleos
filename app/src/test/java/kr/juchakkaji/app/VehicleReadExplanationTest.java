package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import static kr.juchakkaji.app.ParkingPlan.*;
import static kr.juchakkaji.app.VehicleRefreshState.Outcome.*;
import static org.junit.Assert.*;

/** Synthetic clock/source/Screen; no Android runtime or SDK callback evidence. */
public final class VehicleReadExplanationTest {
    private String details(VehicleRefreshStateTest.Screen s) {
        return VehicleReadExplanation.details(s.state, s.height);
    }
    private void outcome(VehicleRefreshStateTest.Screen s, VehicleRefreshState.Outcome expected, String text) {
        assertEquals(expected, s.state.outcome());
        assertTrue(details(s), details(s).contains(text));
    }
    @Test public void retryAndTerminalReasonsDoNotImplyPropertyAvailability() {
        var s = new VehicleRefreshStateTest.Screen(Priority.ATTACHED_FIRST);
        s.complete(VehicleReadResult.disconnected());
        outcome(s, READING, "차량 정보 확인 중");
        assertTrue(s.state.reading()); assertEquals(1, s.reads);
        s.now = 499; s.retry.run(); assertEquals(1, s.reads);
        s.now = 500; s.retry.run(); s.retry.run(); assertEquals(2, s.reads);
        s.deliver(s.state.generation(), 1, VehicleReadResult.disconnected());
        outcome(s, DISCONNECTED, "차량 서비스 미연결");
        assertFalse(s.state.reading());
        assertTrue(s.start()); s.complete(VehicleReadResult.unavailable());
        outcome(s, UNAVAILABLE, "차량 정보 읽기 불가");
        assertTrue(s.start());
        var source = new VehiclePropertyReaderTest.Fake(); source.responses.clear();
        s.complete(VehicleReadResult.connected(source.profile()));
        outcome(s, CONNECTED, "차량 조회 응답 수신");
        assertNull(s.state.vehicle().heightMm()); assertNull(s.state.vehicle().widthMm());
        assertEquals(VehicleProfile.DriveType.NEEDS_CHECK, s.state.vehicle().drive());
        assertEquals(0, requestableCount(s.options()));
        assertTrue(s.explanation().vehicleDetails().contains("차량 높이 확인 필요"));
        assertTrue(details(s).contains("개별 차량값 확인·입차 가능 여부는 별개"));
        assertTrue(details(s).contains("확인값이 없으면 입구 요청을 보류"));
        assertTrue(details(s).contains("차량 정보 다시 확인"));
        assertTrue(s.fake.requests.isEmpty());
    }
    @Test public void deadlineOldEventsAndStopCannotInventFailure() {
        var s = new VehicleRefreshStateTest.Screen(Priority.FARE_FIRST);
        int old = s.state.generation(); var expiry = s.expiry;
        s.now = 2999; expiry.run(); outcome(s, READING, "확인 중");
        s.now = 3000; s.complete(VehicleRefreshStateTest.hardware(1850, false));
        outcome(s, EXPIRED, "차량 조회 대기 만료");
        assertEquals(0, requestableCount(s.options()));
        String expired = details(s); expiry.run(); assertEquals(expired, details(s));
        assertTrue(s.start()); s.stop(); outcome(s, IDLE, "진행 안 함");
        s.onStart(); outcome(s, READING, "확인 중");
        s.deliver(old, 0, VehicleRefreshStateTest.hardware(2250, false)); expiry.run();
        outcome(s, READING, "확인 중"); assertNull(s.state.vehicle().heightMm());
        s.complete(VehicleRefreshStateTest.hardware(1850, false));
        var vehicle = s.state.vehicle(); String recovered = details(s);
        s.complete(VehicleReadResult.unavailable()); expiry.run();
        assertSame(vehicle, s.state.vehicle()); assertEquals(recovered, details(s));
        s.stop(); outcome(s, CONNECTED, "응답 수신");
        // The existing fake timer also expires a query with no callback at exactly 3000ms.
        s.onStart(); s.now += 2999; s.expiry.run(); assertTrue(s.state.reading());
        s.now++; s.expiry.run(); outcome(s, EXPIRED, "대기 만료");
        assertFalse(s.state.reading());
    }
    @Test public void catalogSourceKeepsProvenanceAndOnlyAlternativeRequestable() {
        var s = new VehicleRefreshStateTest.Screen(Priority.ATTACHED_FIRST);
        var source = new VehiclePropertyReaderTest.Fake();
        source.put(VehiclePropertyReader.Property.MAKE, "HYUNDAI");
        source.put(VehiclePropertyReader.Property.MODEL, "IONIQ 5 N");
        source.put(VehiclePropertyReader.Property.YEAR, 2025);
        source.responses.remove(VehiclePropertyReader.Property.DIMENSIONS);
        source.put(VehiclePropertyReader.Property.FUEL, new Integer[]{FuelType.ELECTRIC});
        s.complete(VehicleReadResult.connected(source.profile()));
        outcome(s, CONNECTED, "응답 수신");
        assertEquals(Integer.valueOf(1585), s.state.vehicle().heightMm());
        assertEquals(Integer.valueOf(1940), s.state.vehicle().widthMm());
        assertTrue(s.state.vehicle().catalogHeight()); assertTrue(s.state.vehicle().catalogWidth());
        assertTrue(s.explanation().vehicleDetails().contains(VehicleProfile.CATALOG_SOURCE));
        assertTrue(s.explanation().vehicleDetails().contains("제조사 기본 제원"));
        assertEquals(1, requestableCount(s.options()));
        assertSame(SampleTrip.LOTS.get(1), s.options().get(0).lot());
        assertTrue(s.fake.requests.isEmpty());
    }
    @Test public void expiryThenChangedReadReplacesExplanationAndInvalidatesSelection() {
        for (Priority priority : Priority.values()) {
            var s = new VehicleRefreshStateTest.Screen(priority);
            s.complete(VehicleRefreshStateTest.hardware(1850, false));
            var old = s.nav.select(s.options().get(0)).orElseThrow(); var visit = s.nav.visit();
            assertTrue(s.start()); s.now = 3000; s.expiry.run();
            outcome(s, EXPIRED, "대기 만료");
            assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(old));
            assertEquals(0, requestableCount(s.options()));
            assertTrue(s.start()); s.complete(VehicleRefreshStateTest.hardware(2250, false));
            outcome(s, CONNECTED, "응답 수신"); assertFalse(details(s).contains("대기 만료"));
            assertEquals(Fit.RESTRICTED, s.coex().fit());
            assertEquals(1, requestableCount(s.options()));
            assertSame(SampleTrip.LOTS.get(1), s.options().get(0).lot());
            assertEquals(Integer.valueOf(6000), s.options().get(0).expectedWon());
            assertSame(visit, s.nav.visit()); assertEquals(120, s.quote.stayMinutes());
            assertTrue(s.quote.purchaseDiscount()); assertNull(s.height.confirmedMm());
            assertTrue(s.fake.requests.isEmpty());
        }
    }
    @Test public void manualHeightPreservesExpiryAndOnlyExplicitConfirmationRequests() {
        var s = new VehicleRefreshStateTest.Screen(Priority.ATTACHED_FIRST);
        assertTrue(s.quote.apply(s.quote.open(), 120, false));
        s.now = 3000; s.expiry.run(); outcome(s, EXPIRED, "대기 만료");
        assertNull(s.state.vehicle().heightMm());
        assertEquals(VehicleProfile.DriveType.NEEDS_CHECK, s.state.vehicle().drive());
        assertEquals(0, requestableCount(s.options()));
        assertTrue(details(s).contains("직접 확인한 총높이가 있을 때만 총높이 보완"));
        assertTrue(s.height.apply(s.height.session(), "1850")); s.refresh();
        outcome(s, EXPIRED, "대기 만료");
        assertTrue(details(s).contains("차량 조회 성공을 뜻하지 않습니다"));
        assertFalse(details(s).contains("조회 상태: 차량 조회 응답 수신"));
        assertEquals(1, requestableCount(s.options()));
        var alternative = s.options().get(0);
        assertSame(SampleTrip.LOTS.get(1), alternative.lot());
        assertEquals(Integer.valueOf(6000), alternative.expectedWon());
        var preview = EntranceConfirmation.create(s.nav, alternative, 120, false).orElseThrow();
        assertTrue(s.fake.requests.isEmpty()); assertEquals(1, s.reads);
        assertEquals(ParkingNavigation.Status.ACCEPTED, s.nav.confirm(preview.selection()));
        assertEquals(1, s.fake.requests.size());
        assertEquals(SampleTrip.VISIT, s.fake.requests.get(0).visit());
        assertEquals(37.51400, s.fake.requests.get(0).entrance().latitude(), 0);
        assertEquals(127.06100, s.fake.requests.get(0).entrance().longitude(), 0);
        assertEquals(ParkingNavigation.Status.STALE, s.nav.confirm(preview.selection()));
        assertEquals(1, s.fake.requests.size());
    }
}
