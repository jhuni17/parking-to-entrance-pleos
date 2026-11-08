package kr.juchakkaji.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import kr.juchakkaji.app.VehicleProfile.DriveType;

final class ParkingPlan {
    enum Priority { ATTACHED_FIRST, FARE_FIRST }

    enum Fit { MATCHES_RECORDED_RULES, NEEDS_CHECK, RESTRICTED }
    enum EvEntry { ALLOWED, RESTRICTED, UNKNOWN }

    record Entrance(String label, double latitude, double longitude) {
        // Numeric validity only; this does not verify the physical entrance location.
        boolean hasValidCoordinates() {
            return Double.isFinite(latitude) && Double.isFinite(longitude)
                    && latitude >= -90 && latitude <= 90
                    && longitude >= -180 && longitude <= 180;
        }
    }

    record Tariff(int unitMinutes, int wonPerUnit, int purchaseFreeMinutes, String purchaseRule) {
        Tariff {
            purchaseRule = normalizedRule(purchaseRule);
        }

        private static String normalizedRule(String rule) {
            if (rule == null) return "";
            int start = 0;
            int end = rule.length();
            while (start < end && Character.isWhitespace(rule.charAt(start))) start++;
            while (end > start && Character.isWhitespace(rule.charAt(end - 1))) end--;
            return rule.substring(start, end);
        }

        boolean appliesPurchaseDiscount(boolean assumed) {
            return assumed && purchaseFreeMinutes > 0 && !purchaseRule.isEmpty();
        }

        String discountText(boolean assumed) {
            if (purchaseFreeMinutes > 0 && purchaseRule.isEmpty()) {
                return "구매 할인 조건 미확인 · 할인 미적용";
            }
            if (!assumed) return "구매 할인 미적용";
            if (!appliesPurchaseDiscount(assumed)) {
                return "구매 할인 조건 충족 가정 · 선택 시설에 적용할 구매 할인 규칙 없음";
            }
            return "구매 할인 조건 충족 가정 · 선택 시설에만 적용: " + purchaseRule
                    + " (실제 구매 검증 아님)";
        }

        Integer quote(int stayMinutes, boolean qualifiesForPurchaseDiscount) {
            // Validate the whole tariff even when its discount is not selected.
            if (unitMinutes <= 0 || wonPerUnit < 0 || purchaseFreeMinutes < 0 || stayMinutes < 0) {
                return null;
            }
            long freeMinutes = appliesPurchaseDiscount(qualifiesForPurchaseDiscount) ? purchaseFreeMinutes : 0L;
            long billedMinutes = Math.max(0L, (long) stayMinutes - freeMinutes);
            long units = (billedMinutes + unitMinutes - 1L) / unitMinutes;
            long amount = units * wonPerUnit;
            return amount > Integer.MAX_VALUE ? null : (int) amount;
        }
    }

    /** Maximum permitted vehicle specification width, never a measured aisle width.
     * null condition means unregistered; a condition with no positive maximum is unknown. */
    record WidthCondition(Integer maximumMm, String provenance) {
        String details() {
            return "최대 허용 차량 너비 " + (maximumMm == null || maximumMm <= 0
                    ? "확인 필요" : maximumMm + "mm")
                    + "\n차폭 조건 출처 " + provenance
                    + "\n차량 제원과 같은 기준의 허용 차폭만 비교 · 통로 폭/통과 안전 판단 아님";
        }
    }

    record Lot(String name, String type, boolean attached, Integer heightLimitMm,
               EvEntry evEntry, Tariff tariff, Entrance entrance, String provenance, WidthCondition widthCondition) {
        Lot {
            // Nonpositive facility heights are missing data, not explicit entry limits.
            heightLimitMm = heightLimitMm != null && heightLimitMm <= 0 ? null : heightLimitMm;
            // Missing facility policy is uncertainty, never permission to enter.
            evEntry = evEntry == null ? EvEntry.UNKNOWN : evEntry;
        }
        Lot(String name, String type, boolean attached, Integer heightLimitMm,
            EvEntry evEntry, Tariff tariff, Entrance entrance, String provenance) {
            this(name, type, attached, heightLimitMm, evEntry, tariff, entrance, provenance, null);
        }
        String widthDetails() {
            return widthCondition == null
                    ? "차폭 조건 미등록 · 차폭 무제한 또는 적합을 뜻하지 않음"
                    : widthCondition.details();
        }
    }

    record RequestAvailability(boolean available, String reason) {}

    record FitReason(Fit classification, String text) {}

    record Option(Lot lot, Fit fit, String reason, Integer baseWon, Integer expectedWon,
                  boolean excluded, List<FitReason> reasons) {
        Option {
            reasons = List.copyOf(reasons);
        }
        Option(Lot lot, Fit fit, String reason, Integer baseWon, Integer expectedWon, boolean excluded) {
            this(lot, fit, reason, baseWon, expectedWon, excluded,
                    fit == Fit.MATCHES_RECORDED_RULES ? List.of() : List.of(new FitReason(fit, reason)));
        }
        Option(Lot lot, Fit fit, String reason, Integer baseWon, Integer expectedWon) {
            this(lot, fit, reason, baseWon, expectedWon, false);
        }
        String vehicleFitDetails() {
            StringBuilder text = new StringBuilder("차량 판정: ").append(fit);
            if (reasons.isEmpty()) text.append(" · ").append(reason);
            for (FitReason item : reasons) {
                text.append("\n").append(item.classification() == Fit.RESTRICTED ? "제한: " : "확인 필요: ")
                        .append(item.text());
            }
            return text.toString();
        }
        RequestAvailability requestAvailability() {
            if (excluded) return new RequestAvailability(false, "이번 방문 제외");
            String entranceIssue = lot.entrance() == null ? "입구 정보 미확인"
                    : !lot.entrance().hasValidCoordinates() ? "입구 좌표 확인 필요" : "";
            if (fit != Fit.MATCHES_RECORDED_RULES) {
                return new RequestAvailability(false, reason
                        + (entranceIssue.isEmpty() ? "" : " · " + entranceIssue));
            }
            if (!entranceIssue.isEmpty()) {
                return new RequestAvailability(false, entranceIssue);
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

    static List<Option> compare(List<Lot> lots, Integer vehicleHeightMm, DriveType drive,
                                int stayMinutes, boolean qualifiesForPurchaseDiscount) {
        return compare(lots, vehicleHeightMm, drive, stayMinutes, qualifiesForPurchaseDiscount, Priority.ATTACHED_FIRST);
    }

    static List<Option> compare(List<Lot> lots, Integer vehicleHeightMm, DriveType drive,
                                int stayMinutes, boolean qualifiesForPurchaseDiscount, Priority priority) {
        return compare(lots, vehicleHeightMm, null, drive, stayMinutes, qualifiesForPurchaseDiscount, priority);
    }

    static List<Option> compare(List<Lot> lots, Integer vehicleHeightMm, Integer vehicleWidthMm,
                                DriveType drive, int stayMinutes, boolean qualifiesForPurchaseDiscount,
                                Priority priority) {
        return compare(lots, vehicleHeightMm, vehicleWidthMm, drive, stayMinutes,
                qualifiesForPurchaseDiscount, priority, Set.of());
    }

    static List<Option> compare(List<Lot> lots, Integer vehicleHeightMm, Integer vehicleWidthMm,
                                DriveType drive, int stayMinutes, boolean qualifiesForPurchaseDiscount,
                                Priority priority, Set<Lot> excluded) {
        List<Option> options = new ArrayList<>();
        for (Lot lot : lots) {
            Option evaluated = evaluate(lot, vehicleHeightMm, vehicleWidthMm, drive, stayMinutes, qualifiesForPurchaseDiscount);
            options.add(new Option(lot, evaluated.fit(), evaluated.reason(), evaluated.baseWon(),
                    evaluated.expectedWon(), excluded.contains(lot), evaluated.reasons()));
        }
        Comparator<Option> existing = Comparator.comparing(Option::fit)
                .thenComparing(option -> !option.lot().attached())
                .thenComparing(Option::expectedWon, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Option> fare = Comparator.comparing(Option::expectedWon,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(option -> !option.lot().attached());
        options.sort((left, right) -> {
            boolean leftAvailable = left.requestAvailability().available();
            boolean rightAvailable = right.requestAvailability().available();
            if (leftAvailable != rightAvailable) return leftAvailable ? -1 : 1;
            // Unavailable candidates retain their original verdict and ordering policy.
            return (leftAvailable && priority == Priority.FARE_FIRST ? fare : existing).compare(left, right);
        });
        return options;
    }

    private static Option evaluate(Lot lot, Integer heightMm, Integer widthMm, DriveType drive,
                                   int stayMinutes, boolean qualifiesForPurchaseDiscount) {
        Tariff tariff = lot.tariff();
        Integer base = tariff == null ? null : tariff.quote(stayMinutes, false);
        Integer expected = tariff == null ? null : tariff.quote(stayMinutes, qualifiesForPurchaseDiscount);
        List<FitReason> reasons = new ArrayList<>();
        Integer limitMm = lot.heightLimitMm();
        if (heightMm != null && limitMm != null && heightMm > limitMm) {
            reasons.add(new FitReason(Fit.RESTRICTED, "표기 높이 제한 초과"));
        }
        if (drive == DriveType.ELECTRIC && lot.evEntry() == EvEntry.RESTRICTED) {
            reasons.add(new FitReason(Fit.RESTRICTED, "등록된 전기차 입차 제한"));
        }
        WidthCondition width = lot.widthCondition();
        if (width != null && width.maximumMm() != null && width.maximumMm() > 0
                && widthMm != null && widthMm > width.maximumMm()) {
            reasons.add(new FitReason(Fit.RESTRICTED, "최대 허용 차폭 초과"));
        }
        if (width != null && (width.maximumMm() == null || width.maximumMm() <= 0)) {
            reasons.add(new FitReason(Fit.NEEDS_CHECK, "시설 최대 허용 차폭 확인 필요"));
        }
        if (width != null && (widthMm == null || widthMm <= 0)) {
            reasons.add(new FitReason(Fit.NEEDS_CHECK, "차량 너비 확인 필요"));
        }
        if (heightMm == null) {
            reasons.add(new FitReason(Fit.NEEDS_CHECK, "차량 높이 확인 필요"));
        } else if (limitMm == null || heightMm.equals(limitMm)) {
            reasons.add(new FitReason(Fit.NEEDS_CHECK, "높이 여유 또는 제한 정보 확인 필요"));
        }
        if (drive == DriveType.NEEDS_CHECK && lot.evEntry() != EvEntry.ALLOWED) {
            reasons.add(new FitReason(Fit.NEEDS_CHECK, "차량 연료 유형 확인 필요"));
        }
        if (drive == DriveType.ELECTRIC && lot.evEntry() == EvEntry.UNKNOWN) {
            reasons.add(new FitReason(Fit.NEEDS_CHECK, "전기차 입차 조건 확인 필요"));
        }
        // The original priority is restricted height, EV, width, then uncertainty width,
        // height and fuel. Collect every condition before choosing that representative.
        FitReason primary = reasons.stream().filter(r -> r.classification() == Fit.RESTRICTED)
                .findFirst().orElse(reasons.isEmpty() ? null : reasons.get(0));
        return new Option(lot, primary == null ? Fit.MATCHES_RECORDED_RULES : primary.classification(),
                primary == null ? "등록된 입차 조건 기준 충족" : primary.text(),
                base, expected, false, reasons);
    }

    static Optional<RouteRequest> routeRequest(String visit, Option option) {
        if (!option.requestAvailability().available()) {
            return Optional.empty();
        }
        return Optional.of(new RouteRequest(visit, option.lot().entrance()));
    }

    private ParkingPlan() {}
}
