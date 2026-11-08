package kr.juchakkaji.app;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Owns visit lifetime; only confirm can cross the route-request boundary. */
final class ParkingNavigation {
    enum Status { IDLE, SIMULATED, ACCEPTED, REJECTED, STALE }
    record Receipt(ParkingPlan.RouteRequest request, Status outcome) {}

    static final class Selection {
        private final ParkingNavigation owner;
        private final long generation;
        private final ParkingPlan.RouteRequest request;
        private boolean consumed;
        private Selection(ParkingNavigation owner, long generation, ParkingPlan.RouteRequest request) {
            this.owner = owner;
            this.generation = generation;
            this.request = request;
        }
        ParkingPlan.RouteRequest request() { return request; }
    }
    private final NavigationPort port;
    private final Runnable changed;
    private final Set<Long> sent = new HashSet<>();
    private NavigationPort.Destination visit;
    private long generation;
    private long requestId;
    private Status status = Status.IDLE;
    private Receipt lastRequest;

    ParkingNavigation(NavigationPort port, Runnable changed) {
        this.port = port;
        this.changed = changed;
        port.setDestinationListener(this::receive);
    }
    private void receive(NavigationPort.Destination destination) {
        if (destination.echoedRequestId() != null && sent.contains(destination.echoedRequestId())) return;
        visit = destination;
        generation++;
        status = Status.IDLE;
        lastRequest = null;
        changed.run();
    }
    NavigationPort.Destination visit() { return visit; }
    Status status() { return status; }
    Receipt lastRequest() { return lastRequest; }
    void invalidateSelections() { generation++; }
    List<ParkingPlan.Option> options(Integer height, VehicleProfile.DriveType drive, int minutes, boolean discount) {
        return ParkingPlan.compare(visit == null ? List.of() : visit.lots(), height, drive, minutes, discount);
    }
    Optional<Selection> select(ParkingPlan.Option option) {
        if (visit == null || !visit.lots().contains(option.lot())) return Optional.empty();
        return ParkingPlan.routeRequest(visit.name(), option).map(route -> new Selection(this, generation, route));
    }
    Status confirm(Selection selection) {
        if (selection == null || selection.owner != this || selection.generation != generation || selection.consumed) return Status.STALE;
        selection.consumed = true;
        long id = ++requestId;
        long requestedGeneration = generation;
        sent.add(id); // Register before the call: an adapter can echo synchronously.
        NavigationPort.Result result;
        try {
            result = port.requestEntrance(id, selection.request);
        } catch (RuntimeException failure) {
            result = NavigationPort.Result.REJECTED;
        }
        Status outcome = result == NavigationPort.Result.ACCEPTED ? Status.ACCEPTED
                : result == NavigationPort.Result.SIMULATED ? Status.SIMULATED : Status.REJECTED;
        if (generation == requestedGeneration) {
            lastRequest = new Receipt(selection.request, outcome);
            status = outcome;
            changed.run();
        }
        return outcome;
    }
}
