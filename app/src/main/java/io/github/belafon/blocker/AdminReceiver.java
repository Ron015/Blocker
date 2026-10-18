package io.github.belafon.blocker;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Being an active device admin is what prevents uninstalling, force stopping and clearing data. */
public class AdminReceiver extends DeviceAdminReceiver {
    public static ComponentName component(Context c) {
        return new ComponentName(c, AdminReceiver.class);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return Prefs.hasPassword(context) ? context.getString(R.string.admin_disable_warning) : null;
    }
}
