package kr.juchakkaji.app;

import java.text.NumberFormat;
import java.util.Locale;

/** Display snapshot of an evaluated option; no fare/fit calculation or navigation side effects. */
record FareDetails(Integer baseWon, Integer expectedWon, int stayMinutes,
                   boolean purchaseDiscount, String discountText) {
    static FareDetails create(ParkingPlan.Option option, int stayMinutes, boolean purchaseDiscount) {
        ParkingPlan.Tariff tariff = option.lot().tariff();
        String discount = tariff == null
                ? purchaseDiscount ? "구매 할인 조건 충족 가정 · 선택 시설 할인 정보 미확인" : "구매 할인 미적용"
                : tariff.discountText(purchaseDiscount);
        return new FareDetails(option.baseWon(), option.expectedWon(), stayMinutes, purchaseDiscount, discount);
    }

    private static String amount(Integer won) {
        return won == null ? "요금 미확인" : NumberFormat.getIntegerInstance(Locale.KOREA).format(won) + "원";
    }

    String message() {
        return "체류 시간 " + stayMinutes + "분"
                + "\n할인 전 예상 " + amount(baseWon)
                + "\n현재 조건 예상 " + amount(expectedWon)
                + "\n" + discountText
                + "\n예상 요금은 입차 허용이나 확정 결제액을 뜻하지 않음";
    }
}
