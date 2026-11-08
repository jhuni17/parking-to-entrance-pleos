package kr.juchakkaji.app;

/** Visit-owned assumptions, independent of Android and selection expiration. */
final class VisitQuoteState {
    static final class Token {
        private final VisitQuoteState owner;
        private final long visitVersion;
        private Token(VisitQuoteState owner, long visitVersion) {
            this.owner = owner;
            this.visitVersion = visitVersion;
        }
    }

    private int stayMinutes;
    private boolean purchaseDiscount;
    private long visitVersion;

    VisitQuoteState(int restoredStayMinutes) {
        stayMinutes = validMinutes(restoredStayMinutes) ? restoredStayMinutes : 120;
    }

    void syncVisit(long version) {
        if (visitVersion == version) return;
        visitVersion = version;
        purchaseDiscount = false;
    }

    int stayMinutes() { return stayMinutes; }
    boolean purchaseDiscount() { return purchaseDiscount; }
    Token open() { return new Token(this, visitVersion); }

    // App input policy, not a facility restriction.
    private static boolean validMinutes(int minutes) { return minutes >= 1 && minutes <= 360; }

    String durationText() { return stayMinutes + "분 예상"; }

    boolean apply(Token token, String minutes, boolean discount) {
        if (minutes == null || !minutes.matches("[0-9]+")) return false;
        try {
            return apply(token, Integer.parseInt(minutes), discount);
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    boolean apply(Token token, int minutes, boolean discount) {
        if (!validMinutes(minutes)) return false;
        if (token == null || token.owner != this || token.visitVersion != visitVersion) return false;
        stayMinutes = minutes;
        purchaseDiscount = discount;
        return true;
    }
}
