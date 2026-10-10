package com.flyme.auto.plugin.systemui;

import android.view.View;
import android.service.notification.StatusBarNotification;
import com.flyme.plugin.Plugin;

/** Compile-only host ABI; intentionally no implementation/default methods. */
public interface StatusBarPlugin extends Plugin {
    String ACTION="com.flyme.auto.plugin.action.PLUGIN_STATUS_BAR";int VERSION=1;
    int getDialogHeight();int getDialogWidth();String getDialogTitle();View getDialogView();
    void onDialogDismissed();void onDialogShowed();void onStatusIconPosted(StatusBarNotification notification);
    void setDialogCallback(DialogCallback callback);
    interface DialogCallback {void dismissDialog();boolean isPanelExpended();void updateDialogTitle(String title);boolean updateHeightFromAnim(int height);}
}
