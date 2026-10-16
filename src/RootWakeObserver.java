package com.e02.rootconsole;

import android.os.*;
import java.io.File;

/** Android 9 Binder adapter in our existing UID 0 process. Passive, own service only. */
final class RootWakeObserver implements AutoCloseable {
    static final String RESTORE="com.e02.rootconsole.RECOVER_AFTER_WAKE";
    private final File directory;private final RootPermit.Ticket permit;
    private final WakeRestoreController controller=new WakeRestoreController();
    private final OwnedRootCommandRunner runner=new OwnedRootCommandRunner();
    private volatile RootDisplayObserver display;
    private final WakeRestoreController displayRecovery=new WakeRestoreController();
    private final BoundedRead<IBinder> displayLookup=new BoundedRead<>();
    private volatile Thread displayWorker;
    private volatile boolean registered,listeningEstablished;private volatile boolean closed;private volatile Thread worker;private volatile String status="unavailable",loadedVersion="";
    private final Object operationLock=new Object();
    private static final BoundedRead<Boolean> powerReads=new BoundedRead<>();
    private long lastReconcile=-1;
    RootWakeObserver(File directory,RootPermit.Ticket permit){this.directory=directory;this.permit=permit;}
    void register() {
        if(!WakeRecoveryOptions.available()){status="disabled";trace("register","disabled");return;}
        if(permit==null||android.os.Process.myUid()!=0||!RootPermit.matches(permit)){trace("register","permit-denied");return;}
        try {
            loadedVersion=RootServiceVersion.PREFIX+RootServiceVersion.hash(new File(System.getProperty("java.class.path","")));
            display=newDisplayObserver();
            display.register();registered=true;listeningEstablished=true;status=loadedVersion;trace("register","ready");
        }catch(Exception error){if(display!=null)display.close();status="unavailable";trace("register","unavailable");}
    }
    private RootDisplayObserver newDisplayObserver(){
        final class Events implements RootDisplayObserver.Listener{
            RootDisplayObserver owner;
            private boolean current(){return !closed&&display==owner;}
            public void screenOn(){if(current())observed("default-display-on");}
            public void reconcile(){if(current())observed("default-display-recheck");}
            public void screenOff(){if(current()){cancelRound();trace("display","off");}}
            public void unavailable(){if(current()){cancelRound();trace("display","read-unavailable");}}
            public void endpointLost(){if(current()){trace("display","endpoint-lost");if(listeningEstablished)reconnectDisplay();}}
        }
        Events events=new Events();RootDisplayObserver result=new RootDisplayObserver(events);events.owner=result;return result;
    }
    private synchronized void reconnectDisplay(){
        if(!allowed()){trace("rebind","permission-or-options-denied");return;}
        final long ticket=displayRecovery.begin();if(ticket<0)return;
        status="unavailable";
        final long deadline=SystemClock.elapsedRealtime()+120000;
        Thread next=new Thread(()->{
            boolean recovered=displayRecovery.run(ticket,new WakeRestoreController.Driver(){
                public boolean allowed(){return displayRecovery.current(ticket)&&RootWakeObserver.this.allowed()&&SystemClock.elapsedRealtime()<deadline;}
                public long nowMillis(){return SystemClock.elapsedRealtime();}
                public void delay(int seconds)throws InterruptedException{Thread.sleep(seconds*1000L);}
                public boolean request()throws Exception{
                    if(!allowed())return false;
                    RootDisplayObserver lost=display;
                    if(lost!=null&&lost.available())return true;
                    // A read timeout or rejected registration does not prove that
                    // the old callback is gone. Never register a second callback
                    // on that still-live endpoint (Android 9 has no unregister).
                    if(lost==null||!lost.endpointDead()){trace("rebind","registration-uncertain");displayRecovery.cancel();return false;}
                    IBinder candidate=displayLookup.read(()->ReadOnlyServiceLookup.get("display"),Math.min(500,deadline-SystemClock.elapsedRealtime()));
                    if(candidate==null||candidate.equals(lost.endpoint())){trace("rebind","waiting-service");return false;}
                    if(!allowed()||!lost.endpointDead())return false;
                    RootDisplayObserver replacement=newDisplayObserver();
                    synchronized(RootWakeObserver.this){if(!allowed()||display!=lost){replacement.close();return false;}registered=false;display=replacement;lost.close();}
                    try{replacement.register(candidate);}
                    catch(Exception uncertain){
                        replacement.close();trace("rebind","registration-unavailable");
                        if(!replacement.endpointDead())displayRecovery.cancel();
                        return false;
                    }
                    if(!allowed()||display!=replacement){replacement.close();return false;}
                    if(replacement.endpointDead())return false;
                    registered=true;status=loadedVersion;trace("rebind","ready");return true;
                }
            });
            if(recovered&&allowed()&&display!=null&&display.isOn())observed("default-display-reconnected");
            else if(!recovered&&!closed)trace("rebind","not-restored");
        },"starbox-display-rebind");next.setDaemon(true);displayWorker=next;next.start();
    }
    private boolean allowed(){
        if(!WakeRecoveryOptions.available()||closed||!RootPermit.matches(permit))return false;
        try{return WakeRecoveryOptions.mayRestore(new LifecycleFileStore(directory).load(),WakeRecoveryOptions.read(directory));}
        catch(Exception invalid){return false;}
    }
    String status(){return closed?"stopped":registered&&display!=null&&!display.available()?"unavailable":status;}
    private void trace(String stage,String result){WakeDiagnosticLog.record(directory,stage,result,System.currentTimeMillis(),SystemClock.elapsedRealtime(),SystemClock.uptimeMillis());try{int owner=android.system.Os.stat(directory.getPath()).st_uid;if(android.os.Process.myUid()==0&&owner>=10000){File file=new File(directory,"wake-observer-events");android.system.Os.chown(file.getPath(),owner,owner);android.system.Os.chmod(file.getPath(),0600);}}catch(Exception ignored){}}
    static boolean interactive(){return interactive(500);}
    private static boolean interactive(long timeout){try{return powerReads.read(()->{
        Object binder=ReadOnlyServiceLookup.get("power");
        Object power=Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        return Boolean.TRUE.equals(Class.forName("android.os.IPowerManager").getMethod("isInteractive").invoke(power));
    },Math.min(500,timeout));}catch(Exception unavailable){return false;}}
    private boolean repairAllowed(){try{RuntimePolicy.Snapshot state=new LifecycleFileStore(directory).load();return !closed&&RootPermit.matches(permit)&&state.accepted&&!state.exited&&state.rootAllowed&&state.keepAlive&&AuxiliaryRepairChoice.enabled(directory)&&interactive();}catch(Exception invalid){return false;}}
    AuxiliaryRepair.Result repairAccessibility(long deadline){return repairAccessibility(deadline,false,-1);}
    private AuxiliaryRepair.Result repairAccessibility(long deadline,boolean natural,long ticket){synchronized(operationLock){
        AuxiliaryRepair.Result result=AuxiliaryRepair.restore(new AuxiliaryRepair.Driver(){
            public boolean allowed(){return SystemClock.elapsedRealtime()<deadline&&repairAllowed()&&(!natural||controller.current(ticket)&&display!=null&&display.isOn(deadline-SystemClock.elapsedRealtime()))&&SystemClock.elapsedRealtime()<deadline;}
            private String execute(String command)throws java.io.IOException{
                long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<1000||!allowed())throw new java.io.IOException("Recovery cancelled");
                CommandRunner.Result r=runner.execute(command,true,(int)Math.min(3,remaining/1000));
                if(r.exit!=0||r.timedOut||!r.error.isEmpty())throw new java.io.IOException("Settings operation failed");return r.stdout.trim();
            }
            public String readList()throws java.io.IOException{return execute("/system/bin/settings --user 0 get secure enabled_accessibility_services");}
            public String readMaster()throws java.io.IOException{return execute("/system/bin/settings --user 0 get secure accessibility_enabled");}
            public void writeList(String list)throws java.io.IOException{execute("/system/bin/settings --user 0 put secure enabled_accessibility_services '"+AuxiliaryRepair.clean(list)+"'");}
        });trace("auxiliary",result.name());return result;
    }}
    private synchronized void observed(String action){
        final boolean reconnected=action.equals("default-display-reconnected");
        final boolean recheck=action.equals("default-display-recheck")||reconnected;
        if(recheck){long now=SystemClock.elapsedRealtime();if(!reconnected&&lastReconcile>=0&&now-lastReconcile<10000)return;lastReconcile=now;}
        trace("signal",action);
        if(!allowed()){trace("decision","permission-or-options-denied");return;}
        if(display==null||!display.isOn()){trace("decision","display-off-or-unknown");return;}
        long ticket=controller.begin();if(ticket<0){trace("decision","coalesced");return;}
        final long deadline=SystemClock.elapsedRealtime()+120000;
        Thread next=new Thread(()->controller.run(ticket,new WakeRestoreController.Driver(){
            public boolean allowed(){boolean ready=controller.current(ticket)&&SystemClock.elapsedRealtime()<deadline&&RootWakeObserver.this.allowed()&&display!=null&&display.isOn(deadline-SystemClock.elapsedRealtime())&&SystemClock.elapsedRealtime()<deadline;if(!ready)trace("decision","cancelled-or-asleep-or-expired");return ready;}
            public long nowMillis(){return SystemClock.elapsedRealtime();}
            public void delay(int seconds)throws InterruptedException{Thread.sleep(seconds*1000L);}
            public boolean request()throws Exception{
              synchronized(operationLock){
                if(!allowed())return false;
                if(!interactive(deadline-SystemClock.elapsedRealtime())){trace("decision","waiting-interactive");return false;}
                if(recheck){
                    // A CHANGED event does not prove an OFF/ON transition: first read actual own service state.
                    CommandRunner.Result live=runner.execute("/system/bin/dumpsys activity services com.e02.rootconsole/.ConsoleService",true,2);
                    int state=live.exit==0&&!live.timedOut&&live.error.isEmpty()?RecoveryServiceState.parse(live.stdout):-1;
                    if(!allowed())return false;
                    if(state<0){trace("service","liveness-unknown");return false;}
                    if(state==1){
                        if(!AuxiliaryRepairChoice.enabled(directory)){trace("service","already-running");return true;}
                        CommandRunner.Result list=runner.execute("/system/bin/settings --user 0 get secure enabled_accessibility_services",true,2);
                        if(list.exit!=0||list.timedOut||!list.error.isEmpty())return false;
                        try{if(AuxiliaryRepair.contains(AuxiliaryRepair.clean(list.stdout))){trace("service","already-running");return allowed();}}catch(java.io.IOException invalid){return false;}
                    }
                }
                repairAccessibility(deadline,true,ticket);
                if(!allowed()||!interactive(deadline-SystemClock.elapsedRealtime())||!allowed())return false;
                // Fixed own component and a validated generation, no user shell text.
                String request=RootIdentity.challenge();
                long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<1000)return false;
                CommandRunner.Result result=runner.execute("/system/bin/am start-foreground-service --user 0 -n com.e02.rootconsole/.ConsoleService -a "+RESTORE+" --es wakeGeneration "+permit.nonce+" --es wakeRequest "+request,true,(int)Math.min(8,remaining/1000));
                boolean accepted=result.exit==0&&!result.timedOut&&result.error.isEmpty()&&!result.stdout.contains("Error")&&!result.stderr.contains("Error");
                trace("service",accepted?"request-accepted":"request-failed");
                if(accepted)for(int i=0;i<20&&allowed();i++){if(WakeReceipt.matches(directory,permit.nonce,request)){trace("service","receipt-confirmed");return true;}try{Thread.sleep(100);}catch(InterruptedException cancelled){Thread.currentThread().interrupt();return false;}}
                trace("service","receipt-missing");return false;
              }
            }
        }),"starbox-wake-restore");next.setDaemon(true);worker=next;next.start();
    }
    private void cancelRound(){controller.cancel();Thread active=worker;if(active!=null)active.interrupt();}
    @Override public void close(){
        closed=true;controller.close();displayRecovery.close();displayLookup.close();runner.close();Thread active=worker;if(active!=null)active.interrupt();Thread reconnecting=displayWorker;if(reconnecting!=null)reconnecting.interrupt();
        registered=false;listeningEstablished=false;if(display!=null)display.close();
    }
}
