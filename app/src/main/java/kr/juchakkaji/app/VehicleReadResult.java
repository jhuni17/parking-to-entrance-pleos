package kr.juchakkaji.app;

/** Connection state is independent of missing/unsupported vehicle properties. */
record VehicleReadResult(State state, VehicleProfile profile) {
    enum State { CONNECTED, DISCONNECTED, UNAVAILABLE }

    static VehicleReadResult connected(VehicleProfile profile) {
        return new VehicleReadResult(State.CONNECTED, profile);
    }

    static VehicleReadResult disconnected() {
        return new VehicleReadResult(State.DISCONNECTED, VehicleProfile.UNKNOWN);
    }

    static VehicleReadResult unavailable() {
        return new VehicleReadResult(State.UNAVAILABLE, VehicleProfile.UNKNOWN);
    }

    /** Main-thread policy, shared by Activity and deterministic synthetic tests. */
    static final class Session {
        static final long RETRY_DELAY_MS = 500;
        // App policy, not an SDK response-time guarantee. Uses monotonic uptime.
        static final long WAIT_BUDGET_MS = 3000;
        enum Action { IGNORE, RETRY, COMPLETE, EXPIRE }
        private int generation;
        private int expectedAttempt;
        private boolean active;
        private long deadline;

        void start(int generation, long now) {
            deadline = now + WAIT_BUDGET_MS;
            this.generation = generation;
            expectedAttempt = 0;
            active = true;
        }

        void stop() {
            active = false;
        }

        boolean isCurrent(int generation) {
            return active && this.generation == generation;
        }

        long deadline() { return deadline; }

        Action expire(int generation, long now) {
            if (!isCurrent(generation) || now < deadline) return Action.IGNORE;
            active = false;
            return Action.EXPIRE;
        }

        Action accept(int generation, int attempt, VehicleReadResult result, long now) {
            if (!isCurrent(generation) || attempt != expectedAttempt) return Action.IGNORE;
            if (now >= deadline) return expire(generation, now);
            if (attempt == 0 && result.state == State.DISCONNECTED) {
                expectedAttempt = 1;
                return Action.RETRY;
            }
            active = false;
            return Action.COMPLETE;
        }
    }
}
