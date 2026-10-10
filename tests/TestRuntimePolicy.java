package com.e02.rootconsole;

import static com.e02.rootconsole.RuntimePolicy.*;
import static com.e02.rootconsole.RuntimePolicy.Effect.*;

public final class TestRuntimePolicy {
    private static int checks;
    private static void check(boolean value) { checks++;if(!value)throw new AssertionError("Check "+checks); }
    private static void invalid(String text) { try{Snapshot.decode(text);throw new AssertionError("Invalid accepted");}catch(IllegalArgumentException expected){checks++;} }
    private static final class Memory implements Store {
        String text;boolean fail,throwLoad;
        Memory(Snapshot s){text=s==null?null:s.encode();}
        public Snapshot load() throws Exception{if(throwLoad)throw new Exception("read failure");return text==null?null:Snapshot.decode(text);}
        public boolean commit(Snapshot next){if(fail)return false;text=next.encode();return true;}
    }
    private static RuntimePolicy ready(Memory m){RuntimePolicy p=new RuntimePolicy(m);check(p.openByUser(true).has(CHECK_IDENTITY));return p;}
    public static void main(String[] args) {
        // All eight independent option combinations persist across a fresh controller.
        for(int bits=0;bits<8;bits++){
            boolean keep=(bits&1)!=0,boot=(bits&2)!=0,park=(bits&4)!=0;
            Memory m=new Memory(new Snapshot(true,false,false,false,false,false,0));
            RuntimePolicy p=ready(m);check(p.configure(keep,boot,park).has(CANCEL_RETRIES));
            RuntimePolicy restored=new RuntimePolicy(m);Snapshot s=restored.snapshot();
            check(s.keepAlive==keep&&s.autoStart==boot&&s.parkedRemote==park);
            check(restored.bootOrWake().has(boot?CHECK_IDENTITY:NONE));
            check(restored.restoreExistingService().has(keep?CHECK_IDENTITY:NONE));
            check(restored.snapshot().keepAlive==keep&&restored.snapshot().autoStart==boot&&restored.snapshot().parkedRemote==park);
            long before=p.ticket();
            if(keep){check(p.exitByUser().has(EXIT_BLOCKED));check(!p.snapshot().exited&&p.ticket()==before);}else{check(p.exitByUser().has(STOP_ALL_OWNED));check(p.snapshot().exited&&p.snapshot().autoStart==boot&&p.snapshot().parkedRemote==park);check(p.openByUser(true).has(CHECK_IDENTITY));}
            check(p.unexpectedServiceStop().has(keep?RETRY_IDENTITY:NONE));
            check(p.snapshot().keepAlive==keep&&p.snapshot().autoStart==boot&&p.snapshot().parkedRemote==park);
            check(p.configure(false,false,false).has(RELEASE_PARKED_REMOTE));
            check(p.exitByUser().has(STOP_ALL_OWNED));restored=new RuntimePolicy(m);
            check(restored.snapshot().exited&&restored.bootOrWake().has(NONE));
            check(restored.restoreExistingService().has(NONE));
            check(restored.unexpectedServiceStop().has(NONE));
            check(restored.powerSnapshot(Power.PARKED_AWAKE,true).has(RELEASE_PARKED_REMOTE));
            check(restored.openByUser(true).has(CHECK_IDENTITY)&&!restored.snapshot().exited);
        }
        invalid("E02-LIFECYCLE-1:0:111111:extra");invalid("E02-LIFECYCLE-1:01:111111");
        // Boot is a future preference, not an active keepalive lock. Only a genuine new
        // boot clears a deliberate exit; stale work and package replacement stay stopped.
        Memory restartStore=new Memory(new Snapshot(true,false,true,true,false,true,0));
        RuntimePolicy restart=ready(restartStore);long oldTicket=restart.ticket();
        check(restart.exitByUser().has(STOP_ALL_OWNED));check(restart.snapshot().autoStart&&restart.snapshot().parkedRemote);
        check(restart.bootOrWake().has(NONE)&&restart.restoreExistingService().has(NONE));
        check(!restart.ownedIdentity(oldTicket,0,42,true));
        restart=new RuntimePolicy(restartStore);check(restart.coldStart().has(CHECK_IDENTITY));
        check(!restart.snapshot().exited&&restart.snapshot().autoStart&&restart.snapshot().rootAllowed);
        check(restart.exitByUser().has(STOP_ALL_OWNED));restartStore.fail=true;
        check(restart.coldStart().has(SAVE_ERROR)&&!restart.saveHealthy());
        check(!restart.retryMayRun(restart.ticket()));
        invalid("E02-LIFECYCLE-1:-1:111111");invalid("E02-LIFECYCLE-1:0:11111x");
        invalid("E02-LIFECYCLE-1:9223372036854775807:111111");invalid("E02-LIFECYCLE-2:0:111111");
        Memory missing=new Memory(null);RuntimePolicy cold=new RuntimePolicy(missing);
        check(cold.bootOrWake().has(NONE));check(cold.openByUser(false).has(WAIT_USER));
        check(cold.openByUser(true).has(CHECK_IDENTITY)&&cold.saveHealthy());
        Memory m=new Memory(new Snapshot(true,true,true,true,false,false,0));RuntimePolicy p=ready(m);
        long t=p.ticket();check(p.backendIdentity(t,0).has(NEED_ROOT_CONSENT));check(p.rootState()==Root.BACKEND_ONLY);
        check(!p.ownedIdentity(t,0,42,true));check(p.rootConsentByUser().has(CHECK_IDENTITY));
        t=p.ticket();check(p.backendIdentity(t,0).has(START_OWNED_ROOT));check(p.rootState()!=Root.VERIFIED_OWNED);
        check(!p.ownedIdentity(t,0,42,false));check(!p.ownedIdentity(t,2000,42,true));check(!p.ownedIdentity(t,0,0,true));
        check(p.ownedIdentity(t,0,42,true)&&p.rootState()==Root.VERIFIED_OWNED&&p.ownedPid()==42);
        check(p.exitByUser().has(EXIT_BLOCKED)&&p.ticket()==t&&p.rootState()==Root.VERIFIED_OWNED&&p.ownedPid()==42);
        Decision disableParked=p.configure(true,true,false);check(disableParked.has(RELEASE_PARKED_REMOTE)&&!disableParked.has(STOP_ALL_OWNED)&&!disableParked.has(STOP_OWNED_ROOT));
        check(p.snapshot().keepAlive&&p.snapshot().autoStart&&!p.snapshot().parkedRemote&&p.rootState()==Root.VERIFIED_OWNED&&p.ownedPid()==42);
        check(p.endRootByUser().has(STOP_OWNED_ROOT));check(!p.ownedIdentity(t,0,42,true));
        RuntimePolicy restored=new RuntimePolicy(m);check(restored.bootOrWake().has(CHECK_IDENTITY));
        check(restored.backendIdentity(restored.ticket(),0).has(NEED_ROOT_CONSENT));
        check(restored.rootConsentByUser().has(CHECK_IDENTITY));t=restored.ticket();
        check(restored.backendIdentity(t,2000).has(NONE)&&restored.rootState()==Root.UNAVAILABLE);
        check(restored.backendIdentity(t,0).has(START_OWNED_ROOT));
        check(restored.exitByUser().has(EXIT_BLOCKED));check(!restored.snapshot().exited);
        check(restored.configure(false,false,false).has(RELEASE_PARKED_REMOTE));
        check(restored.exitByUser().has(STOP_ALL_OWNED));check(restored.backendIdentity(t,0).has(NONE));
        check(!restored.ownedIdentity(t,0,42,true));check(!restored.retryMayRun(t));
        check(new RuntimePolicy(m).bootOrWake().has(NONE));
        p=ready(new Memory(new Snapshot(true,true,true,true,false,true,0)));
        Decision retry=p.unexpectedServiceStop();check(retry.has(RETRY_IDENTITY)&&retry.retrySeconds==1);t=retry.ticket;
        check(p.retryMayRun(t));check(p.unexpectedServiceStop().retrySeconds==5);check(!p.retryMayRun(t));
        check(p.unexpectedServiceStop().retrySeconds==15);check(p.unexpectedServiceStop().has(WAIT_USER));
        check(p.powerSnapshot(Power.PARKED_AWAKE,false).has(RELEASE_PARKED_REMOTE));
        check(p.powerSnapshot(Power.UNKNOWN,true).has(RELEASE_PARKED_REMOTE));
        check(p.powerSnapshot(Power.DRIVING_AWAKE,true).has(RELEASE_PARKED_REMOTE));
        check(p.powerSnapshot(Power.PARKED_AWAKE,true).has(RELEASE_PARKED_REMOTE));
        t=p.ticket();check(p.suspend().has(REMOTE_OFFLINE));check(!p.retryMayRun(t));
        check(!p.ownedIdentity(t,0,42,true));check(p.unexpectedServiceStop().has(NONE));
        check(p.bootOrWake().has(CHECK_IDENTITY));
        check(p.configure(false,false,false).has(RELEASE_PARKED_REMOTE));check(p.unexpectedServiceStop().has(NONE));
        check(p.suspend().has(CANCEL_RETRIES));check(p.bootOrWake().has(NONE));
        m=new Memory(new Snapshot(true,true,true,true,false,true,0));p=ready(m);check(p.configure(false,false,false).has(RELEASE_PARKED_REMOTE));t=p.ticket();m.fail=true;
        Decision exit=p.exitByUser();check(exit.has(STOP_ALL_OWNED)&&exit.has(SAVE_ERROR));
        check(p.snapshot().exited&&!p.saveHealthy());check(p.backendIdentity(t,0).has(NONE));check(!p.retryMayRun(t));
        check(p.bootOrWake().has(NONE));
        m=new Memory(new Snapshot(true,true,true,true,false,true,0));m.throwLoad=true;
        p=new RuntimePolicy(m);check(!p.saveHealthy()&&p.bootOrWake().has(NONE));
        System.out.println("PASS "+checks+" checks: boot preference persists through exit; only background keepalive blocks exit; dormant legacy parked flag, consent/revoke, backend vs owned root, stale callbacks, bounded retries, sleep/no power-write policy, storage failure");
    }
}
