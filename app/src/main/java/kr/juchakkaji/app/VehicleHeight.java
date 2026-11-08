package kr.juchakkaji.app;

/** Confirmed height lives with this object; input authority lives only for an active session. */
final class VehicleHeight {
    private long generation;
    private boolean active;
    private Integer confirmedMm;

    long start() {
        generation++;
        active = true;
        return generation;
    }

    void stop() { active = false; }
    long session() { return generation; }
    boolean isCurrent(long session) { return active && session == generation; }
    Integer confirmedMm() { return confirmedMm; }

    static Integer parse(String input) {
        if (input == null || !input.matches("[0-9]+")) return null;
        try {
            int value = Integer.parseInt(input);
            return value >= 1000 && value <= 4000 ? value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    boolean apply(long session, String input) {
        Integer parsed = parse(input);
        if (!isCurrent(session) || parsed == null) return false;
        confirmedMm = parsed;
        return true;
    }

    boolean clear(long session) {
        if (!isCurrent(session)) return false;
        confirmedMm = null;
        return true;
    }

    Integer effectiveMm(VehicleProfile original) {
        if (confirmedMm == null) return original.heightMm();
        return original.heightMm() == null ? confirmedMm : Math.max(original.heightMm(), confirmedMm);
    }
}
