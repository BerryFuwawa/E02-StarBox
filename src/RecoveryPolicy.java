package com.e02.rootconsole;

/** Shared service decisions: never holds a wake lock or modifies an option. */
final class RecoveryPolicy {
    enum Event { COLD_START, WAKE, REPLACED, SERVICE_RESTART }
    static boolean valid(RuntimePolicy.Snapshot state,boolean accepted,boolean healthy) {
        return healthy&&accepted&&state.accepted&&!state.exited;
    }
    // Legacy parkedRemote is retained in storage for compatibility but is inactive in this baseline.
    static boolean background(RuntimePolicy.Snapshot state) { return background(state,false); }
    static boolean background(RuntimePolicy.Snapshot state,boolean wakeRecovery) { return state.keepAlive||state.autoStart||wakeRecovery; }
    static boolean serviceWanted(RuntimePolicy.Snapshot state,boolean functionalWork) { return serviceWanted(state,functionalWork,false); }
    static boolean serviceWanted(RuntimePolicy.Snapshot state,boolean functionalWork,boolean wakeRecovery) {
        return state.accepted&&!state.exited&&(background(state,wakeRecovery)||functionalWork);
    }
    static boolean start(RuntimePolicy.Snapshot state,boolean accepted,boolean healthy,Event event) { return start(state,accepted,healthy,event,false); }
    static boolean start(RuntimePolicy.Snapshot state,boolean accepted,boolean healthy,Event event,boolean wakeRecovery) {
        if(!healthy||!accepted||!state.accepted)return false;
        if(event==Event.COLD_START)return state.autoStart;
        if(state.exited)return false;
        if(event==Event.WAKE)return state.autoStart||wakeRecovery;
        if(event==Event.SERVICE_RESTART)return background(state,wakeRecovery);
        return state.autoStart||background(state,wakeRecovery);
    }
    static boolean restoreChannels(RuntimePolicy.Snapshot state,boolean accepted,boolean healthy) { return restoreChannels(state,accepted,healthy,false); }
    static boolean restoreChannels(RuntimePolicy.Snapshot state,boolean accepted,boolean healthy,boolean wakeRecovery) {
        return valid(state,accepted,healthy)&&(state.autoStart||background(state,wakeRecovery));
    }
    /** Partial wake locks and wakeup alarms would violate the safe-sleep policy. */
    static final int[] RETRY_SECONDS={1,5,15};
}
