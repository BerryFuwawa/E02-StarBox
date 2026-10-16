package com.e02.rootconsole;

/** A system binding can recover our service; it cannot grant Root or wake the vehicle. */
final class SystemKeepAliveGate {
    enum Decision { SKIP, RESTORE, COLD_START }
    static Decision decide(RuntimePolicy.Snapshot state,boolean accepted,boolean healthy,
                           boolean requested,boolean knownBoot,boolean newBoot) {
        if(!requested||!healthy||!accepted||!state.accepted||!state.keepAlive||!knownBoot)return Decision.SKIP;
        if(newBoot)return state.autoStart?Decision.COLD_START:Decision.SKIP;
        return state.exited?Decision.SKIP:Decision.RESTORE;
    }
}
