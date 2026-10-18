package io.github.belafon.blocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** Unlocks everything when the emergency code is typed in by hand. */
public class EmergencyUnlockActivity extends Activity {
    /** More than this many characters arriving at once means pasting, not typing. */
    private static final int MAX_CHARS_PER_INPUT = 2;

    private EditText input;
    private TextView progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_emergency_unlock);
        Screen.padForSystemBars(findViewById(R.id.root));

        input = findViewById(R.id.code_input);
        progress = findViewById(R.id.code_progress);

        // "Visible password" turns off suggestions, and keyboards don't offer the clipboard there.
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new InputFilter[] {new TypingOnlyFilter()});
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateProgress();
            }
        });
        updateProgress();

        findViewById(R.id.btn_unlock).setOnClickListener(v -> tryUnlock());
    }

    private void updateProgress() {
        int typed = EmergencyCode.normalize(input.getText().toString()).length();
        progress.setText(getString(R.string.code_progress, typed, EmergencyCode.length()));
    }

    private void tryUnlock() {
        if (!EmergencyCode.matches(input.getText().toString())) {
            Toast.makeText(this, R.string.code_wrong, Toast.LENGTH_LONG).show();
            return;
        }
        Prefs.clearPassword(this);
        BlockerVpnService.stop(this);
        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        if (dpm.isAdminActive(AdminReceiver.component(this))) {
            dpm.removeActiveAdmin(AdminReceiver.component(this));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.unlocked_title)
                .setMessage(R.string.unlocked_message)
                .setPositiveButton(R.string.ok, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    /** Rejects any edit that inserts more than a couple of characters at once (paste, autofill). */
    private static final class TypingOnlyFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, android.text.Spanned dest, int dstart, int dend) {
            int inserted = (end - start) - (dend - dstart);
            return inserted > MAX_CHARS_PER_INPUT ? "" : null;
        }
    }
}
