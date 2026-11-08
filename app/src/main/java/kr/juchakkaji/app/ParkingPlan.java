package kr.juchakkaji.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class ParkingPlan {
    enum Fit { MATCHES_RECORDED_RULES, NEEDS_CHECK, RESTRICTED }
    enum EvEntry { ALLOWED, RESTRICTED, UNKNOWN }

    record Entrance(String label, double latitude, double longitude) {}

    record Tariff(int unitMinutes, int wonPerUnit, int purchaseFreeMinutes, String purchaseRule) {
        int quote(int stayMinutes, boolean qualifiesForPurchaseDiscount) {
            int freeMinutes = qualifiesForPurchaseDiscount ? purchaseFreeMinutes : 0;
            int billedMinutes = Math.max(0, stayMinutes - freeMinutes);
            return ((billedMinutes + unitMinutes - 1) / unitMinutes) * wonPerUnit;
        }
    }

    record Lot(String name, String type, boolean attached, Integer heightLimitMm,
               EvEntry evEntry, Tariff tariff, Entrance entrance, String provenance) {}

    record RequestAvailability(boolean available, String reason) {}

    record Option(Lot lot, Fit fit, String reason, Integer baseWon, Integer expectedWon) {
        RequestAvailability requestAvailability() {
            if (fit != Fit.MATCHES_RECORDED_RULES) {
                return new RequestAvailability(false, reason
                        + (lot.entrance() == null ? " · 입구 정보 미확인" : ""));
            }
            if (lot.entrance() == null) {
                return new RequestAvailability(false, "입구 정보 미확인");
            }
            return new RequestAvailability(true, "입구 경로 요청 가능");
        }
    }

    static long requestableCount(List<Option> options) {
        return options.stream().filter(option -> option.requestAvailability().available()).count();
    }

    static String candidateSummary(List<Option> options) {
        long count = requestableCount(options);
        if (options.isEmpty()) return "시설 정보 없음 · 요청 가능한 후보 0곳";
        if (count == 0) return "요청 가능한 입구 없음 · 요청 가능한 후보 0곳";
        return "요청 가능한 후보 " + count + "곳";
    }

    record RouteRequest(String visit, Entrance entrance) {}

    static List<Option> compare(List<Lot> lots, Integer vehicleHeightMm, boolean electric,
                                int stayMinutes, boolean qualifiesForPurchaseDiscount) {
        List<Option> options = new ArrayList<>();
        for (Lot lot : lots) {
            options.add(evaluate(lot, vehicleHeightMm, electric, stayMinutes, qualifiesForPurchaseDiscount));
        }
        options.sort(Comparator.comparing((Option option) -> !option.requestAvailability().available())
                .thenComparing(Option::fit)
                .thenComparing(Option::expectedWon, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(option -> !option.lot().attached()));
        return options;
    }

    private static Option evaluate(Lot lot, Integer heightMm, boolean electric,
                                   int stayMinutes, boolean qualifiesForPurchaseDiscount) {
        Tariff tariff = lot.tariff();
        Integer base = tariff == null ? null : tariff.quote(stayMinutes, false);
        Integer expected = tariff == null ? null : tariff.quote(stayMinutes, qualifiesForPurchaseDiscount);
        Integer limitMm = lot.heightLimitMm();
        if (heightMm != null && limitMm != null && heightMm > limitMm) {
            return new Option(lot, Fit.RESTRICTED, "표기 높이 제한 초과", base, expected);
        }
        if (electric && lot.evEntry() == EvEntry.RESTRICTED) {
            return new Option(lot, Fit.RESTRICTED, "등록된 전기차 입차 제한", base, expected);
        }
        if (heightMm == null || limitMm == null || heightMm.equals(limitMm)) {
            return new Option(lot, Fit.NEEDS_CHECK, "높이 여유 또는 제한 정보 확인 필요", base, expected);
        }
        if (electric && lot.evEntry() == EvEntry.UNKNOWN) {
            return new Option(lot, Fit.NEEDS_CHECK, "전기차 입차 조건 확인 필요", base, expected);
        }
        return new Option(lot, Fit.MATCHES_RECORDED_RULES, "등록된 입차 조건 기준 충족", base, expected);
    }

    static Optional<RouteRequest> routeRequest(String visit, Option option) {
        if (!option.requestAvailability().available()) {
            return Optional.empty();
        }
        return Optional.of(new RouteRequest(visit, option.lot().entrance()));
    }

    private ParkingPlan() {}
}
