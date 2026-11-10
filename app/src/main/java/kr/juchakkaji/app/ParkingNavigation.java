package kr.juchakkaji.app;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Owns visit lifetime; only confirm can cross the route-request boundary. */
final class ParkingNavigation {
    enum Status { IDLE, SIMULATED, PENDING, ACCEPTED, REJECTED, STALE }
    record Receipt(ParkingPlan.Lot lot, ParkingPlan.RouteRequest request, Status outcome) {
        // Retain the existing request/outcome value contract for historical receipts.
        // Facility membership is checked explicitly by identity, never Receipt.equals.
        Receipt(ParkingPlan.RouteRequest request, Status outcome) { this(null, request, outcome); }
        @Override public boolean equals(Object other) {
            return other instanceof Receipt receipt
                    && java.util.Objects.equals(request, receipt.request) && outcome == receipt.outcome;
        }
        @Override public int hashCode() { return java.util.Objects.hash(request, outcome); }
    }

    static final class Selection {
        private final ParkingNavigation owner;
        private final long generation;
        private final ParkingPlan.RouteRequest request;
        private final ParkingPlan.Lot lot;
        private boolean consumed;
        private Selection(ParkingNavigation owner, long generation, ParkingPlan.Lot lot, ParkingPlan.RouteRequest request) {
            this.owner = owner;
            this.generation = generation;
            this.request = request;
            this.lot = lot;
        }
        ParkingPlan.RouteRequest request() { return request; }
    }
    private final NavigationPort port;
    private final Runnable changed;
    private final Set<Long> sent = new HashSet<>();
    // Identity is scoped to the exact facilities supplied for this visit, never a name.
    private final Set<ParkingPlan.Lot> excluded = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    // Only objects issued by this instance in the current generation can be selected.
    // Keep earlier issuances when options are recalculated within that generation.
    private final Set<ParkingPlan.Option> issuedOptions = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private NavigationPort.Destination visit;
    private long generation;
    private long visitVersion;
    private long requestId;
    private Status status = Status.IDLE;
    private Receipt lastRequest;

    ParkingNavigation(NavigationPort port, Runnable changed) {
        this.port = port;
        this.changed = changed;
        port.setDestinationListener(this::receive);
        port.setRouteResultListener(this::receiveRouteResult);
    }
    private void receive(NavigationPort.Destination destination) {
        if (destination != null && destination.echoedRequestId() != null
                && sent.contains(destination.echoedRequestId())) return;
        excluded.clear();
        visit = destination;
        visitVersion++;
        invalidateSelections();
        status = Status.IDLE;
        lastRequest = null;
        changed.run();
    }

    private void receiveRouteResult(long id, NavigationPort.Result result) {
        if (id != requestId || status != Status.PENDING
                || (result != NavigationPort.Result.ACCEPTED && result != NavigationPort.Result.REJECTED)) return;
        status = statusOf(result);
        lastRequest = new Receipt(lastRequest.lot(), lastRequest.request(), status);
        changed.run();
    }
    NavigationPort.Destination visit() { return visit; }
    long visitVersion() { return visitVersion; }
    boolean setExcluded(long version, ParkingPlan.Lot lot, boolean value) {
        if (version != visitVersion || !belongsToVisit(lot)) return false;
        boolean updated = value ? excluded.add(lot) : excluded.remove(lot);
        if (updated) {
            invalidateSelections();
            changed.run();
        }
        return updated;
    }
    private boolean belongsToVisit(ParkingPlan.Lot lot) {
        return visit != null && visit.lots().stream().anyMatch(member -> member == lot);
    }
    Status status() { return status; }
    Receipt lastRequest() { return lastRequest; }
    void invalidateSelections() {
        generation++;
        issuedOptions.clear();
    }
    List<ParkingPlan.Option> options(Integer height, VehicleProfile.DriveType drive, int minutes, boolean discount) {
        return options(height, drive, minutes, discount, ParkingPlan.Priority.ATTACHED_FIRST);
    }
    List<ParkingPlan.Option> options(Integer height, VehicleProfile.DriveType drive, int minutes,
                                    boolean discount, ParkingPlan.Priority priority) {
        return options(height, null, drive, minutes, discount, priority);
    }
    List<ParkingPlan.Option> options(Integer height, Integer width, VehicleProfile.DriveType drive,
                                    int minutes, boolean discount, ParkingPlan.Priority priority) {
        List<ParkingPlan.Option> options = ParkingPlan.compare(visit == null ? List.of() : visit.lots(), height, width, drive, minutes, discount, priority, excluded);
        issuedOptions.addAll(options);
        return options;
    }
    Optional<Selection> select(ParkingPlan.Option option) {
        if (!issuedOptions.contains(option)) return Optional.empty();
        if (!belongsToVisit(option.lot()) || excluded.contains(option.lot())) return Optional.empty();
        return ParkingPlan.routeRequest(visit.name(), option).map(route -> new Selection(this, generation, option.lot(), route));
    }
    Status confirm(Selection selection) {
        if (selection == null || selection.owner != this || selection.generation != generation
                || selection.consumed || status == Status.PENDING) return Status.STALE;
        selection.consumed = true;
        long id = ++requestId;
        long requestedVisit = visitVersion;
        sent.add(id); // Register before the call: an adapter can echo synchronously.
        NavigationPort.Result result;
        try {
            result = port.requestEntrance(id, selection.request);
        } catch (RuntimeException failure) {
            result = NavigationPort.Result.REJECTED;
        }
        Status outcome = statusOf(result);
        // Refresh expires selections, but cannot erase a sent request's result.
        // A new visit or a newer reentrant request owns subsequent presentation.
        if (visitVersion == requestedVisit && requestId == id) {
            lastRequest = new Receipt(selection.lot, selection.request, outcome);
            status = outcome;
            changed.run();
        }
        return outcome;
    }

    private static Status statusOf(NavigationPort.Result result) {
        if (result == null) return Status.REJECTED;
        return switch (result) {
            case ACCEPTED -> Status.ACCEPTED;
            case PENDING -> Status.PENDING;
            case SIMULATED -> Status.SIMULATED;
            case REJECTED -> Status.REJECTED;
        };
    }
}
