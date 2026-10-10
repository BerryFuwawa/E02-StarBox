package com.e02.rootconsole;

public final class TestRecoveryPolicy {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        for(int bits=0;bits<8;bits++){
            boolean keep=(bits&1)!=0,boot=(bits&2)!=0,park=(bits&4)!=0;
            RuntimePolicy.Snapshot live=new RuntimePolicy.Snapshot(true,keep,boot,park,false,true,0);
            check(RecoveryPolicy.background(live)==keep);
            check(RecoveryPolicy.serviceWanted(live,false)==keep);
            check(RecoveryPolicy.serviceWanted(live,true));
            check(RecoveryPolicy.start(live,true,true,RecoveryPolicy.Event.COLD_START)==boot);
            check(RecoveryPolicy.start(live,true,true,RecoveryPolicy.Event.SERVICE_RESTART)==keep);
            check(!RecoveryPolicy.start(live,true,true,RecoveryPolicy.Event.WAKE));
            check(RecoveryPolicy.start(live,true,true,RecoveryPolicy.Event.REPLACED)==(keep||boot));
            check(RecoveryPolicy.restoreChannels(live,true,true)==(keep||boot));
            for(RecoveryPolicy.Event event:RecoveryPolicy.Event.values()){
                check(!RecoveryPolicy.start(live,false,true,event));check(!RecoveryPolicy.start(live,true,false,event));
                RuntimePolicy.Snapshot exited=new RuntimePolicy.Snapshot(true,keep,boot,park,true,true,1);
                check(RecoveryPolicy.start(exited,true,true,event)==(event==RecoveryPolicy.Event.COLD_START&&boot));
                RuntimePolicy.Snapshot unaccepted=new RuntimePolicy.Snapshot(false,keep,boot,park,false,true,1);
                check(!RecoveryPolicy.start(unaccepted,true,true,event));
            }
            check(live.keepAlive==keep&&live.autoStart==boot&&live.parkedRemote==park);
        }
        check(RecoveryPolicy.RETRY_SECONDS.length==3&&RecoveryPolicy.RETRY_SECONDS[0]==1&&RecoveryPolicy.RETRY_SECONDS[1]==5&&RecoveryPolicy.RETRY_SECONDS[2]==15);
        System.out.println("PASS "+checks+" recovery policy checks: eight independent combinations, standard boot/restart/replaced gates, dormant legacy parked flag, ignored wake, exit/consent/storage failure and bounded retry delays. This does not simulate vehicle power or Android services.");
    }
}
