package com.flyme.plugin;

import android.content.ComponentName;
import android.content.Context;

/** Signatures only, derived from the installed host. Never package this interface in an APK. */
public interface Plugin {
    int getID();int getVersion();ComponentName getComponentName();
    void onCreate(Context systemContext,Context pluginContext);void onDestroy();
}
