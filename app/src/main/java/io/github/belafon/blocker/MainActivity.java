package io.github.belafon.blocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private DevicePolicyManager dpm;
    private TextView status;
    private TextView lockdownWarning;
    private Button filterButton;
    private EditText domainInput;
    private TextView noDomains;
    private LinearLayout domainList;
    private Button toggleListButton;
    private TextView passwordHint;
    private Button setPasswordButton;
    private Button removePasswordButton;
    private Button adminButton;
    private Button guardButton;
    private Set<String> shownDomains;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Screen.padForSystemBars(findViewById(R.id.root));
        Prefs.addDefaultDomainsOnce(this);
        dpm = getSystemService(DevicePolicyManager.class);

        status = findViewById(R.id.status);
        lockdownWarning = findViewById(R.id.lockdown_warning);
        lockdownWarning.setOnClickListener(v -> Setup.openVpnSettings(this));
        filterButton = findViewById(R.id.btn_filter);
        domainInput = findViewById(R.id.input_domain);
        noDomains = findViewById(R.id.no_domains);
        domainList = findViewById(R.id.domain_list);
        toggleListButton = findViewById(R.id.btn_toggle_list);
        toggleListButton.setOnClickListener(v -> {
            domainList.setVisibility(domainList.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            updateToggleListButton();
        });
        passwordHint = findViewById(R.id.password_hint);
        setPasswordButton = findViewById(R.id.btn_set_password);
        removePasswordButton = findViewById(R.id.btn_remove_password);
        adminButton = findViewById(R.id.btn_admin);
        guardButton = findViewById(R.id.btn_guard);

        filterButton.setOnClickListener(v -> toggleFilter());
        findViewById(R.id.btn_add).setOnClickListener(v -> addDomain());
        domainInput.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            addDomain();
            return true;
        });
        setPasswordButton.setOnClickListener(v -> requirePassword(this::showNewPasswordDialog));
        findViewById(R.id.btn_random_password).setOnClickListener(v -> confirmRandomPassword());
        removePasswordButton.setOnClickListener(v -> requirePassword(() -> {
            Prefs.clearPassword(this);
            toast(R.string.password_removed);
            refresh();
        }));
        adminButton.setOnClickListener(v -> toggleAdmin());
        guardButton.setOnClickListener(v -> Setup.openAccessibilitySettings(this));
        findViewById(R.id.btn_help).setOnClickListener(v -> startActivity(new Intent(this, OnboardingActivity.class)));
        findViewById(R.id.btn_show_code).setOnClickListener(
                v -> startActivity(new Intent(this, EmergencyCodeActivity.class)));
        findViewById(R.id.btn_enter_code).setOnClickListener(
                v -> startActivity(new Intent(this, EmergencyUnlockActivity.class)));

        if (savedInstanceState == null && !Prefs.isOnboardingDone(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }
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

    private void refresh() {
        boolean running = BlockerVpnService.isRunning();
        boolean locked = Prefs.hasPassword(this);
        boolean admin = dpm.isAdminActive(AdminReceiver.component(this));
        boolean guard = GuardService.isEnabled(this);
        Set<String> domains = Prefs.getDomains(this);

        String filterLine = getString(running ? R.string.status_filter_on
                : Prefs.isFilterEnabled(this) ? R.string.status_filter_broken : R.string.status_filter_off);
        lockdownWarning.setVisibility(running && BlockerVpnService.isLockdown() ? View.VISIBLE : View.GONE);
        status.setText(filterLine + "\n"
                + getString(R.string.status_count, domains.size()) + "\n"
                + getString(locked ? R.string.status_locked : R.string.status_unlocked) + "\n"
                + getString(admin ? R.string.status_admin_on : R.string.status_admin_off) + "\n"
                + getString(guard ? R.string.status_guard_on : R.string.status_guard_off));

        filterButton.setText(running ? R.string.stop_filter : R.string.start_filter);
        passwordHint.setText(locked ? R.string.password_hint_locked : R.string.password_hint_unlocked);
        setPasswordButton.setText(locked ? R.string.change_password : R.string.set_password);
        removePasswordButton.setEnabled(locked);
        adminButton.setText(admin ? R.string.disable_admin : R.string.enable_admin);
        guardButton.setText(guard ? R.string.guard_settings : R.string.enable_guard);
        // While locked, the guard would close the accessibility page anyway.
        guardButton.setEnabled(!(guard && locked));

        if (!domains.equals(shownDomains)) showDomains(domains);
    }

    // --- Filter ---

    private void toggleFilter() {
        if (BlockerVpnService.isRunning()) {
            requirePassword(() -> {
                BlockerVpnService.stop(this);
                handler.postDelayed(this::refresh, 300);
            });
            return;
        }
        Setup.startFilter(this);
        handler.postDelayed(this::refresh, 300);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Setup.onActivityResult(this, requestCode, resultCode);
        handler.postDelayed(this::refresh, 300);
    }

    // --- Domains ---

    private void addDomain() {
        String domain = Prefs.normalizeDomain(domainInput.getText().toString());
        if (domain == null) {
            toast(R.string.invalid_domain);
            return;
        }
        Prefs.addDomain(this, domain);
        domainInput.setText("");
        refresh();
    }

    private void showDomains(Set<String> domains) {
        shownDomains = domains;
        domainList.removeAllViews();
        noDomains.setVisibility(domains.isEmpty() ? View.VISIBLE : View.GONE);
        updateToggleListButton();
        for (String domain : domains) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(this);
            name.setText(domain);
            name.setTextSize(16);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            Button remove = new Button(this, null, android.R.attr.borderlessButtonStyle);
            remove.setText(R.string.remove);
            remove.setOnClickListener(v -> requirePassword(() -> {
                Prefs.removeDomain(this, domain);
                refresh();
            }));
            row.addView(remove);
            domainList.addView(row);
        }
    }

    /** The list is long (the defaults alone are ~80 sites), so it starts collapsed. */
    private void updateToggleListButton() {
        int count = shownDomains == null ? 0 : shownDomains.size();
        toggleListButton.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        toggleListButton.setText(getString(domainList.getVisibility() == View.VISIBLE
                ? R.string.hide_list : R.string.show_list, count));
    }

    // --- Password ---

    /** Runs the action right away when there's no password, otherwise after asking for it. */
    private void requirePassword(Runnable action) {
        if (!Prefs.hasPassword(this)) {
            action.run();
            return;
        }
        EditText input = passwordField(R.string.password);
        new AlertDialog.Builder(this)
                .setTitle(R.string.password_title)
                .setView(padded(input))
                .setPositiveButton(R.string.ok, (d, w) -> {
                    if (Prefs.checkPassword(this, input.getText().toString())) {
                        action.run();
                    } else {
                        toast(R.string.wrong_password);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showNewPasswordDialog() {
        EditText first = passwordField(R.string.password);
        EditText second = passwordField(R.string.password_again);
        LinearLayout fields = padded(first);
        fields.addView(second);
        new AlertDialog.Builder(this)
                .setTitle(R.string.new_password_title)
                .setView(fields)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String password = first.getText().toString();
                    if (password.isEmpty()) {
                        toast(R.string.password_empty);
                    } else if (!password.equals(second.getText().toString())) {
                        toast(R.string.passwords_differ);
                    } else {
                        Prefs.setPassword(this, password);
                        toast(R.string.password_set);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmRandomPassword() {
        // Making the lock stricter never needs the current password.
        new AlertDialog.Builder(this)
                .setTitle(R.string.random_title)
                .setMessage(R.string.random_message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    Prefs.setRandomPassword(this);
                    toast(R.string.random_done);
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText passwordField(int hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return field;
    }

    private LinearLayout padded(View view) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        layout.addView(view);
        return layout;
    }

    // --- Protection ---

    private void toggleAdmin() {
        if (dpm.isAdminActive(AdminReceiver.component(this))) {
            requirePassword(() -> {
                dpm.removeActiveAdmin(AdminReceiver.component(this));
                handler.postDelayed(this::refresh, 300);
            });
            return;
        }
        Setup.requestAdmin(this);
    }

    private void toast(int text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
