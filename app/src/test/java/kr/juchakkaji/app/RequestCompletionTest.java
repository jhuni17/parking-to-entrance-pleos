package kr.juchakkaji.app;

import android.car.FuelType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;
import static kr.juchakkaji.app.ParkingPlan.*;

/** Deterministic synthetic hardware and reentrant port calls; no Android/SDK observation. */
public final class RequestCompletionTest {
    static final class Fake implements NavigationPort {
        Consumer<Destination> listener;
        final List<Long> ids = new ArrayList<>();
        final List<RouteRequest> requests = new ArrayList<>();
        Runnable during = () -> {};
        Result result = Result.ACCEPTED;
        boolean fail;
        public void setDestinationListener(Consumer<Destination> listener) { this.listener = listener; }
        void visit() { listener.accept(new Destination("same", SampleTrip.VISIT, SampleTrip.LOTS, null)); }
        void echo(long id) { listener.accept(new Destination("echo", "entrance", List.of(), id)); }
        public Result requestEntrance(long id, RouteRequest request) {
            ids.add(id);
            requests.add(request);
            Result returned = result;
            boolean throwsFailure = fail;
            Runnable callback = during;
            during = () -> {};
            callback.run();
            if (throwsFailure) throw new RuntimeException("synthetic rejection");
            return returned;
        }
    }
    static final class Fixture {
        final Fake fake = new Fake();
        final ParkingNavigation nav = new ParkingNavigation(fake, () -> {});
        final VehicleRefreshState vehicle = new VehicleRefreshState();
        Fixture() { fake.visit(); vehicle.activate(); update(1850); }
        void update(int height) {
            assertTrue(vehicle.start(0));
            nav.invalidateSelections();
            int token = vehicle.generation();
            assertTrue(vehicle.beginRead(token, 0));
            VehicleProfile profile = VehicleProfile.fromHardware(null, "Synthetic", null,
                    new Integer[]{height, 4700, 1990}, new Integer[]{FuelType.UNLEADED});
            assertEquals(VehicleProfile.DriveType.NON_ELECTRIC, profile.drive());
            assertEquals(VehicleReadResult.Session.Action.COMPLETE,
                    vehicle.accept(token, 0, VehicleReadResult.connected(profile), 1));
            assertEquals(Integer.valueOf(height), vehicle.vehicle().heightMm());
        }
        List<Option> options(Priority priority) {
            VehicleProfile v = vehicle.vehicle();
            return nav.options(v.heightMm(), v.widthMm(), v.drive(), 120, true, priority);
        }
        EntranceConfirmation preview() {
            return EntranceConfirmation.create(nav, options(Priority.ATTACHED_FIRST).get(0), 120, true).orElseThrow();
        }
        RequestRecheck model() {
            return RequestRecheck.create(nav.lastRequest(), vehicle.reading(), options(Priority.ATTACHED_FIRST));
        }
    }

    @Test public void refreshDuringAcceptedAndSimulatedRequestRetainsReceiptAndRechecksAlternative() {
        for (NavigationPort.Result result : List.of(NavigationPort.Result.ACCEPTED, NavigationPort.Result.SIMULATED)) {
            Fixture f = new Fixture();
            f.fake.result = result;
            var visit = f.nav.visit();
            long version = f.nav.visitVersion();
            var preview = f.preview();
            assertEquals(Integer.valueOf(0), preview.expectedWon());
            assertEquals(0, f.fake.requests.size());
            f.fake.during = () -> { f.fake.echo(f.fake.ids.get(0)); f.update(2250); };
            var expected = ParkingNavigation.Status.valueOf(result.name());
            assertEquals(expected, f.nav.confirm(preview.selection()));
            var receipt = f.nav.lastRequest();
            assertNotNull(receipt); // Baseline loses this result when refresh changes generation.
            assertSame(SampleTrip.LOTS.get(0), receipt.lot());
            assertSame(preview.selection().request(), receipt.request());
            assertEquals(SampleTrip.VISIT, receipt.request().visit());
            assertSame(receipt.lot().entrance(), receipt.request().entrance());
            assertEquals(expected, receipt.outcome());
            assertEquals(expected, f.nav.status());
            assertEquals(List.of(1L), f.fake.ids);
            assertEquals(1, f.fake.requests.size());
            assertEquals(ParkingNavigation.Status.STALE, f.nav.confirm(preview.selection()));
            f.fake.echo(1L); // Delayed echo preserves the exact receipt and visit.
            assertSame(receipt, f.nav.lastRequest());
            assertSame(visit, f.nav.visit());
            assertEquals(version, f.nav.visitVersion());
            for (Priority priority : Priority.values()) {
                var options = f.options(priority);
                assertEquals(Fit.RESTRICTED, options.stream().filter(o -> o.lot() == receipt.lot()).findFirst().orElseThrow().fit());
                assertSame(SampleTrip.LOTS.get(1), options.get(0).lot());
                assertEquals(Integer.valueOf(6000), options.get(0).expectedWon());
                assertEquals(1, requestableCount(options));
                var model = RequestRecheck.create(receipt, false, options);
                assertSame(receipt, model.receipt());
                assertEquals(RequestRecheck.State.RECHECK, model.state());
                assertTrue(model.summary().contains("입구 재확인 필요"));
            }
            var explanation = RecommendationExplanation.create(f.vehicle.vehicle(), false, f.options(Priority.ATTACHED_FIRST));
            assertTrue(explanation.vehicleDetails().contains("2250mm · 차량 속성값"));
            assertFalse(explanation.cardHeadline().contains("\n"));
            var next = f.preview();
            assertEquals(Integer.valueOf(6000), next.expectedWon());
            assertEquals(1, f.fake.requests.size());
            assertEquals(expected, f.nav.confirm(next.selection()));
            assertEquals(List.of(1L, 2L), f.fake.ids);
            assertEquals(2, f.fake.requests.size());
            assertEquals(SampleTrip.VISIT, f.fake.requests.get(1).visit());
            assertEquals(37.51400, f.fake.requests.get(1).entrance().latitude(), 0.0);
            assertEquals(127.06100, f.fake.requests.get(1).entrance().longitude(), 0.0);
        }
    }

    @Test public void rejectionDuringRefreshReplacesPreviousSuccess() { failure(false); }
    @Test public void exceptionDuringRefreshReplacesPreviousSuccess() { failure(true); }
    private void failure(boolean exception) {
        Fixture f = new Fixture();
        assertEquals(ParkingNavigation.Status.ACCEPTED, f.nav.confirm(f.preview().selection()));
        var previous = f.nav.lastRequest();
        var selection = f.preview().selection();
        long version = f.nav.visitVersion();
        f.fake.result = NavigationPort.Result.REJECTED;
        f.fake.fail = exception;
        f.fake.during = () -> f.update(2250);
        assertEquals(ParkingNavigation.Status.REJECTED, f.nav.confirm(selection));
        assertNotSame(previous, f.nav.lastRequest());
        assertSame(selection.request(), f.nav.lastRequest().request());
        assertEquals(ParkingNavigation.Status.REJECTED, f.nav.lastRequest().outcome());
        assertEquals(ParkingNavigation.Status.REJECTED, f.nav.status());
        assertEquals(version, f.nav.visitVersion());
        assertSame(f.nav.lastRequest(), f.model().receipt());
        assertTrue(f.model().summary().contains("입구 경로 요청 실패"));
        assertEquals(ParkingNavigation.Status.STALE, f.nav.confirm(selection));
        assertEquals(List.of(1L, 2L), f.fake.ids);
        assertEquals(2, f.fake.requests.size());
    }

    @Test public void identicalDestinationEventDuringRequestIsNewVisit() {
        Fixture f = new Fixture();
        var previous = f.nav.visit();
        long version = f.nav.visitVersion();
        f.fake.during = f.fake::visit;
        assertEquals(ParkingNavigation.Status.ACCEPTED, f.nav.confirm(f.preview().selection()));
        assertEquals(previous, f.nav.visit());
        assertNotSame(previous, f.nav.visit());
        assertEquals(version + 1, f.nav.visitVersion());
        assertNull(f.nav.lastRequest());
        assertEquals(ParkingNavigation.Status.IDLE, f.nav.status());
        assertEquals(List.of(1L), f.fake.ids);
        assertEquals(1, f.fake.requests.size());
    }

    @Test public void newerReentrantCompletionOwnsReceiptDespiteOlderLateSuccess() {
        Fixture f = new Fixture();
        long version = f.nav.visitVersion();
        ParkingNavigation.Receipt[] latest = new ParkingNavigation.Receipt[1];
        f.fake.during = () -> {
            f.update(2250);
            f.fake.result = NavigationPort.Result.REJECTED;
            var next = f.preview().selection();
            assertEquals(ParkingNavigation.Status.REJECTED, f.nav.confirm(next));
            latest[0] = f.nav.lastRequest();
            assertSame(next.request(), latest[0].request());
        };
        assertEquals(ParkingNavigation.Status.ACCEPTED, f.nav.confirm(f.preview().selection()));
        assertNotNull(latest[0]);
        assertSame(latest[0], f.nav.lastRequest());
        assertSame(SampleTrip.LOTS.get(1), latest[0].lot());
        assertEquals(ParkingNavigation.Status.REJECTED, latest[0].outcome());
        assertEquals(ParkingNavigation.Status.REJECTED, f.nav.status());
        assertEquals(version, f.nav.visitVersion());
        assertEquals(List.of(1L, 2L), f.fake.ids);
        assertEquals(2, f.fake.requests.size());
        f.fake.echo(1L);
        f.fake.echo(2L);
        assertEquals(version, f.nav.visitVersion());
        assertSame(latest[0], f.nav.lastRequest());
    }

    @Test public void refreshBeforeSendingExpiresSelectionWithoutRequest() {
        Fixture f = new Fixture();
        var selection = f.preview().selection();
        f.update(2250);
        assertEquals(ParkingNavigation.Status.STALE, f.nav.confirm(selection));
        assertEquals(0, f.fake.requests.size());
        assertTrue(f.fake.ids.isEmpty());
        assertNull(f.nav.lastRequest());
        assertEquals(ParkingNavigation.Status.IDLE, f.nav.status());
    }
}
