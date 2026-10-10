package com.e02.rootconsole;

import java.util.*;

/** Lifecycle decisions. The Android adapter must perform the returned effects.
 * This class has no shell, network, Android, scheduling or power operations. */
public final class RuntimePolicy {
    public enum Effect {
        NONE, CHECK_IDENTITY, START_OWNED_ROOT, STOP_OWNED_ROOT,
        STOP_ALL_OWNED, CANCEL_RETRIES, RETRY_IDENTITY,
        PREPARE_PARKED_REMOTE, RELEASE_PARKED_REMOTE, REMOTE_OFFLINE,
        NEED_ROOT_CONSENT, WAIT_POWER_SUPPORT, WAIT_USER, SAVE_ERROR, EXIT_BLOCKED
    }
    public enum Power { UNKNOWN, DRIVING_AWAKE, PARKED_AWAKE, SUSPENDING, ASLEEP }
    public enum Root { UNKNOWN, BACKEND_ONLY, VERIFIED_OWNED, UNAVAILABLE }
    public interface Store {
        Snapshot load() throws Exception;
        boolean commit(Snapshot next);
    }
    public static final class Snapshot {
        public final boolean accepted, keepAlive, autoStart, parkedRemote, exited, rootAllowed;
        public final long revision;
        public Snapshot(boolean accepted, boolean keepAlive, boolean autoStart, boolean parkedRemote,
                        boolean exited, boolean rootAllowed, long revision) {
            if(revision < 0 || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid revision");
            this.accepted=accepted; this.keepAlive=keepAlive; this.autoStart=autoStart;
            this.parkedRemote=parkedRemote; this.exited=exited; this.rootAllowed=rootAllowed; this.revision=revision;
        }
        public String encode() {
            return "E02-LIFECYCLE-1:"+revision+":"+bit(accepted)+bit(keepAlive)+bit(autoStart)+bit(parkedRemote)+bit(exited)+bit(rootAllowed);
        }
        private static char bit(boolean value) { return value?'1':'0'; }
        public static Snapshot decode(String text) {
            String[] parts=text.split(":",-1);
            if(parts.length!=3 || !parts[0].equals("E02-LIFECYCLE-1") || !parts[1].matches("0|[1-9][0-9]*") || !parts[2].matches("[01]{6}"))
                throw new IllegalArgumentException("Invalid saved state");
            String f=parts[2];
            return new Snapshot(f.charAt(0)=='1',f.charAt(1)=='1',f.charAt(2)=='1',f.charAt(3)=='1',f.charAt(4)=='1',f.charAt(5)=='1',Long.parseLong(parts[1]));
        }
    }
    public static final class Decision {
        public final EnumSet<Effect> effects;
        public final long ticket;
        public final int retrySeconds;
        private Decision(long ticket,int delay,Effect... effects) {
            this.ticket=ticket;retrySeconds=delay;
            this.effects=EnumSet.noneOf(Effect.class);Collections.addAll(this.effects,effects);
        }
        public boolean has(Effect effect) { return effects.contains(effect); }
    }
    private final Store store;
    private Snapshot state;
    private boolean storageHealthy,active,suspended;
    private long generation;
    private int retries,ownedPid;
    private Power power=Power.UNKNOWN;
    private Root root=Root.UNKNOWN;

    public RuntimePolicy(Store store) {
        this.store=store;
        try { state=store.load();storageHealthy=state!=null; }
        catch(Exception e) { storageHealthy=false; }
        if(!storageHealthy) state=new Snapshot(false,false,false,false,true,false,0);
        generation=state.revision;
    }
    private void invalidate() { if(generation==Long.MAX_VALUE)throw new IllegalStateException("Generation exhausted");generation++; }
    private Snapshot next(boolean accepted,boolean keep,boolean boot,boolean parked,boolean exit,boolean rootAllowed) {
        return new Snapshot(accepted,keep,boot,parked,exit,rootAllowed,Math.max(state.revision,generation));
    }
    private boolean save(Snapshot next) {
        state=next;
        try { storageHealthy=store.commit(next); } catch(RuntimeException e) { storageHealthy=false; }
        return storageHealthy;
    }
    private Decision decision(Effect... effects) { return new Decision(generation,0,effects); }
    private boolean runningAllowed() { return storageHealthy&&state.accepted&&!state.exited&&active&&!suspended; }
    private boolean current(long ticket) { return ticket==generation&&runningAllowed(); }
    public synchronized Decision openByUser(boolean accepted) {
        invalidate();active=false;suspended=false;root=Root.UNKNOWN;ownedPid=0;retries=0;
        if(!accepted)return decision(Effect.WAIT_USER);
        if(!save(next(true,state.keepAlive,state.autoStart,state.parkedRemote,false,state.rootAllowed)))return decision(Effect.SAVE_ERROR);
        active=true;return decision(Effect.CHECK_IDENTITY);
    }
    public synchronized Decision configure(boolean keepAlive,boolean autoStart,boolean parkedRemote) {
        invalidate();retries=0;
        if(!save(next(state.accepted,keepAlive,autoStart,parkedRemote,state.exited,state.rootAllowed)))
            return decision(Effect.CANCEL_RETRIES,Effect.RELEASE_PARKED_REMOTE,Effect.SAVE_ERROR);
        return decision(Effect.CANCEL_RETRIES,Effect.RELEASE_PARKED_REMOTE);
    }
    public synchronized Decision exitByUser() {
        if(ExitPolicy.blocked(state.keepAlive,state.autoStart,state.parkedRemote))return decision(Effect.EXIT_BLOCKED);
        // Invalidate callbacks first; persist the exit gate before stopping anything.
        invalidate();active=false;suspended=false;root=Root.UNKNOWN;ownedPid=0;retries=0;
        boolean saved=save(next(state.accepted,state.keepAlive,state.autoStart,state.parkedRemote,true,state.rootAllowed));
        return saved?decision(Effect.STOP_ALL_OWNED,Effect.CANCEL_RETRIES,Effect.RELEASE_PARKED_REMOTE):
            decision(Effect.STOP_ALL_OWNED,Effect.CANCEL_RETRIES,Effect.RELEASE_PARKED_REMOTE,Effect.SAVE_ERROR);
    }
    public synchronized Decision bootOrWake() {
        invalidate();root=Root.UNKNOWN;ownedPid=0;retries=0;active=false;suspended=false;
        if(!storageHealthy||!state.accepted||state.exited||!state.autoStart)return decision(Effect.NONE);
        active=true;return decision(Effect.CHECK_IDENTITY);
    }
    /** A genuine startup event may resume after an earlier user exit; wake/replacement may not. */
    public synchronized Decision coldStart() {
        invalidate();root=Root.UNKNOWN;ownedPid=0;retries=0;active=false;suspended=false;
        if(!storageHealthy||!state.accepted||!state.autoStart)return decision(Effect.NONE);
        if(state.exited&&!save(next(state.accepted,state.keepAlive,state.autoStart,state.parkedRemote,false,state.rootAllowed)))return decision(Effect.SAVE_ERROR);
        active=true;return decision(Effect.CHECK_IDENTITY);
    }
    /** Android may recreate an already-running sticky service after process loss.
     * This never changes an option or clears an explicit exit gate. */
    public synchronized Decision restoreExistingService() {
        invalidate();root=Root.UNKNOWN;ownedPid=0;retries=0;active=false;suspended=false;
        if(!storageHealthy||!state.accepted||state.exited||!backgroundRecoveryWanted())return decision(Effect.NONE);
        active=true;return decision(Effect.CHECK_IDENTITY);
    }
    public synchronized Decision suspend() {
        invalidate();suspended=true;root=Root.UNKNOWN;ownedPid=0;power=Power.ASLEEP;retries=0;
        return decision(Effect.CANCEL_RETRIES,Effect.RELEASE_PARKED_REMOTE,Effect.REMOTE_OFFLINE);
    }
    public synchronized Decision powerSnapshot(Power actual,boolean safeMaintenanceVerified) {
        power=Objects.requireNonNull(actual);
        if(actual==Power.SUSPENDING||actual==Power.ASLEEP)return suspend();
        return decision(Effect.RELEASE_PARKED_REMOTE);
    }
    public synchronized Decision unexpectedServiceStop() {
        invalidate();root=Root.UNKNOWN;ownedPid=0;
        if(!runningAllowed()||!backgroundRecoveryWanted())return decision(Effect.NONE);
        if(retries>=3)return decision(Effect.WAIT_USER);
        retries++;return new Decision(generation,new int[]{1,5,15}[retries-1],Effect.RETRY_IDENTITY);
    }
    private boolean backgroundRecoveryWanted() { return state.keepAlive; }
    public synchronized boolean retryMayRun(long ticket) { return current(ticket)&&backgroundRecoveryWanted(); }
    public synchronized Decision rootConsentByUser() {
        if(!runningAllowed())return decision(Effect.WAIT_USER);
        invalidate();root=Root.UNKNOWN;ownedPid=0;
        if(!save(next(state.accepted,state.keepAlive,state.autoStart,state.parkedRemote,state.exited,true)))return decision(Effect.SAVE_ERROR);
        return decision(Effect.CHECK_IDENTITY);
    }
    public synchronized Decision endRootByUser() {
        invalidate();root=Root.UNKNOWN;ownedPid=0;
        boolean saved=save(next(state.accepted,state.keepAlive,state.autoStart,state.parkedRemote,state.exited,false));
        return saved?decision(Effect.STOP_OWNED_ROOT,Effect.CANCEL_RETRIES):decision(Effect.STOP_OWNED_ROOT,Effect.CANCEL_RETRIES,Effect.SAVE_ERROR);
    }
    public synchronized Decision backendIdentity(long ticket,int uid) {
        if(!current(ticket))return decision(Effect.NONE);
        if(uid!=0){root=Root.UNAVAILABLE;ownedPid=0;return decision(Effect.NONE);}
        root=Root.BACKEND_ONLY;
        return state.rootAllowed?decision(Effect.START_OWNED_ROOT):decision(Effect.NEED_ROOT_CONSENT);
    }
    public synchronized boolean ownedIdentity(long ticket,int uid,int pid,boolean authenticated) {
        if(!current(ticket)||!state.rootAllowed)return false;
        if(!authenticated||uid!=0||pid<=0){root=Root.UNAVAILABLE;ownedPid=0;return false;}
        root=Root.VERIFIED_OWNED;ownedPid=pid;retries=0;return true;
    }
    public synchronized Root rootState() { return root; }
    public synchronized int ownedPid() { return ownedPid; }
    public synchronized Snapshot snapshot() { return state; }
    public synchronized boolean saveHealthy() { return storageHealthy; }
    public synchronized long ticket() { return generation; }
}
