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
        stayMinutes = restoredStayMinutes;
    }

    void syncVisit(long version) {
        if (visitVersion == version) return;
        visitVersion = version;
        purchaseDiscount = false;
    }

    int stayMinutes() { return stayMinutes; }
    boolean purchaseDiscount() { return purchaseDiscount; }
    Token open() { return new Token(this, visitVersion); }

    boolean apply(Token token, int minutes, boolean discount) {
        if (token == null || token.owner != this || token.visitVersion != visitVersion) return false;
        stayMinutes = minutes;
        purchaseDiscount = discount;
        return true;
    }
}
