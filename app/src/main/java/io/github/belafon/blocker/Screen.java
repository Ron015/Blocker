package io.github.belafon.blocker;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Layout helpers shared by all screens. */
final class Screen {
    private Screen() {}

    /**
     * Since Android 15, apps draw behind the status bar, navigation bar and keyboard. This pads
     * the root view so nothing ends up hidden behind them. Older versions don't need it.
     */
    static void padForSystemBars(View root) {
        if (Build.VERSION.SDK_INT < 35) return;
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return WindowInsets.CONSUMED;
        });
    }
}
