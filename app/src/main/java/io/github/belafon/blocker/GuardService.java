package io.github.belafon.blocker;

import android.accessibilityservice.AccessibilityService;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * While the filter is locked (a password is set), this closes the Settings screens that would
 * turn the protection off:
 * - deactivating Blocker as device admin (needed before uninstalling),
 * - the uninstall dialog,
 * - the VPN details page of Blocker ("Forget VPN", "Always-on VPN") and the VPN disconnect dialog,
 * - the accessibility page of this guard (so it can't be switched off).
 */
public class GuardService extends AccessibilityService {
    /** Keep in sync with android:packageNames in res/xml/guard_service.xml. */
    private static final Set<String> WATCHED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.android.vpndialogs",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.securitycenter"));
    private static final String VPN_DIALOGS = "com.android.vpndialogs";
    private static final long CHECK_INTERVAL_MS = 300;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> lastWindowClass = new HashMap<>();
    private final Runnable check = this::checkActiveWindow;
    private String appLabel;
    private String guardLabel;
    private long lastCheck;
    private long lastBlock;

    public static boolean isEnabled(Context c) {
        String enabled = Settings.Secure.getString(
                c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(c, GuardService.class);
        for (String service : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(service);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        appLabel = getApplicationInfo().loadLabel(getPackageManager()).toString();
        guardLabel = getString(R.string.guard_label);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getClassName() != null) {
            lastWindowClass.put(pkg, event.getClassName().toString());
        }
        if (!WATCHED_PACKAGES.contains(pkg) || !Prefs.hasPassword(this)) return;

        // Throttle: content-changed events come in bursts. Always do one trailing check.
        long now = SystemClock.uptimeMillis();
        handler.removeCallbacks(check);
        if (now - lastCheck >= CHECK_INTERVAL_MS) {
            checkActiveWindow();
        } else {
            handler.postDelayed(check, CHECK_INTERVAL_MS);
        }
    }

    private void checkActiveWindow() {
        lastCheck = SystemClock.uptimeMillis();
        if (!Prefs.hasPassword(this)) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();
        if (WATCHED_PACKAGES.contains(pkg) && shouldBlock(pkg, root)) block();
    }

    private boolean shouldBlock(String pkg, AccessibilityNodeInfo root) {
        if (pkg.equals(VPN_DIALOGS)) {
            // "Disconnect" dialog, or another VPN app asking to replace ours. While our VPN is not
            // running it may be our own permission dialog, so let it through then.
            return BlockerVpnService.isRunning();
        }
        // Accessibility settings of the guard itself.
        if (countNodes(root, guardLabel, false) > 0) return true;

        // Everything else only matters on screens that are about Blocker.
        if (countNodes(root, appLabel, true) == 0) return false;
        String windowClass = lastWindowClass.getOrDefault(pkg, "");
        if (windowClass.contains("DeviceAdminAdd")) {
            // Deactivation screen. While not active yet it's the activation screen: allow that.
            return isAdminActive();
        }
        if (pkg.contains("packageinstaller")) return true; // uninstall dialog
        // VPN details page of Blocker: "Always-on VPN", "Block connections without VPN",
        // "Forget VPN". Pages that merely list the VPN mention "VPN" only once.
        return countNodes(root, "VPN", false) >= 2;
    }

    private static int countNodes(AccessibilityNodeInfo root, String text, boolean exact) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (!exact) return nodes.size();
        int count = 0;
        for (AccessibilityNodeInfo node : nodes) {
            if (equalsIgnoreCase(node.getText(), text) || equalsIgnoreCase(node.getContentDescription(), text)) {
                count++;
            }
        }
        return count;
    }

    private static boolean equalsIgnoreCase(CharSequence cs, String text) {
        return cs != null && cs.toString().trim().equalsIgnoreCase(text);
    }

    private boolean isAdminActive() {
        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        return dpm.isAdminActive(new ComponentName(this, AdminReceiver.class));
    }

    private void block() {
        long now = SystemClock.uptimeMillis();
        if (now - lastBlock < 1000) return;
        lastBlock = now;
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 200);
        Toast.makeText(this, R.string.guard_blocked, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onInterrupt() {
    }
}
