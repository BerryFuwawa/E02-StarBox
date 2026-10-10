package com.e02.rootconsole;

import android.content.*;

/** Only standard boot and package replacement events are supported in this baseline. */
public final class StartupReceiver extends BroadcastReceiver {
    static RecoveryPolicy.Event event(String action) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(action))return RecoveryPolicy.Event.COLD_START;
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return RecoveryPolicy.Event.REPLACED;
        return null;
    }
    @Override public void onReceive(Context context,Intent intent) {
        RecoveryPolicy.Event event=intent==null?null:event(intent.getAction());
        if(event==null)return;
        RuntimePolicy.Snapshot saved=RuntimeSettings.snapshot(context);
        if(!RecoveryPolicy.start(saved,RiskNotice.accepted(context),RuntimeSettings.healthy(context),event))return;
        try {context.startForegroundService(new Intent(context,ConsoleService.class).setAction("restore").putExtra("coldStart",event==RecoveryPolicy.Event.COLD_START));}
        catch(RuntimeException denied) { RuntimeRecovery.message="系统暂未允许恢复服务，请打开星匣"; }
    }
}
