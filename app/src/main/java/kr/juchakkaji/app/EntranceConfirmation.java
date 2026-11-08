package kr.juchakkaji.app;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

/** Immutable display snapshot of a selected, already evaluated option; never requests a route. */
final class EntranceConfirmation {
    private final ParkingNavigation.Selection selection;
    private final ParkingPlan.Lot lot;
    private final int stayMinutes;
    private final boolean purchaseDiscount;
    private final Integer expectedWon;

    private EntranceConfirmation(ParkingNavigation.Selection selection, ParkingPlan.Option option,
                                 int stayMinutes, boolean purchaseDiscount) {
        this.selection = selection;
        this.lot = option.lot();
        this.stayMinutes = stayMinutes;
        this.purchaseDiscount = purchaseDiscount;
        this.expectedWon = option.expectedWon();
    }

    static Optional<EntranceConfirmation> create(ParkingNavigation navigation, ParkingPlan.Option option,
                                                 int stayMinutes, boolean purchaseDiscount) {
        return navigation.select(option).map(selection ->
                new EntranceConfirmation(selection, option, stayMinutes, purchaseDiscount));
    }

    ParkingNavigation.Selection selection() { return selection; }
    ParkingPlan.Lot lot() { return lot; }
    int stayMinutes() { return stayMinutes; }
    boolean purchaseDiscount() { return purchaseDiscount; }
    Integer expectedWon() { return expectedWon; }
    String visit() { return selection.request().visit(); }
    ParkingPlan.Entrance entrance() { return selection.request().entrance(); }

    String discountText() {
        ParkingPlan.Tariff tariff = lot.tariff();
        if (tariff != null) return tariff.discountText(purchaseDiscount);
        return purchaseDiscount ? "구매 할인 조건 충족 가정 · 선택 시설 할인 정보 미확인"
                : "구매 할인 미적용";
    }

    String message() {
        String amount = expectedWon == null ? "요금 미확인"
                : NumberFormat.getIntegerInstance(Locale.KOREA).format(expectedWon) + "원";
        return "방문지: " + visit() + "\n선택 주차장: " + lot.name()
                + "\n입구: " + entrance().label()
                + "\n" + stayMinutes + "분 예상 " + amount
                + "\n" + discountText() + "\n시연 경로 요청";
    }
}
