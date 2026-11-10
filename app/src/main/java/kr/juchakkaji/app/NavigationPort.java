package kr.juchakkaji.app;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Adapter boundary: an SDK adapter must correlate its route echoes with requestId.
 * Uncorrelated events are new visits, even if their coordinates match an entrance. */
interface NavigationPort {
    record Destination(String id, String name, List<ParkingPlan.Lot> lots,
                       Long echoedRequestId, SeoulParking.Area publicParkingArea) {
        public Destination { lots = List.copyOf(lots); }
        public Destination(String id, String name, List<ParkingPlan.Lot> lots, Long echoedRequestId) {
            this(id, name, lots, echoedRequestId, null);
        }
    }
    enum Result { SIMULATED, PENDING, ACCEPTED, REJECTED }

    void setDestinationListener(Consumer<Destination> listener);
    default void setRouteResultListener(BiConsumer<Long, Result> listener) {}
    Result requestEntrance(long requestId, ParkingPlan.RouteRequest request);
}
