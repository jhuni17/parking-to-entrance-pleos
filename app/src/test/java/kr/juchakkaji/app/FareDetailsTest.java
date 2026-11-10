package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;
import java.util.List;
import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

public final class FareDetailsTest {
    private final ParkingNavigationTest.Fake fake = new ParkingNavigationTest.Fake();
    private final ParkingNavigation nav = new ParkingNavigation(fake, () -> {});

    private List<Option> options(int height, int minutes, boolean discount, Priority priority) {
        VehicleProfile vehicle = VehicleProfile.fromHardware(null, null, null,
                new Integer[]{height, 4700, 1990, 2100}, new Integer[]{FuelType.UNLEADED});
        assertEquals(Integer.valueOf(height), vehicle.heightMm());
        assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, vehicle.drive());
        return nav.options(vehicle.heightMm(), vehicle.widthMm(), vehicle.drive(), minutes, discount, priority);
    }

    private Option named(List<Option> options, int index) {
        return options.stream().filter(o -> o.lot() == SampleTrip.LOTS.get(index)).findFirst().orElseThrow();
    }

    private FareDetails details(Option option, int minutes, boolean discount,
                                Integer base, Integer expected, String baseText, String expectedText) {
        FareDetails detail = FareDetails.create(option, minutes, discount);
        assertEquals(base, option.baseWon());
        assertEquals(expected, option.expectedWon());
        assertEquals(option.baseWon(), detail.baseWon());
        assertEquals(option.expectedWon(), detail.expectedWon());
        assertEquals(minutes, detail.stayMinutes());
        assertEquals(discount, detail.purchaseDiscount());
        assertTrue(detail.message().contains("체류 시간 " + minutes + "분"));
        assertTrue(detail.message().contains("할인 전 예상 " + baseText));
        assertTrue(detail.message().contains("현재 조건 예상 " + expectedText));
        if (option.requestAvailability().available()) {
            EntranceConfirmation confirmation = EntranceConfirmation.create(nav, option, minutes, discount).orElseThrow();
            assertEquals(detail.expectedWon(), confirmation.expectedWon());
            assertEquals(detail.stayMinutes(), confirmation.stayMinutes());
            assertEquals(detail.purchaseDiscount(), confirmation.purchaseDiscount());
            assertEquals(detail.discountText(), confirmation.discountText());
            assertTrue(confirmation.message().contains(minutes + "분 예상 " + expectedText));
        } else {
            assertTrue(EntranceConfirmation.create(nav, option, minutes, discount).isEmpty());
        }
        assertEquals(0, fake.requests.size());
        return detail;
    }

    @Test public void discountAndDurationUseActualCandidateAndConfirmationAmounts() {
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        for (Priority priority : Priority.values()) {
            for (boolean discount : new boolean[]{false, true}) {
                nav.invalidateSelections();
                List<Option> candidates = options(1850, 120, discount, priority);
                FareDetails coex = details(named(candidates, 0), 120, discount, 12000,
                        discount ? 0 : 12000, "12,000원", discount ? "0원" : "12,000원");
                assertEquals(SampleTrip.LOTS.get(0).tariff().discountText(discount), coex.discountText());
                assertTrue(coex.discountText().contains(discount ? "실제 구매 검증 아님" : "구매 할인 미적용"));
                FareDetails alternative = details(named(candidates, 1), 120, discount, 6000, 6000, "6,000원", "6,000원");
                assertTrue(alternative.discountText().contains(discount ? "적용할 구매 할인 규칙 없음" : "구매 할인 미적용"));
            }
            nav.invalidateSelections();
            List<Option> changed = options(1850, 121, true, priority);
            details(named(changed, 0), 121, true, 13500, 1500, "13,500원", "1,500원");
            details(named(changed, 1), 121, true, 6500, 6500, "6,500원", "6,500원");
        }
    }

    @Test public void heightRefreshKeepsCostsButBlocksOldSelectionUntilExplicitAlternativeConfirmation() {
        for (Priority priority : Priority.values()) {
            fake.requests.clear();
            fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
            List<Option> low = options(1850, 120, true, priority);
            assertSame(SampleTrip.LOTS.get(0), low.get(0).lot());
            assertEquals(2, requestableCount(low));
            details(low.get(0), 120, true, 12000, 0, "12,000원", "0원");
            EntranceConfirmation old = EntranceConfirmation.create(nav, low.get(0), 120, true).orElseThrow();
            // Same invalidation boundary used by MainActivity.refresh on a vehicle update.
            nav.invalidateSelections();
            List<Option> tall = options(2250, 120, true, priority);
            Option coex = named(tall, 0);
            assertEquals(Fit.RESTRICTED, coex.fit());
            assertFalse(coex.requestAvailability().available());
            assertTrue(nav.select(coex).isEmpty());
            details(coex, 120, true, 12000, 0, "12,000원", "0원");
            assertEquals(1, requestableCount(tall));
            assertSame(SampleTrip.LOTS.get(1), tall.get(0).lot());
            assertEquals(Fit.MATCHES_RECORDED_RULES, tall.get(0).fit());
            assertTrue(tall.get(0).requestAvailability().available());
            details(tall.get(0), 120, true, 6000, 6000, "6,000원", "6,000원");
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(old.selection()));
            EntranceConfirmation fresh = EntranceConfirmation.create(nav, tall.get(0), 120, true).orElseThrow();
            assertEquals(0, fake.requests.size());
            fake.echo = true;
            assertEquals(ParkingNavigation.Status.ACCEPTED, nav.confirm(fresh.selection()));
            assertEquals(ParkingNavigation.Status.STALE, nav.confirm(fresh.selection()));
            assertEquals(1, fake.requests.size());
            assertEquals(SampleTrip.VISIT, fake.requests.get(0).visit());
            assertEquals(37.51400, fake.requests.get(0).entrance().latitude(), 0.0);
            assertEquals(127.06100, fake.requests.get(0).entrance().longitude(), 0.0);
            assertEquals(SampleTrip.VISIT, nav.visit().name());
        }
    }

    @Test public void missingInvalidAndZeroTariffsRemainDistinctAndUnavailableCostsStayVisible() {
        for (Tariff tariff : new Tariff[]{null, new Tariff(0, 500, 0, ""),
                new Tariff(10, -1, 0, ""), new Tariff(10, 0, 0, "")}) {
            Lot lot = new Lot("합성 요금", "시연", false, 2500, EvEntry.ALLOWED, tariff,
                    SampleTrip.LOTS.get(1).entrance(), "합성");
            fake.visit(SampleTrip.VISIT, List.of(lot));
            Option option = options(1850, 120, true, Priority.FARE_FIRST).get(0);
            boolean zero = tariff != null && tariff.wonPerUnit() == 0;
            FareDetails detail = details(option, 120, true, zero ? 0 : null, zero ? 0 : null,
                    zero ? "0원" : "요금 미확인", zero ? "0원" : "요금 미확인");
            if (tariff == null) assertTrue(detail.discountText().contains("할인 정보 미확인"));
            assertTrue(nav.setExcluded(nav.visitVersion(), lot, true));
            Option excluded = options(1850, 120, true, Priority.FARE_FIRST).get(0);
            assertTrue(excluded.excluded());
            assertEquals(detail.message(), FareDetails.create(excluded, 120, true).message());
            assertTrue(EntranceConfirmation.create(nav, excluded, 120, true).isEmpty());
        }
        fake.visit(SampleTrip.VISIT, SampleTrip.LOTS);
        Option unknown = named(options(1850, 120, true, Priority.ATTACHED_FIRST), 2);
        assertEquals(Fit.NEEDS_CHECK, unknown.fit());
        details(unknown, 120, true, 4000, 4000, "4,000원", "4,000원");
        assertEquals(0, fake.requests.size());
    }

    @Test public void displayConsumesSuppliedAmountsWithoutRecalculating() {
        Option supplied = new Option(SampleTrip.LOTS.get(0), Fit.RESTRICTED, "합성 제한", 42, 7);
        FareDetails detail = FareDetails.create(supplied, 121, true);
        assertEquals(Integer.valueOf(42), detail.baseWon());
        assertEquals(Integer.valueOf(7), detail.expectedWon());
        assertTrue(detail.message().contains("할인 전 예상 42원"));
        assertTrue(detail.message().contains("현재 조건 예상 7원"));
        assertEquals(0, fake.requests.size());
    }
}
