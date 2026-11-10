package kr.juchakkaji.app;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** One executor entry. Cancellation only prevents a read that has not been claimed yet. */
final class VehicleReadTask implements Runnable {
    private final Supplier<VehicleReadResult> reader;
    private final Consumer<VehicleReadResult> completion;
    private final LongSupplier clock;
    private final long deadline;
    private boolean claimed;
    private boolean cancelled;

    VehicleReadTask(Supplier<VehicleReadResult> reader, Consumer<VehicleReadResult> completion,
                    LongSupplier clock, long deadline) {
        this.reader = reader;
        this.completion = completion;
        this.clock = clock;
        this.deadline = deadline;
    }

    synchronized void cancel() { cancelled = true; }

    @Override public void run() {
        synchronized (this) {
            if (claimed) return;
            claimed = true;
            if (cancelled || clock.getAsLong() >= deadline) return;
        }
        // Do not hold the lock across a blocking API. Already claimed reads may return
        // after cancel; the main-thread generation/deadline gate owns result acceptance.
        completion.accept(reader.get());
    }
}
