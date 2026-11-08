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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static kr.juchakkaji.app.ParkingPlan.*;

public final class MainActivity extends Activity {
    private static final String STAY_MINUTES = "demoProfile.stayMinutes";
    private static final String PURCHASE_DISCOUNT = "demoProfile.purchaseDiscount";

    private final ExecutorService carReader = Executors.newSingleThreadExecutor();
    private ParkingNavigation navigation;
    private VehicleProfile vehicle = VehicleProfile.UNKNOWN;
    private int readGeneration;
    private final VehicleHeight totalHeight = new VehicleHeight();
    private final Handler vehicleHandler = new Handler(Looper.getMainLooper());
    private final VehicleReadResult.Session vehicleSession = new VehicleReadResult.Session();
    private Runnable pendingVehicleRetry;
    private boolean readingVehicle;
    private int stayMinutes = 120;
    private boolean purchaseDiscount;

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
    private AlertDialog detailsDialog;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            stayMinutes = saved.getInt(STAY_MINUTES, stayMinutes);
            purchaseDiscount = saved.getBoolean(PURCHASE_DISCOUNT, purchaseDiscount);
        }
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
        TextView mode = label("시연 목적지", 13, 500, muted);
        mode.setPadding(dp(8), 0, 0, 0);
        header.addView(mode);
        content.addView(header);
        status = label("", 15, 500, muted);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        list = column();
        content.addView(list);
        receipt = label("", 14, 500, muted);
        receipt.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(receipt);

        SampleTrip.SimulatedNavigation adapter = new SampleTrip.SimulatedNavigation();
        navigation = new ParkingNavigation(adapter, this::refresh);
        adapter.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        totalHeight.start();
        int generation = ++readGeneration;
        readingVehicle = true;
        vehicle = VehicleProfile.UNKNOWN;
        refresh();
        vehicleHandler.removeCallbacksAndMessages(null);
        pendingVehicleRetry = null;
        vehicleSession.start(generation);
        readVehicle(generation, 0);
    }

    private void readVehicle(int generation, int attempt) {
        if (!vehicleSession.isCurrent(generation) || isDestroyed()) return;
        var context = getApplicationContext();
        carReader.execute(() -> {
            VehicleReadResult result = AndroidVehicleInfo.read(context);
            vehicleHandler.post(() -> {
                if (generation != readGeneration || isDestroyed()) return;
                VehicleReadResult.Session.Action action = vehicleSession.accept(generation, attempt, result);
                if (action == VehicleReadResult.Session.Action.IGNORE) return;
                if (action == VehicleReadResult.Session.Action.RETRY) {
                    pendingVehicleRetry = () -> {
                        pendingVehicleRetry = null;
                        readVehicle(generation, 1);
                    };
                    vehicleHandler.postDelayed(pendingVehicleRetry, VehicleReadResult.Session.RETRY_DELAY_MS);
                    return;
                }
                vehicle = result.profile();
                readingVehicle = false;
                refresh();
            });
        });
    }

    private void stopVehicleRead() {
        totalHeight.stop();
        readGeneration++;
        vehicleSession.stop();
        vehicleHandler.removeCallbacksAndMessages(null);
        pendingVehicleRetry = null;
        readingVehicle = false;
    }

    @Override
    protected void onStop() {
        stopVehicleRead();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopVehicleRead();
        carReader.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(STAY_MINUTES, stayMinutes);
        state.putBoolean(PURCHASE_DISCOUNT, purchaseDiscount);
    }

    private void refresh() {
        if (detailsDialog != null) {
            detailsDialog.dismiss();
            detailsDialog = null;
        }
        navigation.invalidateSelections();
        destination.setText(navigation.visit() == null ? "목적지 없음" : navigation.visit().name());
        List<Option> options = navigation.options(totalHeight.effectiveMm(vehicle), vehicle.drive(), stayMinutes, purchaseDiscount);
        RecommendationExplanation explanation = RecommendationExplanation.create(vehicle, totalHeight, readingVehicle, options);
        setStatus(readingVehicle ? "차량 정보 확인 중"
                : options.isEmpty() ? "시설 정보 없음"
                : totalHeight.effectiveMm(vehicle) == null ? "차량 높이 확인 필요"
                : requestableCount(options) == 0 ? "입구 안내 가능한 주차장 없음" : "");
        ParkingNavigation.Receipt last = navigation.lastRequest();
        receipt.setVisibility(last == null ? View.GONE : View.VISIBLE);
        if (last != null) {
            receipt.setText(last.request().entrance().label() + " · " + routeStatus(last.outcome()));
            receipt.setOnClickListener(view -> showReceipt(last));
        } else {
            receipt.setOnClickListener(null);
        }
        list.removeAllViews();
        if (readingVehicle) return;
        for (int index = 0; index < options.size(); index++) {
            Option option = options.get(index);
            if (index == 0 && option.requestAvailability().available()) addRecommendation(option, explanation);
            else addAlternative(option, explanation);
        }
    }

    private void addRecommendation(Option option, RecommendationExplanation explanation) {
        LinearLayout card = card();
        LinearLayout top = row();
        top.addView(parkingMark());
        LinearLayout name = column();
        name.addView(label(option.lot().name(), 20, 700, ink));
        Tariff tariff = option.lot().tariff();
        if (tariff != null && !tariff.purchaseRule().isEmpty()) {
            name.addView(label(tariff.purchaseRule(), 14, 500, muted));
        }
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.setMarginStart(dp(10));
        top.addView(name, nameParams);
        card.addView(top);

        boolean compact = getResources().getConfiguration().screenWidthDp < 440;
        LinearLayout cost = compact ? row() : column();
        TextView amount = label(won(option.expectedWon()), 27, 700, ink);
        TextView duration = label((stayMinutes / 60) + "시간 예상", 13, 500, muted);
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

        if (!explanation.headline().isEmpty()) {
            TextView reason = label(explanation.headline(), 14, 500, muted);
            reason.setSingleLine(true);
            card.addView(reason);
        }
        LinearLayout actions = row();
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, 0);
        actions.addView(action("상세", false, () -> showDetails(option, explanation)));
        Button guide = action("입구 안내 ↗", true, () -> navigation.select(option).ifPresent(this::confirmPreview));
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(-2, -2);
        guideParams.setMarginStart(dp(8));
        actions.addView(guide, guideParams);
        card.addView(actions);
    }

    private void addAlternative(Option option, RecommendationExplanation explanation) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(7), dp(14), dp(7));
        card.setMinimumHeight(dp(52));
        card.setBackground(touchBackground(cardColor, 12));
        LinearLayout line = row();
        line.setMinimumHeight(dp(38));
        line.addView(parkingMark());
        LinearLayout.LayoutParams title = new LinearLayout.LayoutParams(0, -2, 1);
        title.setMarginStart(dp(10));
        line.addView(label(option.lot().name(), 16, 600, ink), title);
        String value = option.requestAvailability().available() ? won(option.expectedWon())
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
        Lot lot = option.lot();
        String text = lot.type() + "\n입구 높이 제한 " + meters(lot.heightLimitMm())
                + " (" + (lot.heightLimitMm() == null ? "미확인" : lot.heightLimitMm() + "mm") + ")"
                + "\n" + explanation.vehicleDetails()
                + (vehicle.widthMm() == null ? "" : "\n차량 너비 " + meters(vehicle.widthMm()))
                + (vehicle.model().isEmpty() ? "" : "\n차량 " + vehicle.model())
                + "\n" + option.requestAvailability().reason() + "\n\n" + lot.provenance();
        if (lot.tariff() != null && !lot.tariff().purchaseRule().isEmpty()) {
            text += "\n" + lot.tariff().purchaseRule();
        }
        LinearLayout body = column();
        body.setPadding(dp(24), dp(8), dp(24), 0);
        body.addView(label(text, 16, 400, ink));
        body.addView(action("총높이 보완", false, this::showTotalHeight));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog.Builder details = dialog().setTitle(lot.name()).setView(scroll)
                .setNegativeButton("닫기", null)
                .setNeutralButton("요금 조건", (d, which) -> showQuoteSettings());
        navigation.select(option).ifPresent(selection ->
                details.setPositiveButton("입구 안내", (d, which) -> confirmPreview(selection)));
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
        LinearLayout body = column();
        body.setPadding(dp(24), dp(8), dp(24), 0);
        CheckBox twoHours = new CheckBox(this);
        twoHours.setText("2시간 기준 (해제 시 1시간)");
        twoHours.setTypeface(font);
        twoHours.setChecked(stayMinutes == 120);
        body.addView(twoHours);
        CheckBox discount = new CheckBox(this);
        discount.setText("구매 할인 조건 충족 가정");
        discount.setTypeface(font);
        discount.setChecked(purchaseDiscount);
        body.addView(discount);
        show(dialog().setTitle("요금 조건").setView(body).setNegativeButton("취소", null)
                .setPositiveButton("적용", (d, which) -> {
                    stayMinutes = twoHours.isChecked() ? 120 : 60;
                    purchaseDiscount = discount.isChecked();
                    refresh();
                }));
    }

    private void confirmPreview(ParkingNavigation.Selection selection) {
        show(dialog().setTitle("이 입구로 안내할까요?")
                .setMessage(selection.request().entrance().label() + "\n시연 경로 요청")
                .setNegativeButton("취소", null)
                .setPositiveButton("시연 요청", (d, which) -> {
                    if (navigation.confirm(selection) == ParkingNavigation.Status.STALE) {
                        setStatus("조건이 바뀌었습니다. 입구를 다시 선택하세요.");
                    }
                }));
    }

    private void showReceipt(ParkingNavigation.Receipt last) {
        Entrance entrance = last.request().entrance();
        String coordinates = String.format(Locale.KOREA, "%.5f, %.5f", entrance.latitude(), entrance.longitude());
        show(dialog().setTitle("마지막 요청")
                .setMessage(last.request().visit() + "\n" + entrance.label() + "\n" + coordinates
                        + "\n" + routeStatus(last.outcome()))
                .setPositiveButton("닫기", null));
    }

    private String routeStatus(ParkingNavigation.Status outcome) {
        return switch (outcome) {
            case SIMULATED -> "시연 요청 완료";
            case ACCEPTED -> "내비 요청 수락";
            case REJECTED -> "입구 경로 요청 실패";
            case STALE -> "만료된 선택";
            case IDLE -> "입구 요청 전";
        };
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, night
                ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    private AlertDialog show(AlertDialog.Builder builder) {
        AlertDialog alert = builder.show();
        TextView message = alert.findViewById(android.R.id.message);
        if (message != null) message.setTypeface(font);
        for (int which : new int[]{-1, -2, -3}) {
            Button button = alert.getButton(which);
            if (button != null) button.setTypeface(font);
        }
        return alert;
    }

    private void setStatus(String message) {
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        status.setText(message);
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(shape(cardColor, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(9);
        list.addView(card, params);
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
        button.setTextSize(15);
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
        view.setTextSize(size);
        view.setTypeface(Typeface.create(font, weight, false));
        view.setTextColor(color);
        return view;
    }

    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density);
    }

    private static String meters(Integer mm) {
        return mm == null ? "미확인" : String.format(Locale.KOREA, "%.2f m", mm / 1000.0);
    }

    private static String won(Integer amount) {
        return amount == null ? "요금 미확인" : NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원";
    }
}
