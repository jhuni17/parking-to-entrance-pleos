package kr.juchakkaji.app;

/** Main-thread screen/read state; time is supplied by the caller, never by Android. */
final class VehicleRefreshState {
    private final VehicleReadResult.Session session = new VehicleReadResult.Session();
    enum Outcome { IDLE, READING, CONNECTED, DISCONNECTED, UNAVAILABLE, EXPIRED }
    private Outcome outcome = Outcome.IDLE;
    private boolean active;
    private boolean reading;
    private int generation;
    private int nextAttempt = -1;
    private VehicleProfile vehicle = VehicleProfile.UNKNOWN;

    void activate() { active = true; }
    void stop() {
        active = false;
        if (reading) outcome = Outcome.IDLE;
        reading = false;
        nextAttempt = -1;
        session.stop();
    }
    boolean start(long now) {
        if (!active || reading) return false;
        session.start(++generation, now);
        vehicle = VehicleProfile.UNKNOWN;
        reading = true;
        outcome = Outcome.READING;
        nextAttempt = 0;
        return true;
    }
    int generation() { return generation; }
    long deadline() { return session.deadline(); }
    Outcome outcome() { return outcome; }
    boolean reading() { return reading; }
    VehicleProfile vehicle() { return vehicle; }
    boolean isCurrent(int token) { return active && session.isCurrent(token); }

    // Claim each executor submission once, including duplicate retry timers.
    boolean beginRead(int token, int attempt) {
        if (!isCurrent(token) || nextAttempt != attempt) return false;
        nextAttempt = -1;
        return true;
    }
    VehicleReadResult.Session.Action expire(int token, long now) {
        var action = session.expire(token, now);
        if (action == VehicleReadResult.Session.Action.EXPIRE) finish(VehicleProfile.UNKNOWN, Outcome.EXPIRED);
        return action;
    }
    VehicleReadResult.Session.Action accept(int token, int attempt, VehicleReadResult result, long now) {
        var action = session.accept(token, attempt, result, now);
        switch (action) {
            case RETRY -> nextAttempt = 1;
            case COMPLETE -> finish(result.profile(), Outcome.valueOf(result.state().name()));
            case EXPIRE -> finish(VehicleProfile.UNKNOWN, Outcome.EXPIRED);
            case IGNORE -> { }
        }
        return action;
    }
    private void finish(VehicleProfile profile, Outcome outcome) {
        this.outcome = outcome;
        vehicle = profile;
        reading = false;
        nextAttempt = -1;
    }
}
