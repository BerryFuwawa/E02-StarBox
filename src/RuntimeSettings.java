package com.e02.rootconsole;

import android.content.Context;

/** UI/storage adapter. Android service and power recovery adapters are separate. */
final class RuntimeSettings {
    private static RuntimePolicy policy;
    private static LifecycleFileStore store;
    private RuntimeSettings() {}
    private static void init(Context context) {
        if(policy==null) {
            store=new LifecycleFileStore(context.getApplicationContext().getFilesDir());
            policy=new RuntimePolicy(store);
        }
    }
    static synchronized RuntimePolicy.Snapshot snapshot(Context context) { init(context);return policy.snapshot(); }
    static synchronized boolean healthy(Context context) { init(context);return policy.saveHealthy(); }
    static synchronized boolean open(Context context) {
        init(context);
        if(!RiskNotice.accepted(context)||!policy.saveHealthy())return false;
        return !policy.openByUser(true).has(RuntimePolicy.Effect.SAVE_ERROR)&&policy.saveHealthy();
    }
    static synchronized boolean configure(Context context,boolean keep,boolean boot,boolean parked) {
        init(context);
        if(!policy.saveHealthy())return false;
        if(policy.configure(keep,boot,parked).has(RuntimePolicy.Effect.SAVE_ERROR)) {
            // Show the saved options, not a failed write's proposed values.
            policy=new RuntimePolicy(store);
            return false;
        }
        return true;
    }
    static synchronized RuntimePolicy.Decision requestExit(Context context) { init(context);return policy.exitByUser(); }
    static synchronized boolean runningAllowed(Context context) {
        init(context);RuntimePolicy.Snapshot current=policy.snapshot();
        return policy.saveHealthy()&&RiskNotice.accepted(context)&&current.accepted&&!current.exited;
    }
    static synchronized boolean automaticRootAllowed(Context context) {
        return runningAllowed(context)&&policy.snapshot().rootAllowed;
    }
    static synchronized boolean approveRoot(Context context) {
        init(context);
        if(!runningAllowed(context))return false;
        RuntimePolicy.Decision decision=policy.rootConsentByUser();
        return decision.has(RuntimePolicy.Effect.CHECK_IDENTITY)&&!decision.has(RuntimePolicy.Effect.SAVE_ERROR)&&policy.snapshot().rootAllowed;
    }
    static synchronized boolean endRoot(Context context) {
        init(context);
        return !policy.endRootByUser().has(RuntimePolicy.Effect.SAVE_ERROR);
    }
    static synchronized boolean restore(Context context,boolean coldStart) {
        init(context);
        if(!RiskNotice.accepted(context)||!policy.saveHealthy())return false;
        RuntimePolicy.Decision decision=coldStart?policy.coldStart():(policy.snapshot().autoStart?policy.bootOrWake():policy.restoreExistingService());
        return decision.has(RuntimePolicy.Effect.CHECK_IDENTITY);
    }
}
