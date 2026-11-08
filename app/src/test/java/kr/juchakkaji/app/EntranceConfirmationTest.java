package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

public final class EntranceConfirmationTest {
    private final ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
    private final ParkingNavigation nav = new ParkingNavigation(fake, () -> {});

    private List<Option> options(int height, int minutes, boolean discount, Priority priority) {
        VehicleProfile vehicle = VehicleProfile.fromHardware(null, null, null,
                new Integer[]{height, 4700, 1990, 2100}, new Integer[]{FuelType.UNLEADED});
        assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, vehicle.drive());
        return nav.options(vehicle.heightMm(), vehicle.widthMm(), vehicle.drive(), minutes, discount, priority);
    }
    private EntranceConfirmation preview(Option option, int minutes, boolean discount) {
        EntranceConfirmation result = EntranceConfirmation.create(nav, option, minutes, discount).orElseThrow();
        assertSame(option.lot(), result.lot());
        assertEquals(option.expectedWon(), result.expectedWon());
        assertEquals(minutes, result.stayMinutes());
        assertEquals(discount, result.purchaseDiscount());
        assertSame(result.selection().request().entrance(), result.entrance());
        return result;
    }
    private void visit() { fake.visit(SampleTrip.VISIT, SampleTrip.LOTS); }

    @Test public void hardwareHeightChangesFreeAttachedPreviewToPaidAlternative() {
        visit();
        EntranceConfirmation attached = preview(options(1850, 120, true, Priority.ATTACHED_FIRST).get(0), 120, true);
        assertEquals(SampleTrip.VISIT, attached.visit());
        assertEquals("코엑스 지하주차장", attached.lot().name());
        assertEquals(SampleTrip.LOTS.get(0).entrance(), attached.entrance());
        assertTrue(attached.message().contains("120분 예상 0원"));
        assertTrue(attached.discountText().contains("구매 할인 조건 충족 가정 · 선택 시설에만 적용:"));
        nav.invalidateSelections();
        List<Option> tall = options(2250, 120, true, Priority.ATTACHED_FIRST);
        Option coex = tall.stream().filter(o -> o.lot().attached()).findFirst().orElseThrow();
        assertEquals(Fit.RESTRICTED, coex.fit());
        assertTrue(EntranceConfirmation.create(nav, coex, 120, true).isEmpty());
        EntranceConfirmation alternative = preview(tall.get(0), 120, true);
        assertTrue(alternative.message().contains("대안 A · 시연 주차장"));
        assertTrue(alternative.message().contains("120분 예상 6,000원"));
        assertTrue(alternative.discountText().contains("적용할 구매 할인 규칙 없음"));
        assertFalse(alternative.message().contains("예상 0원"));
        assertEquals(0, fake.requests.size());
    }

    @Test public void secondCandidateOwnsFareDurationAndUnknownFare() {
        visit();
        List<Option> candidates = options(1850, 120, false, Priority.ATTACHED_FIRST);
        assertEquals(Integer.valueOf(12000), candidates.get(0).expectedWon());
        EntranceConfirmation second = preview(candidates.get(1), 120, false);
        assertEquals(SampleTrip.LOTS.get(1), second.lot());
        assertTrue(second.message().contains("120분 예상 6,000원"));
        assertFalse(second.message().contains("12,000원"));
        assertEquals("구매 할인 미적용", second.discountText());
        EntranceConfirmation hour = preview(options(1850, 60, false, Priority.ATTACHED_FIRST).get(1), 60, false);
        assertTrue(hour.message().contains("60분 예상 3,000원"));
        Lot original = SampleTrip.LOTS.get(1);
        Lot unknown = new Lot(original.name(), original.type(), false, original.heightLimitMm(),
                original.evEntry(), null, original.entrance(), "합성 요금 누락");
        fake.visit("새 방문지", List.of(unknown));
        EntranceConfirmation missing = preview(options(1850, 120, true, Priority.ATTACHED_FIRST).get(0), 120, true);
        assertNull(missing.expectedWon());
        assertTrue(missing.message().contains("120분 예상 요금 미확인"));
        assertTrue(missing.discountText().contains("할인 정보 미확인"));
        assertEquals(0, fake.requests.size());
    }

    @Test public void refreshChangesExpireSelectionWithoutMutatingSnapshotAndNewVisitReplacesIt() {
        for (String change : List.of("duration", "discount", "priority", "vehicle")) {
            visit();
            EntranceConfirmation old = preview(options(1850, 120, true, Priority.ATTACHED_FIRST).get(0), 120, true);
            String displayed = old.message();
            // Mirrors MainActivity.refresh after each settings/vehicle update.
            nav.invalidateSelections();
            options(change.equals("vehicle") ? 2250 : 1850,
                    change.equals("duration") ? 60 : 120, !change.equals("discount"),
                    change.equals("priority") ? Priority.FARE_FIRST : Priority.ATTACHED_FIRST);
            assertEquals(displayed, old.message());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old.selection()));
            assertEquals(0, fake.requests.size());
        }
        EntranceConfirmation old = preview(options(1850, 120, false, Priority.ATTACHED_FIRST).get(0), 120, false);
        fake.visit("새 방문지", SampleTrip.LOTS);
        assertEquals(SampleTrip.VISIT, old.visit());
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old.selection()));
        EntranceConfirmation fresh = preview(options(1850, 120, false, Priority.ATTACHED_FIRST).get(1), 120, false);
        assertEquals("새 방문지", fresh.visit());
        assertTrue(fresh.message().contains("방문지: 새 방문지"));
        assertEquals(0, fake.requests.size());
    }

    @Test public void onlyDisplayedSelectionConfirmationRequestsOnceAndEchoPreservesVisit() {
        visit();
        EntranceConfirmation cancelled = preview(options(1850, 120, false, Priority.ATTACHED_FIRST).get(1), 120, false);
        assertTrue(cancelled.message().contains("시연 경로 요청"));
        // Cancel is a null dialog listener: discard the preview without confirm.
        assertEquals(0, fake.requests.size());
        EntranceConfirmation shown = preview(options(1850, 120, false, Priority.ATTACHED_FIRST).get(1), 120, false);
        fake.echo = true;
        assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(shown.selection()));
        assertEquals(ParkingNavigation.Status.STALE, nav.confirm(shown.selection()));
        assertEquals(1, fake.requests.size());
        assertSame(shown.selection().request(), fake.requests.get(0));
        assertEquals(SampleTrip.VISIT, fake.requests.get(0).visit());
        assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0.0);
        assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0.0);
        fake.listener.accept(new NavigationPort.Destination("echo", "입구", List.of(), fake.lastId));
        assertEquals(SampleTrip.VISIT, nav.visit().name());
    }

    @Test public void rejectionAndExceptionRemainFailedReceipts() {
        for (boolean throwsFailure : new boolean[]{false, true}) {
            visit();
            fake.result = NavigationPort.Result.REJECTED;
            fake.throwsFailure = throwsFailure;
            EntranceConfirmation shown = preview(options(1850, 120, false, Priority.ATTACHED_FIRST).get(1), 120, false);
            int before = fake.requests.size();
            assertEquals(ParkingNavigation.Status.REJECTED, nav.confirm(shown.selection()));
            assertEquals(ParkingNavigation.Status.REJECTED, nav.lastRequest().outcome());
            assertSame(shown.selection().request(), nav.lastRequest().request());
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(shown.selection()));
            assertEquals(before + 1, fake.requests.size());
        }
    }
}
