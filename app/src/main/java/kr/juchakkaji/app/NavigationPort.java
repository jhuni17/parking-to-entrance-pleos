package kr.juchakkaji.app;

import java.util.List;
import java.util.function.Consumer;

/** Adapter boundary: an SDK adapter must correlate its route echoes with requestId.
 * Uncorrelated events are new visits, even if their coordinates match an entrance. */
interface NavigationPort {
    record Destination(String id, String name, List<ParkingPlan.Lot> lots,
                       Long echoedRequestId) {
        Destination { lots = List.copyOf(lots); }
    }
    enum Result { SIMULATED, ACCEPTED, REJECTED }

    void setDestinationListener(Consumer<Destination> listener);
    Result requestEntrance(long requestId, ParkingPlan.RouteRequest request);
}
