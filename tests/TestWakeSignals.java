package com.e02.rootconsole;

/** Legacy event names stay recognizable for research, but no longer authorize recovery. */
public final class TestWakeSignals {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args)throws Exception {
        String[] events=WakeSignals.registeredActions();
        check(WakeSignals.observed("ecarx.intent.action.DISPLAY_ON"));
        check(!WakeSignals.observed("ecarx.intent.action.DISPLAY_OFF"));
        check(!WakeSignals.observed("ecarx.intent.action.carsignal.AVNOFF_ON"));
        check(!WakeSignals.observed(null)&&!WakeSignals.observed("ecarx.intent.action.DISPLAY_ON "));
        events[0]="mutated";check(WakeSignals.observed("android.intent.action.SCREEN_ON"));
        // REGISTERED_ONLY cannot cold-start our manifest receiver.
        check(StartupReceiver.event(WakeSignals.DISPLAY_ON)==null);
        check(StartupReceiver.event("ecarx.intent.action.DISPLAY_OFF")==null);
        for(int bits=0;bits<8;bits++){
            RuntimePolicy.Snapshot before=new RuntimePolicy.Snapshot(true,(bits&1)!=0,(bits&2)!=0,(bits&4)!=0,false,true,0);
            String saved=before.encode();
            for(String event:WakeSignals.registeredActions()){
                boolean actual=WakeSignals.observed(event)&&RecoveryPolicy.start(before,true,true,RecoveryPolicy.Event.WAKE);
                check(!actual);
                check(StartupReceiver.event(event)==null);
                check(before.encode().equals(saved));
            }
            check(!RecoveryPolicy.start(before,false,true,RecoveryPolicy.Event.WAKE));
            check(!RecoveryPolicy.start(before,true,false,RecoveryPolicy.Event.WAKE));
        }
        System.out.println("PASS "+checks+" dormant wake-event checks: names retained as research clues, none trigger startup or recovery for any saved option; no vehicle power or Android delivery.");
    }
}
