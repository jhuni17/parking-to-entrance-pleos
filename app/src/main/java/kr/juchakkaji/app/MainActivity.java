package kr.juchakkaji.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

import static kr.juchakkaji.app.ParkingPlan.*;

public final class MainActivity extends Activity {
    private boolean roofBox;
    private boolean electric;
    private boolean purchaseDiscount;
    private int stayMinutes = 120;
    private TextView result;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(24), dp(16), dp(24), dp(24));
        scroll.addView(content);
        setContentView(scroll);

        content.addView(label("주차까지", 32));
        content.addView(label("시연 모드 · 정차 후 조작", 16));
        content.addView(label("방문 목적지  " + SampleTrip.VISIT + " (시연 입력)", 22));
        content.addView(toggle("루프박스 포함 2.25 m (기본 1.85 m)", value -> roofBox = value));
        content.addView(toggle("전기차 시연 프로필", value -> electric = value));
        CheckBox purchase = toggle("대상 매장 10만 원 영수증 할인 가정", value -> purchaseDiscount = value);
        purchase.setChecked(true);
        content.addView(purchase);
        Button duration = new Button(this);
        duration.setText("이용 시간 2시간 · 1시간으로 변경");
        duration.setOnClickListener(view -> {
            stayMinutes = stayMinutes == 120 ? 60 : 120;
            duration.setText(stayMinutes == 120
                    ? "이용 시간 2시간 · 1시간으로 변경" : "이용 시간 1시간 · 2시간으로 변경");
            refresh();
        });
        content.addView(duration);

        result = label("", 18);
        list = column();
        content.addView(result);
        content.addView(list);
        refresh();
    }

    private void refresh() {
        if (list == null) return;
        result.setText("차량 높이 " + (roofBox ? "2.25 m" : "1.85 m") + " · 등록 시연값");
        list.removeAllViews();
        for (Option option : compare(SampleTrip.LOTS, roofBox ? 2250 : 1850,
                electric, stayMinutes, purchaseDiscount)) {
            LinearLayout card = column();
            card.setBackgroundColor(Color.rgb(32, 40, 54));
            card.setPadding(dp(16), dp(12), dp(16), dp(12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(12);
            list.addView(card, params);
            Lot lot = option.lot();
            card.addView(label(lot.name() + "  ·  " + lot.type(), 22));
            String limit = lot.heightLimitMm() == null ? "미확인"
                    : String.format(Locale.KOREA, "%.2f m", lot.heightLimitMm() / 1000.0);
            card.addView(label(option.reason() + " · 제한 높이 " + limit, 18));
            card.addView(label("기본 " + won(option.baseWon()) + " / 조건 적용 예상 "
                    + won(option.expectedWon()), 18));
            if (lot.tariff() != null && !lot.tariff().purchaseRule().isEmpty()) {
                card.addView(label(lot.tariff().purchaseRule(), 15));
            }
            card.addView(label(lot.provenance(), 15));
            routeRequest(SampleTrip.VISIT, option).ifPresent(route -> {
                Button preview = new Button(this);
                preview.setText("입구 경로 요청 미리보기");
                preview.setOnClickListener(view -> confirmPreview(route));
                card.addView(preview);
            });
        }
    }

    private void confirmPreview(RouteRequest route) {
        Entrance entrance = route.entrance();
        new AlertDialog.Builder(this)
                .setTitle("시연 경로 요청")
                .setMessage("방문 목적지  " + route.visit() + "\n차량 도착점  " + entrance.label()
                        + "\n좌표  " + entrance.latitude() + ", " + entrance.longitude()
                        + "\n기본 내비 연동 전 단계입니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("요청 확인", (dialog, which) ->
                        result.setText("시연 요청 확인 · " + entrance.label() + "\n방문 목적지  " + route.visit()))
                .show();
    }

    private CheckBox toggle(String title, java.util.function.Consumer<Boolean> change) {
        CheckBox box = new CheckBox(this);
        box.setMinHeight(dp(48));
        box.setText(title);
        box.setOnCheckedChangeListener((button, checked) -> {
            change.accept(checked);
            refresh();
        });
        return box;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density);
    }

    private String won(Integer amount) {
        return amount == null ? "미확인" : NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원";
    }
}
