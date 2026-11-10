package kr.juchakkaji.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static kr.juchakkaji.app.ParkingPlan.*;

public final class MainActivity extends Activity {
    private static final String STAY_MINUTES = "demoProfile.stayMinutes";

    private final ExecutorService carReader = Executors.newSingleThreadExecutor();
    private final ExecutorService parkingReader = Executors.newSingleThreadExecutor();
    private final Handler parkingHandler = new Handler(Looper.getMainLooper());
    private final Runnable nextParkingRead = this::readPublicParking;
    private SeoulParking.Area parkingArea;
    private int parkingGeneration;
    private boolean parkingActive;
    private boolean parkingLoaded;
    private List<SeoulParking.LotStatus> publicLots = List.of();
    private Option sincheonOption;
    private ParkingNavigation navigation;
    private PleosParkingNavigation pleosNavigation;
    private String navigationStatus = "Pleos 내비에 연결 중";
    private final VehicleHeight totalHeight = new VehicleHeight();
    private final Handler vehicleHandler = new Handler(Looper.getMainLooper());
    private final VehicleRefreshState vehicleState = new VehicleRefreshState();
    private VehicleReadTask pendingVehicleTask;
    private Runnable pendingVehicleRetry;
    private Runnable pendingVehicleExpiry;
    private Priority priority = Priority.ATTACHED_FIRST;
    private VisitQuoteState quote = new VisitQuoteState(120);

    private Typeface font;
    private boolean night;
    private int surface;
    private int cardColor;
    private int ink;
    private int muted;
    private int strong;
    private int onStrong;
    private TextView destination;
    private TextView status;
    private TextView receipt;
    private LinearLayout list;
    private LinearLayout parkingSection;
    private AlertDialog detailsDialog;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        quote = new VisitQuoteState(saved == null ? 120 : saved.getInt(STAY_MINUTES, 120));
        font = getResources().getFont(R.font.asta_sans);
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        surface = night ? 0xff242529 : 0xffe9e9ed;
        cardColor = night ? 0xff33343a : 0xfffbfbfc;
        ink = night ? 0xfff6f6f9 : 0xff16171a;
        muted = night ? 0xffc8c9d0 : 0xff56585f;
        strong = night ? 0xfff1f1f4 : 0xff24252a;
        onStrong = night ? 0xff18191d : 0xffffffff;
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
        getWindow().getDecorView().setSystemUiVisibility(night ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(surface);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(20));
        scroll.addView(content);
        setContentView(scroll);

        content.addView(label("주차까지", 15, 500, muted));
        LinearLayout header = row();
        destination = label("", 27, 700, ink);
        header.addView(destination, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mode = label("Pleos 내비", 13, 500, muted);
        mode.setPadding(dp(8), 0, 0, 0);
        header.addView(mode);
        content.addView(header);
        status = label("", 15, 500, muted);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        list = column();
        content.addView(list);
        parkingSection = column();
        content.addView(parkingSection);
        receipt = label("", 14, 500, muted);
        receipt.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(receipt);

        pleosNavigation = new PleosParkingNavigation(getApplicationContext(), message -> {
            navigationStatus = message;
            if (navigation.visit() == null) setStatus(message);
        });
        navigation = new ParkingNavigation(pleosNavigation, this::refresh);
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        parkingActive = true;
        pleosNavigation.start();
        totalHeight.start();
        vehicleState.activate();
        startVehicleRead();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pleosNavigation.refresh();
    }

    private void startVehicleRead() {
        if (!vehicleState.start(SystemClock.uptimeMillis())) return;
        int generation = vehicleState.generation();
        cancelVehicleTask();
        // UNKNOWN + refresh immediately expires every previously opened selection.
        refresh();
        vehicleHandler.removeCallbacksAndMessages(null);
        pendingVehicleRetry = null;
        pendingVehicleExpiry = () -> {
            if (isDestroyed()) return;
            if (vehicleState.expire(generation, SystemClock.uptimeMillis())
                    == VehicleReadResult.Session.Action.EXPIRE) finishVehicleRead();
        };
        vehicleHandler.postAtTime(pendingVehicleExpiry, vehicleState.deadline());
        readVehicle(generation, 0);
    }

    private void readVehicle(int generation, int attempt) {
        if (!vehicleState.isCurrent(generation) || isDestroyed()) return;
        if (vehicleState.expire(generation, SystemClock.uptimeMillis())
                == VehicleReadResult.Session.Action.EXPIRE) {
            finishVehicleRead();
            return;
        }
        if (!vehicleState.beginRead(generation, attempt)) return;
        var context = getApplicationContext();
        cancelVehicleTask();
        pendingVehicleTask = new VehicleReadTask(() -> AndroidVehicleInfo.read(context), result ->
            vehicleHandler.post(() -> {
                if (isDestroyed()) return;
                VehicleReadResult.Session.Action action = vehicleState.accept(generation, attempt, result, SystemClock.uptimeMillis());
                if (action == VehicleReadResult.Session.Action.IGNORE) return;
                if (action == VehicleReadResult.Session.Action.RETRY) {
                    pendingVehicleRetry = () -> {
                        if (!vehicleState.isCurrent(generation) || isDestroyed()) return;
                        pendingVehicleRetry = null;
                        readVehicle(generation, 1);
                    };
                    vehicleHandler.postDelayed(pendingVehicleRetry, VehicleReadResult.Session.RETRY_DELAY_MS);
                    return;
                }
                finishVehicleRead();
            }), SystemClock::uptimeMillis, vehicleState.deadline());
        carReader.execute(pendingVehicleTask);
    }

    private void cancelVehicleTask() {
        if (pendingVehicleTask != null) pendingVehicleTask.cancel();
        pendingVehicleTask = null;
    }

    private void finishVehicleRead() {
        cancelVehicleTask();
        if (pendingVehicleRetry != null) vehicleHandler.removeCallbacks(pendingVehicleRetry);
        if (pendingVehicleExpiry != null) vehicleHandler.removeCallbacks(pendingVehicleExpiry);
        pendingVehicleRetry = null;
        pendingVehicleExpiry = null;
        refresh();
    }

    private void stopVehicleRead() {
        cancelVehicleTask();
        totalHeight.stop();
        vehicleState.stop();
        vehicleHandler.removeCallbacksAndMessages(null);
        pendingVehicleRetry = null;
        pendingVehicleExpiry = null;
    }

    @Override
    protected void onStop() {
        parkingActive = false;
        syncPublicParking();
        stopVehicleRead();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopVehicleRead();
        pleosNavigation.close();
        carReader.shutdownNow();
        parkingReader.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(STAY_MINUTES, quote.stayMinutes());
    }

    private void refresh() {
        if (detailsDialog != null) {
            detailsDialog.dismiss();
            detailsDialog = null;
        }
        quote.syncVisit(navigation.visitVersion());
        navigation.invalidateSelections();
        destination.setText(navigation.visit() == null ? "목적지 없음" : navigation.visit().name());
        syncPublicParking();
        List<Option> options = navigation.options(totalHeight.effectiveMm(vehicleState.vehicle()), vehicleState.vehicle().widthMm(), vehicleState.vehicle().drive(), quote.stayMinutes(), quote.purchaseDiscount(), priority);
        boolean emulatorAlternative = !options.isEmpty() && options.get(0).lot() == CoexParking.EMULATOR_ALTERNATIVE
                && options.get(0).fit() == Fit.MATCHES_RECORDED_RULES && !options.get(0).excluded();
        RecommendationExplanation explanation = RecommendationExplanation.create(vehicleState.vehicle(), totalHeight, vehicleState.reading(), options, priority);
        sincheonOption = options.stream().filter(option -> option.lot() == SincheonParking.LOT)
                .findFirst().orElse(null);
        setStatus(navigation.visit() == null ? navigationStatus
                : vehicleState.reading() ? "차량 정보 확인 중"
                : parkingArea == SeoulParking.Area.JAMSIL ? ""
                : options.isEmpty() ? (parkingArea == null ? "시설 정보 없음" : "")
                : totalHeight.effectiveMm(vehicleState.vehicle()) == null ? "차량 높이 확인 필요"
                : emulatorAlternative ? "시연용 대안 추천"
                : requestableCount(options) == 0 ? "입구 안내 가능한 주차장 없음" : "");
        ParkingNavigation.Receipt last = navigation.lastRequest();
        receipt.setVisibility(last == null ? View.GONE : View.VISIBLE);
        if (last != null) {
            RequestRecheck recheck = RequestRecheck.create(last, vehicleState.reading(), options);
            receipt.setText(recheck.summary());
            receipt.setOnClickListener(view -> showReceipt(recheck, explanation.vehicleDetails()));
        } else {
            receipt.setOnClickListener(null);
        }
        list.removeAllViews();
        renderPublicParking();
        if (vehicleState.reading()) return;
        for (int index = 0; index < options.size(); index++) {
            Option option = options.get(index);
            if (option.lot() == SincheonParking.LOT) continue;
            if (index == 0 && (option.requestAvailability().available() || emulatorAlternative)) {
                addRecommendation(option, explanation);
            }
            else addAlternative(option, explanation);
        }
    }

    private void syncPublicParking() {
        NavigationPort.Destination visit = navigation.visit();
        SeoulParking.Area area = parkingActive && visit != null ? visit.publicParkingArea() : null;
        if (area == parkingArea) return;
        parkingHandler.removeCallbacks(nextParkingRead);
        parkingGeneration++;
        parkingArea = area;
        parkingLoaded = false;
        publicLots = List.of();
        if (area != null) readPublicParking();
    }

    private void readPublicParking() {
        SeoulParking.Area area = parkingArea;
        int generation = parkingGeneration;
        parkingReader.execute(() -> {
            List<SeoulParking.LotStatus> lots;
            try {
                lots = SeoulParking.fetch(area);
            } catch (IOException | JSONException failure) {
                Log.w("SeoulParking", "주차 데이터 조회 실패", failure);
                lots = List.of();
            }
            List<SeoulParking.LotStatus> result = lots;
            parkingHandler.post(() -> {
                if (generation != parkingGeneration || isDestroyed()) return;
                publicLots = result;
                parkingLoaded = true;
                renderPublicParking();
                parkingHandler.postDelayed(nextParkingRead, 60_000L);
            });
        });
    }

    private void renderPublicParking() {
        parkingSection.removeAllViews();
        if (parkingArea == null) return;
        Instant now = Instant.now();
        SeoulParking.Comparison comparison = SeoulParking.compare(parkingArea, publicLots, now);
        boolean recommend = comparison.recommendNearby() && sincheonOption != null
                && sincheonOption.requestAvailability().available();
        String heading = recommend ? "목적지 주차장 혼잡 · 주변 대안 추천"
                : comparison.destinationCrowded() ? "목적지 주차장 혼잡" : "공영주차장 빈자리";
        TextView title = label(heading, 22, 700, ink);
        title.setPadding(0, dp(16), 0, dp(3));
        parkingSection.addView(title);
        if (parkingArea == SeoulParking.Area.COEX) {
            parkingSection.addView(label("코엑스 주차장 빈자리 정보 미제공", 16, 500, muted));
        }
        if (publicLots.isEmpty()) {
            parkingSection.addView(label(parkingLoaded ? "공영주차장 정보를 불러오지 못했어요" : "서울시 정보 확인 중",
                    16, 500, muted));
            return;
        }
        if (comparison.destination() != null) {
            addPublicParkingRow(comparison.destination(), comparison.destinationCrowded() ? "목적지 · 혼잡" : "목적지 주차장", now);
        }
        if (comparison.nearby() != null) {
            String category = recommend ? "추천 대안" : "주변 공영주차장";
            if (parkingArea.distanceMeters > 0) category += " · 직선 약 " + parkingArea.distanceMeters + "m";
            addPublicParkingRow(comparison.nearby(), category, now);
        }
        String source = parkingArea == SeoulParking.Area.JAMSIL
                ? "빈자리·요금 서울 열린데이터광장 · 입차 조건 서울시설공단"
                : "서울 열린데이터광장 · 입차 조건과 입구 확인 필요";
        parkingSection.addView(label(source, 14, 500, muted));
    }

    private void addPublicParkingRow(SeoulParking.LotStatus lot, String category, Instant now) {
        LinearLayout card = card(parkingSection);
        card.addView(label(category, 15, 600, muted));
        card.addView(label(lot.name(), 20, 700, ink));
        Integer spaces = lot.available(now);
        String vacancy = spaces == null ? "빈자리 확인 불가" : "빈자리 " + spaces + " / " + lot.capacity() + "면";
        String fare = lot.rate() == null ? "요금 확인 필요" : lot.rate().label();
        card.addView(label(vacancy + " · " + fare, 20, 700, ink));
        if (spaces != null) {
            card.addView(label("갱신 " + lot.updatedLabel(), 14, 500, muted));
        }
        Option option = sincheonOption;
        if (!SincheonParking.CODE.equals(lot.code()) || option == null || vehicleState.reading()) return;

        Integer vehicleHeight = totalHeight.effectiveMm(vehicleState.vehicle());
        String fit = option.fit() == Fit.MATCHES_RECORDED_RULES
                ? "내 차 " + meters(vehicleHeight) + " / 높이 제한 3.0 m · 전기차 입차 가능"
                : option.reason() + " · 높이 제한 3.0 m";
        card.addView(label(fit, 17, 600, ink));
        LinearLayout actions = row();
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.addView(actions);
        actions.addView(action("상세", false, () -> showDetails(option,
                RecommendationExplanation.create(vehicleState.vehicle(), totalHeight, false,
                        List.of(option), priority))));
        if (option.requestAvailability().available() && hasSincheonVacancy(now)
                && navigation.status() != ParkingNavigation.Status.PENDING) {
            actions.addView(action("입구 안내 ↗", true, () -> {
                if (!hasSincheonVacancy(Instant.now())) {
                    renderPublicParking();
                    return;
                }
                EntranceConfirmation.create(navigation, option, quote.stayMinutes(), quote.purchaseDiscount())
                        .ifPresent(this::confirmPreview);
            }));
        }
    }

    private boolean hasSincheonVacancy(Instant now) {
        if (parkingArea != SeoulParking.Area.JAMSIL) return false;
        for (SeoulParking.LotStatus lot : publicLots) {
            if (SincheonParking.CODE.equals(lot.code()) && lot.open24Hours()) {
                Integer spaces = lot.available(now);
                return spaces != null && spaces > 0;
            }
        }
        return false;
    }

    private void addRecommendation(Option option, RecommendationExplanation explanation) {
        LinearLayout card = card(list);
        LinearLayout top = row();
        top.addView(parkingMark());
        LinearLayout name = column();
        name.addView(label(option.lot().name(), 20, 700, ink));
        Tariff tariff = option.lot().tariff();
        if (tariff != null && !tariff.purchaseRule().isEmpty()) {
            name.addView(label(tariff.purchaseRule(), 14, 500, muted));
        }
        if (option.lot() == CoexParking.EMULATOR_ALTERNATIVE) {
            name.addView(label("시연 데이터 · 실제 입구 안내 없음", 14, 500, muted));
        }
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.setMarginStart(dp(10));
        top.addView(name, nameParams);
        card.addView(top);

        boolean compact = getResources().getConfiguration().screenWidthDp < 440 * uiScale();
        LinearLayout cost = compact ? row() : column();
        TextView amount = label(won(option.expectedWon()), 27, 700, ink);
        TextView duration = label(quote.durationText(), 15, 500, muted);
        cost.addView(amount);
        cost.addView(duration);
        if (compact) {
            duration.setPadding(dp(8), 0, 0, 0);
            cost.setPadding(dp(44), dp(6), 0, 0);
            card.addView(cost);
        } else {
            cost.setGravity(Gravity.END);
            cost.setPadding(dp(8), 0, 0, 0);
            top.addView(cost);
        }

        if (!explanation.cardHeadline().isEmpty()) {
            TextView reason = label(explanation.cardHeadline(), 14, 500, muted);
            reason.setSingleLine(true);
            card.addView(reason);
        }
        LinearLayout actions = compact ? column() : row();
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, 0);
        if (compact) stack(actions, action("상세", false, () -> showDetails(option, explanation)));
        else actions.addView(action("상세", false, () -> showDetails(option, explanation)));
        Button fare = action("주차비 계산", false, this::showQuoteSettings);
        LinearLayout.LayoutParams fareParams = new LinearLayout.LayoutParams(-2, -2);
        fareParams.setMarginStart(dp(8));
        if (compact) stack(actions, fare);
        else actions.addView(fare, fareParams);
        boolean pending = navigation.status() == ParkingNavigation.Status.PENDING;
        Button guide = action(pending ? "내비 요청 중" : "입구 안내 ↗", true, () -> EntranceConfirmation.create(navigation, option, quote.stayMinutes(), quote.purchaseDiscount())
                .ifPresent(this::confirmPreview));
        guide.setEnabled(!pending);
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(-2, -2);
        guideParams.setMarginStart(dp(8));
        if (option.requestAvailability().available()) {
            if (compact) stack(actions, guide);
            else actions.addView(guide, guideParams);
        }
        card.addView(actions);
    }

    private void addAlternative(Option option, RecommendationExplanation explanation) {
        LinearLayout card = card(list);
        card.setPadding(dp(14), dp(7), dp(14), dp(7));
        card.setMinimumHeight(dp(52));
        card.setBackground(touchBackground(cardColor, 12));
        LinearLayout line = row();
        line.setMinimumHeight(dp(38));
        line.addView(parkingMark());
        LinearLayout.LayoutParams title = new LinearLayout.LayoutParams(0, -2, 1);
        title.setMarginStart(dp(10));
        line.addView(label(option.lot().name(), 16, 600, ink), title);
        String value = option.excluded() ? "이번 방문 제외" : option.requestAvailability().available() ? won(option.expectedWon())
                : option.fit() == Fit.RESTRICTED
                    ? option.reason().contains("높이") ? "높이 초과" : "입차 제한"
                    : "확인 필요";
        TextView tail = label(value + "  ›", 14, 500, muted);
        tail.setPadding(dp(8), 0, 0, 0);
        line.addView(tail);
        card.addView(line);
        card.setFocusable(true);
        card.setOnClickListener(view -> showDetails(option, explanation));
    }

    private void showDetails(Option option, RecommendationExplanation explanation) {
        long visitVersion = navigation.visitVersion();
        Lot lot = option.lot();
        FareDetails fare = FareDetails.create(option, quote.stayMinutes(), quote.purchaseDiscount());
        String summary = lot.type()
                + "\n" + (lot.entrance() == null ? "입구 미확인" : lot.entrance().label())
                + "\n높이 제한 " + meters(lot.heightLimitMm())
                + (option.requestAvailability().available() ? "" : "\n" + option.requestAvailability().reason())
                + "\n\n" + fare.stayMinutes() + "분 예상 " + won(fare.expectedWon())
                + (java.util.Objects.equals(fare.baseWon(), fare.expectedWon())
                        ? "" : " (할인 전 " + won(fare.baseWon()) + ")")
                + "\n" + (fare.purchaseDiscount() && lot.tariff() != null && !lot.tariff().purchaseRule().isBlank()
                        ? "구매 할인 적용 가정" : fare.discountText());
        String source = VehicleReadExplanation.details(vehicleState, totalHeight)
                + "\n" + explanation.vehicleDetails()
                + "\n" + lot.widthDetails()
                + (vehicleState.vehicle().model().isEmpty() ? "" : "\n차량 " + vehicleState.vehicle().model())
                + "\n" + option.vehicleFitDetails() + "\n\n" + lot.provenance();
        LinearLayout body = column();
        body.setPadding(dp(24), dp(8), dp(24), 0);
        body.addView(label(summary, 18, 500, ink));
        LinearLayout more = column();
        more.setVisibility(View.GONE);
        more.addView(label(source, 15, 400, muted));
        stack(more, action(option.excluded() ? "제외 되돌리기" : "이번 방문에서 제외", false, () ->
                navigation.setExcluded(visitVersion, lot, !option.excluded())));
        stack(more, action("추천 기준", false, () -> show(dialog().setTitle("추천 기준")
                .setSingleChoiceItems(new String[]{"부설 우선", "요금 우선"},
                        priority == Priority.ATTACHED_FIRST ? 0 : 1, (d, which) -> {
                            priority = which == 0 ? Priority.ATTACHED_FIRST : Priority.FARE_FIRST;
                            d.dismiss();
                            refresh();
                        })
                .setNegativeButton("취소", null))));
        LinearLayout recovery = option.fit() == Fit.MATCHES_RECORDED_RULES ? more : body;
        stack(recovery, action("총높이 보완", false, this::showTotalHeight));
        stack(recovery, action("차량 정보 다시 확인", false, this::startVehicleRead));
        stack(body, action("차량·출처 정보", false, () ->
                more.setVisibility(more.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
        body.addView(more);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog.Builder details = dialog().setTitle(lot.name()).setView(scroll)
                .setNegativeButton("닫기", null)
                .setNeutralButton("주차비 계산", (d, which) -> showQuoteSettings());
        if (lot != SincheonParking.LOT || hasSincheonVacancy(Instant.now())) {
            EntranceConfirmation.create(navigation, option, quote.stayMinutes(), quote.purchaseDiscount()).ifPresent(preview ->
                    details.setPositiveButton("입구 안내", (d, which) -> confirmPreview(preview)));
        }
        detailsDialog = show(details);
    }

    private void showTotalHeight() {
        long session = totalHeight.session();
        LinearLayout body = column();
        body.setPadding(dp(24), dp(8), dp(24), 0);
        body.addView(label("루프박스·적재물 포함 직접 확인한 총높이(mm)\n앱 내부 입력 정책: 정수 1000~4000mm", 16, 400, ink));
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (totalHeight.confirmedMm() != null) input.setText(totalHeight.confirmedMm().toString());
        body.addView(input);
        AlertDialog alert = show(dialog().setTitle("총높이 보완").setView(body)
                .setNegativeButton("취소", null)
                .setNeutralButton("보완값 삭제", (d, which) -> {
                    if (totalHeight.clear(session)) refresh();
                })
                .setPositiveButton("적용", null));
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!totalHeight.isCurrent(session)) {
                alert.dismiss();
                return;
            }
            if (!totalHeight.apply(session, input.getText().toString())) {
                input.setError("정수 1000~4000mm를 입력하세요 (앱 내부 입력 정책)");
                return;
            }
            alert.dismiss();
            refresh();
        });
    }

    private void showQuoteSettings() {
        quote.syncVisit(navigation.visitVersion());
        VisitQuoteState.Token token = quote.open();
        LinearLayout body = column();
        body.setPadding(dp(24), dp(8), dp(24), 0);
        body.addView(label("주차 예상 시간(분) · 1~360분", 17, 600, ink));
        EditText minutes = new EditText(this);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setSingleLine(true);
        minutes.setTypeface(font);
        minutes.setTextSize(26 * uiScale());
        minutes.setTextColor(ink);
        minutes.setGravity(Gravity.CENTER);
        minutes.setText(Integer.toString(quote.stayMinutes()));
        LinearLayout stepper = row();
        stepper.addView(action("-30분", false, () -> step(minutes, -30)));
        stepper.addView(minutes, new LinearLayout.LayoutParams(0, -2, 1));
        stepper.addView(action("+30분", false, () -> step(minutes, 30)));
        body.addView(stepper);
        boolean[] discount = {quote.purchaseDiscount()};
        Button toggle = action("", false, () -> { });
        Runnable paint = () -> {
            toggle.setText(discount[0] ? "✓  구매 할인 적용" : "□  구매 할인 미적용");
            toggle.setSelected(discount[0]);
            toggle.setTextColor(discount[0] ? onStrong : ink);
            GradientDrawable fill = shape(discount[0] ? strong : surface, 8);
            fill.setStroke(dp(2), discount[0] ? strong : muted);
            toggle.setBackground(fill);
        };
        toggle.setOnClickListener(view -> {
            discount[0] = !discount[0];
            paint.run();
        });
        paint.run();
        stack(body, toggle);
        AlertDialog alert = show(dialog().setTitle("예상 주차비 계산")
                .setMessage("구매 할인은 이번 방문에만 적용해 계산합니다.")
                .setView(body).setNegativeButton("취소", null)
                .setPositiveButton("적용", null));
        alert.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            quote.syncVisit(navigation.visitVersion());
            if (!quote.apply(token, minutes.getText().toString(), discount[0])) {
                minutes.setError("정수 1~360분을 입력하세요. 방문이 바뀌었다면 창을 다시 여세요.");
                return;
            }
            alert.dismiss();
            refresh();
        });
    }

    private void confirmPreview(EntranceConfirmation preview) {
        boolean discounted = preview.purchaseDiscount() && preview.lot().tariff() != null
                && !preview.lot().tariff().purchaseRule().isBlank();
        detailsDialog = show(dialog().setTitle("이 입구로 안내할까요?")
                .setMessage(preview.entrance().label() + "\n" + preview.stayMinutes()
                        + "분 예상 " + won(preview.expectedWon())
                        + (discounted ? " · 할인 적용 가정" : ""))
                .setNegativeButton("취소", null)
                .setPositiveButton("내비 안내", (d, which) -> {
                    if (preview.lot() == SincheonParking.LOT && !hasSincheonVacancy(Instant.now())) {
                        renderPublicParking();
                        setStatus("빈자리 상태가 바뀌었어요. 다시 확인해 주세요.");
                        return;
                    }
                    if (navigation.confirm(preview.selection()) == ParkingNavigation.Status.STALE) {
                        setStatus("조건이 바뀌었습니다. 입구를 다시 선택하세요.");
                    }
                }));
    }

    private void showReceipt(RequestRecheck recheck, String vehicleDetails) {
        ParkingNavigation.Receipt last = recheck.receipt();
        Entrance entrance = last.request().entrance();
        String coordinates = String.format(Locale.KOREA, "%.5f, %.5f", entrance.latitude(), entrance.longitude());
        detailsDialog = show(dialog().setTitle("마지막 요청")
                .setMessage(entrance.label() + "\n" + last.lot().name() + " · " + last.request().visit()
                        + "\n당시 요청 결과: " + recheck.outcomeText() + "\n\n" + recheck.currentText())
                .setNeutralButton("좌표·출처", (d, which) -> show(dialog().setTitle("좌표·출처")
                        .setMessage(coordinates + "\n\n현재 차량값·출처\n" + vehicleDetails
                                + "\n시설 출처\n" + last.lot().provenance())
                        .setPositiveButton("닫기", null)))
                .setPositiveButton("닫기", null));
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, night
                ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    private AlertDialog show(AlertDialog.Builder builder) {
        AlertDialog alert = builder.show();
        if (alert.getWindow() != null) {
            alert.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(600)), -2);
        }
        float scale = uiScale();
        TextView title = alert.findViewById(getResources().getIdentifier("alertTitle", "id", "android"));
        if (title != null) {
            title.setTypeface(Typeface.create(font, 700, false));
            title.setTextSize(22 * scale);
            title.setSingleLine(false);
        }
        TextView message = alert.findViewById(android.R.id.message);
        if (message != null) {
            message.setTypeface(font);
            message.setTextSize(18 * scale);
            message.setTextColor(ink);
        }
        for (int which : new int[]{-1, -2, -3}) {
            Button button = alert.getButton(which);
            if (button == null) continue;
            button.setTypeface(Typeface.create(font, 650, false));
            button.setAllCaps(false);
            button.setTextSize(17 * scale);
            button.setTextColor(which == -1 ? onStrong : ink);
            button.setMinHeight(dp(56));
            button.setMinimumHeight(dp(56));
            button.setPadding(dp(16), 0, dp(16), 0);
            button.setBackground(touchBackground(which == -1 ? strong : surface, 8));
        }
        return alert;
    }

    private void setStatus(String message) {
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        status.setText(message);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(cardColor, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(9);
        parent.addView(card, params);
        return card;
    }

    private TextView parkingMark() {
        TextView mark = label("P", 17, 700, muted);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(surface, 8));
        mark.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return mark;
    }

    private Button action(String caption, boolean primary, Runnable click) {
        Button button = new Button(this);
        button.setText(caption);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create(font, 650, false));
        button.setTextSize(16 * uiScale());
        button.setTextColor(primary ? onStrong : ink);
        button.setMinHeight(dp(48));
        button.setMinimumWidth(dp(56));
        button.setPadding(dp(13), 0, dp(13), 0);
        button.setStateListAnimator(null);
        button.setBackground(touchBackground(primary ? strong : surface, 8));
        button.setOnClickListener(view -> click.run());
        return button;
    }

    private RippleDrawable touchBackground(int fill, int corner) {
        return new RippleDrawable(ColorStateList.valueOf(0x25000000), shape(fill, corner), null);
    }

    private GradientDrawable shape(int fill, int corner) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(corner));
        return shape;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView label(String text, int size, int weight, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size * uiScale());
        view.setTypeface(Typeface.create(font, weight, false));
        view.setTextColor(color);
        return view;
    }

    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density * uiScale());
    }

    private float uiScale() {
        Configuration config = getResources().getConfiguration();
        return Math.max(1f, Math.min(2.5f, Math.min(config.screenWidthDp, config.screenHeightDp) / 720f));
    }

    private void stack(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(child, params);
    }

    private void step(EditText minutes, int delta) {
        int current;
        try {
            current = Integer.parseInt(minutes.getText().toString());
        } catch (NumberFormatException invalid) {
            current = quote.stayMinutes();
        }
        minutes.setText(Integer.toString(Math.max(1, Math.min(360, current + delta))));
    }

    private static String meters(Integer mm) {
        return mm == null ? "미확인" : String.format(Locale.KOREA, "%.2f m", mm / 1000.0);
    }

    private static String won(Integer amount) {
        return amount == null ? "요금 미확인" : NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원";
    }
}
