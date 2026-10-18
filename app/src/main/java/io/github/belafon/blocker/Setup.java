package io.github.belafon.blocker;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Locale;

/** The setup steps and the system screens they need, shared by MainActivity and the help cards. */
final class Setup {
    static final int REQUEST_VPN = 1;

    private Setup() {}

    // --- Filter ---

    /** Starts the filter, first asking for the VPN permission if needed. */
    static void startFilter(Activity activity) {
        Intent consent = VpnService.prepare(activity);
        if (consent != null) {
            activity.startActivityForResult(consent, REQUEST_VPN);
        } else {
            BlockerVpnService.start(activity);
        }
    }

    /** Call from onActivityResult. */
    static void onActivityResult(Activity activity, int requestCode, int resultCode) {
        if (requestCode == REQUEST_VPN && resultCode == Activity.RESULT_OK) BlockerVpnService.start(activity);
    }

    // --- Uninstall protection ---

    static boolean isAdminActive(Context c) {
        return c.getSystemService(DevicePolicyManager.class).isAdminActive(AdminReceiver.component(c));
    }

    static void requestAdmin(Activity activity) {
        open(activity, new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(activity))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(R.string.admin_explanation)));
    }

    // --- Settings screens ---

    static void openAccessibilitySettings(Activity activity) {
        open(activity, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    static void openNotificationSettings(Activity activity) {
        open(activity, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
    }

    static boolean notificationsEnabled(Context c) {
        return c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    static void openAppInfo(Activity activity) {
        open(activity, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.getPackageName(), null)));
    }

    static void openVpnSettings(Activity activity) {
        open(activity, new Intent(Settings.ACTION_VPN_SETTINGS));
    }

    static boolean ignoresBatteryOptimizations(Context c) {
        return c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** Xiaomi keeps the battery setting in App info; elsewhere there's a list of all apps. */
    static void openBatterySettings(Activity activity) {
        if (isXiaomi()) {
            openAppInfo(activity);
        } else {
            open(activity, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    static boolean isXiaomi() {
        return "xiaomi".equals(Build.MANUFACTURER.toLowerCase(Locale.ROOT));
    }

    /** Xiaomi (MIUI / HyperOS) won't start apps after a reboot unless "Autostart" is allowed. */
    static void openXiaomiAutostart(Activity activity) {
        Intent autostart = new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        try {
            activity.startActivity(autostart);
        } catch (ActivityNotFoundException | SecurityException e) {
            openAppInfo(activity);
        }
    }

    /**
     * Android 13+ blocks accessibility services of apps installed outside an app store until the
     * user allows "restricted settings" in App info.
     */
    static boolean needsRestrictedSettings(Context c) {
        if (Build.VERSION.SDK_INT < 33) return false;
        try {
            String installer = c.getPackageManager().getInstallSourceInfo(c.getPackageName()).getInstallingPackageName();
            return !"com.android.vending".equals(installer);
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private static void open(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.screen_not_found, Toast.LENGTH_LONG).show();
        }
    }
}
