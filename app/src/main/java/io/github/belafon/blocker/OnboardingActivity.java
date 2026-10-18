package io.github.belafon.blocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Help cards that walk through the setup, one step per card. Swipe or use the buttons to move
 * between them. Shown on first start and from the Help button.
 */
public class OnboardingActivity extends Activity {
    private static final int SWIPE_MIN_VELOCITY = 600;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            bindStatus();
            handler.postDelayed(this, 1000);
        }
    };

    private final List<Step> steps = new ArrayList<>();
    private GestureDetector swipes;
    private int current;

    private View card;
    private TextView counter;
    private TextView title;
    private TextView body;
    private TextView status;
    private Button action1;
    private Button action2;
    private Button back;
    private Button next;
    private LinearLayout dots;

    /** One card: text, up to two buttons and an optional "done" check. */
    private static final class Step {
        final int title;
        final CharSequence body;
        int action1Label;
        Runnable action1;
        int action2Label;
        Runnable action2;
        BooleanSupplier done;
        int doneText;

        Step(int title, CharSequence body) {
            this.title = title;
            this.body = body;
        }

        Step action(int label, Runnable action) {
            if (action1 == null) {
                action1Label = label;
                action1 = action;
            } else {
                action2Label = label;
                action2 = action;
            }
            return this;
        }

        Step done(BooleanSupplier done, int text) {
            this.done = done;
            this.doneText = text;
            return this;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        Screen.padForSystemBars(findViewById(R.id.root));

        card = findViewById(R.id.card);
        counter = findViewById(R.id.step_counter);
        title = findViewById(R.id.card_title);
        body = findViewById(R.id.card_body);
        status = findViewById(R.id.card_status);
        action1 = findViewById(R.id.card_action1);
        action2 = findViewById(R.id.card_action2);
        back = findViewById(R.id.btn_back);
        next = findViewById(R.id.btn_next);
        dots = findViewById(R.id.dots);

        buildSteps();
        if (savedInstanceState != null) current = savedInstanceState.getInt("current");

        back.setOnClickListener(v -> go(current - 1));
        next.setOnClickListener(v -> {
            if (current == steps.size() - 1) finishOnboarding();
            else go(current + 1);
        });
        findViewById(R.id.btn_skip).setOnClickListener(v -> finishOnboarding());
        action1.setOnClickListener(v -> steps.get(current).action1.run());
        action2.setOnClickListener(v -> steps.get(current).action2.run());

        swipes = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (Math.abs(vx) < SWIPE_MIN_VELOCITY || Math.abs(vx) < Math.abs(vy) * 1.5f) return false;
                go(vx < 0 ? current + 1 : current - 1);
                return true;
            }
        });
        bind();
    }

    /** Watches every touch for horizontal swipes, without taking it away from the views. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        swipes.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current", current);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshLoop);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Setup.onActivityResult(this, requestCode, resultCode);
    }

    private void buildSteps() {
        steps.add(new Step(R.string.ob_welcome_title, getText(R.string.ob_welcome_body)));

        steps.add(new Step(R.string.ob_filter_title, getText(R.string.ob_filter_body))
                .action(R.string.start_filter, () -> Setup.startFilter(this))
                .done(BlockerVpnService::isRunning, R.string.ob_filter_done));

        steps.add(new Step(R.string.ob_notification_title, getText(R.string.ob_notification_body))
                .action(R.string.ob_notification_action, () -> Setup.openNotificationSettings(this))
                .done(() -> !Setup.notificationsEnabled(this), R.string.ob_notification_done));

        boolean xiaomi = Setup.isXiaomi();
        Step background = new Step(R.string.ob_background_title, join(R.string.ob_background_body,
                xiaomi ? R.string.ob_background_xiaomi : R.string.ob_background_other))
                .action(R.string.ob_background_action, () -> Setup.openBatterySettings(this))
                .done(() -> Setup.ignoresBatteryOptimizations(this), R.string.ob_background_done);
        if (xiaomi) background.action(R.string.ob_autostart_action, () -> Setup.openXiaomiAutostart(this));
        steps.add(background);

        Step alwaysOn = new Step(R.string.ob_always_on_title, getText(R.string.ob_always_on_body))
                .action(R.string.ob_always_on_action, () -> Setup.openVpnSettings(this));
        if (Build.VERSION.SDK_INT >= 29) alwaysOn.done(BlockerVpnService::isAlwaysOnVpn, R.string.ob_always_on_done);
        steps.add(alwaysOn);

        steps.add(new Step(R.string.ob_admin_title, getText(R.string.ob_admin_body))
                .action(R.string.enable_admin, () -> Setup.requestAdmin(this))
                .done(() -> Setup.isAdminActive(this), R.string.ob_admin_done));

        boolean restricted = Setup.needsRestrictedSettings(this);
        Step guard = new Step(R.string.ob_guard_title, restricted
                ? join(R.string.ob_guard_body, R.string.ob_guard_restricted)
                : getText(R.string.ob_guard_body))
                .action(R.string.ob_guard_action, () -> Setup.openAccessibilitySettings(this))
                .done(() -> GuardService.isEnabled(this), R.string.ob_guard_done);
        if (restricted) guard.action(R.string.ob_app_info_action, () -> Setup.openAppInfo(this));
        steps.add(guard);

        steps.add(new Step(R.string.ob_password_title, getText(R.string.ob_password_body)));
    }

    private CharSequence join(int first, int second) {
        return getText(first) + "\n\n" + getText(second);
    }

    private void go(int index) {
        if (index < 0 || index >= steps.size() || index == current) return;
        int direction = index > current ? 1 : -1;
        float width = card.getWidth();
        current = index;
        card.animate().translationX(-direction * width).alpha(0f).setDuration(150).withEndAction(() -> {
            bind();
            card.setTranslationX(direction * width);
            card.animate().translationX(0f).alpha(1f).setDuration(150).start();
        }).start();
    }

    private void bind() {
        Step step = steps.get(current);
        card.scrollTo(0, 0);
        counter.setText(getString(R.string.ob_counter, current + 1, steps.size()));
        title.setText(step.title);
        body.setText(step.body);
        back.setVisibility(current == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(current == steps.size() - 1 ? R.string.ob_finish : R.string.onboarding_next);
        bindDots();
        bindStatus();
    }

    private static void bindAction(Button button, int label, Runnable action) {
        button.setVisibility(action == null ? View.GONE : View.VISIBLE);
        if (action != null) button.setText(label);
    }

    private void bindStatus() {
        Step step = steps.get(current);
        boolean done = step.done != null && step.done.getAsBoolean();
        status.setVisibility(done ? View.VISIBLE : View.GONE);
        if (done) status.setText(step.doneText);
        // A finished step needs no buttons.
        bindAction(action1, step.action1Label, done ? null : step.action1);
        bindAction(action2, step.action2Label, done ? null : step.action2);
    }

    private void bindDots() {
        dots.removeAllViews();
        int size = (int) (8 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < steps.size(); i++) {
            View dot = new View(this);
            dot.setBackgroundResource(i == current ? R.drawable.dot_active : R.drawable.dot_inactive);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(size / 2, 0, size / 2, 0);
            dots.addView(dot, params);
        }
    }

    private void finishOnboarding() {
        Prefs.setOnboardingDone(this);
        finish();
    }
}
