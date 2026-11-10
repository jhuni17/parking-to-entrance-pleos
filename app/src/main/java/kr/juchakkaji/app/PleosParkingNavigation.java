package kr.juchakkaji.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.NavigationStatus;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.DestinationArrivedInfo;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;

/** Keeps SDK callbacks on the UI thread and waits for the selected entrance to be observed. */
final class PleosParkingNavigation implements NavigationPort, AutoCloseable {
    private static final String TAG = "ParkingNavi";
    private static final String READ_ROUTE = "pleos.car.permission.NAVI_ROUTE";
    private static final String SEARCH_ROUTE = "pleos.car.permission.NAVI_ROUTE_SEARCH";
    private static final long QUERY_TIMEOUT_MS = 8_000;
    private static final long ROUTE_TIMEOUT_MS = 25_000;

    private record Pending(long id, RouteInfo target) {}

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Consumer<String> statusChanged;
    private final boolean debug;
    private Consumer<Destination> destinationChanged;
    private BiConsumer<Long, Result> routeResult;
    private NaviHelper helper;
    private boolean connected;
    private boolean closed;
    private boolean checking;
    private boolean routeReady;
    private Pending pending;
    private String lastDestination;

    private final Runnable queryTimeout = this::onQueryTimeout;
    private final Runnable routeTimeout = () -> finishRoute(Result.REJECTED);
    private final Runnable pollRoute = this::pollRoute;

    private void onQueryTimeout() {
        checking = false;
        if (!connected) {
            statusChanged.accept("내비 연결을 확인해 주세요");
        } else if (pending == null) {
            clearVisit();
        }
    }

    private final NaviHelperEventListener listener = new NaviHelperEventListener() {
        @Override public void onDestinationInfo(DestinationInfo info) {
            dispatch(() -> receive(info));
        }

        @Override public void onDestinationChanged() { dispatch(PleosParkingNavigation.this::refresh); }

        @Override public void onRouteStarted(RouteStartInfo info) {
            dispatch(() -> trace("route search started: " + info.getName()));
        }

        @Override public void onRoutingCompleted() {
            dispatch(() -> {
                trace("routing completed");
                routeReady = true;
                refresh();
            });
        }

        @Override public void onNavigationStatus(NavigationStatus state) {
            if (state == NavigationStatus.ROUTE_DRIVE) dispatch(() -> {
                trace("route drive active");
                routeReady = true;
                refresh();
            });
        }

        @Override public void onRouteCancelled() {
            dispatch(() -> { if (pending == null) clearVisit(); });
        }

        @Override public void onDestinationArrived(DestinationArrivedInfo info) {
            dispatch(PleosParkingNavigation.this::clearVisit);
        }

        @Override public void onError(NaviErrorCode error) {
            dispatch(() -> {
                trace("navigation error: " + error);
                finishRoute(Result.REJECTED);
            });
        }
    };

    PleosParkingNavigation(Context context, Consumer<String> statusChanged) {
        this.context = context;
        this.statusChanged = statusChanged;
        debug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    void start() {
        if (helper != null || closed) return;
        if (!hasPermission(READ_ROUTE)) {
            statusChanged.accept("내비 목적지 조회 권한이 필요해요");
            return;
        }
        try {
            helper = new NaviHelper(context);
            main.postDelayed(queryTimeout, QUERY_TIMEOUT_MS);
            helper.initialize(() -> {
                dispatch(() -> {
                    try {
                        helper.addListener(listener);
                        connected = true;
                        trace("NaviHelper connected; route-search permission=" + hasPermission(SEARCH_ROUTE));
                        refresh();
                    } catch (RuntimeException failure) {
                        connectionFailed(failure);
                    }
                });
                return Unit.INSTANCE;
            }, failure -> {
                dispatch(() -> connectionFailed(failure));
                return Unit.INSTANCE;
            });
        } catch (RuntimeException | LinkageError failure) {
            connectionFailed(failure);
        }
    }

    void refresh() {
        if (!connected || !hasPermission(READ_ROUTE)) return;
        try {
            checking = true;
            helper.getDestinationInfo();
            main.removeCallbacks(queryTimeout);
            main.postDelayed(queryTimeout, QUERY_TIMEOUT_MS);
        } catch (RuntimeException failure) {
            connectionFailed(failure);
        }
    }

    @Override public void setDestinationListener(Consumer<Destination> listener) {
        destinationChanged = listener;
    }

    @Override public void setRouteResultListener(BiConsumer<Long, Result> listener) {
        routeResult = listener;
    }

    @Override public Result requestEntrance(long id, ParkingPlan.RouteRequest request) {
        if (!connected || checking || pending != null || !hasPermission(SEARCH_ROUTE)) return Result.REJECTED;
        RouteInfo target = CoexParking.route(request.entrance());
        if (target == null) target = SincheonParking.route(request.entrance());
        if (target == null) return Result.REJECTED;
        pending = new Pending(id, target);
        routeReady = false;
        try {
            trace("request " + id + ": " + target.getPoiName() + " " + target.getLatitude() + "," + target.getLongitude());
            helper.requestRoute(target);
            main.postDelayed(routeTimeout, ROUTE_TIMEOUT_MS);
            main.postDelayed(pollRoute, 2_000);
            return Result.PENDING;
        } catch (RuntimeException failure) {
            pending = null;
            Log.w(TAG, "Entrance route request failed", failure);
            return Result.REJECTED;
        }
    }

    private void pollRoute() {
        if (pending == null) return;
        refresh();
        if (pending != null) main.postDelayed(pollRoute, 2_000);
    }

    private void receive(DestinationInfo info) {
        main.removeCallbacks(queryTimeout);
        checking = false;
        if (info == null || info.getName() == null || info.getName().isBlank()
                || !validCoordinates(info.getLatitude(), info.getLongitude())) {
            if (pending == null) clearVisit();
            return;
        }
        trace("destination: " + info.getName() + " " + info.getLatitude() + "," + info.getLongitude()
                + " poi=" + info.getPoiId() + "/" + info.getPoiSubId() + "/" + info.getPoiProvider());
        String key = info.getPoiId() + "|" + info.getPoiSubId() + "|"
                + info.getName() + "|" + info.getLatitude() + "|" + info.getLongitude();
        if (pending != null && sameEntrance(info, pending.target())) {
            if (routeReady) {
                lastDestination = key;
                finishRoute(Result.ACCEPTED);
            }
            return;
        }
        if (key.equals(lastDestination)) return;
        finishRoute(Result.REJECTED);
        lastDestination = key;
        statusChanged.accept("");
        List<ParkingPlan.Lot> lots = CoexParking.lotsFor(info.getName(), info.getLatitude(), info.getLongitude(),
                Build.FINGERPRINT.startsWith("Pleos/crp_avd/"));
        SeoulParking.Area area = SeoulParking.areaFor(info.getName(), info.getLatitude(),
                info.getLongitude(), !lots.isEmpty());
        if (area == SeoulParking.Area.JAMSIL) lots = List.of(SincheonParking.LOT);
        destinationChanged.accept(new Destination(key, info.getName(), lots, null, area));
    }

    static boolean sameEntrance(DestinationInfo observed, RouteInfo target) {
        boolean sameCoordinates = Math.abs(observed.getLatitude() - target.getLatitude()) < 0.0002
                && Math.abs(observed.getLongitude() - target.getLongitude()) < 0.0002;
        String observedId = observed.getPoiId();
        if (!target.getPoiId().isEmpty() && observedId != null && !observedId.isEmpty()) {
            if (!target.getPoiId().equals(observedId)) return false;
            String observedSubId = observed.getPoiSubId();
            // NaviHelper can omit the sub-ID even when it returns the exact requested driving point.
            return sameCoordinates && (target.getPoiSubId().equals(observedSubId)
                    || observedSubId == null || observedSubId.isEmpty());
        }
        return target.getPoiName().equals(observed.getName()) && sameCoordinates;
    }

    private static boolean validCoordinates(double latitude, double longitude) {
        return Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
                && (latitude != 0 || longitude != 0);
    }

    private void clearVisit() {
        finishRoute(Result.REJECTED);
        checking = false;
        main.removeCallbacks(queryTimeout);
        statusChanged.accept("Pleos 내비에서 목적지를 설정해 주세요");
        if (lastDestination != null) {
            lastDestination = null;
            destinationChanged.accept(null);
        }
    }

    private void finishRoute(Result result) {
        if (pending == null) return;
        long id = pending.id();
        pending = null;
        main.removeCallbacks(routeTimeout);
        main.removeCallbacks(pollRoute);
        trace("request " + id + " result: " + result);
        routeResult.accept(id, result);
    }

    private boolean hasPermission(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void connectionFailed(Throwable failure) {
        Log.w(TAG, "NaviHelper connection failed", failure);
        connected = false;
        clearVisit();
        statusChanged.accept("내비에 연결하지 못했어요");
    }

    private void dispatch(Runnable work) {
        main.post(() -> { if (!closed) work.run(); });
    }

    private void trace(String text) {
        if (debug) Log.i(TAG, text);
    }

    @Override public void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        if (helper == null) return;
        try { if (connected) helper.removeListener(listener); }
        catch (RuntimeException failure) { Log.w(TAG, "Navi listener cleanup failed", failure); }
        try { helper.release(); }
        catch (RuntimeException failure) { Log.w(TAG, "Navi release failed", failure); }
    }
}
