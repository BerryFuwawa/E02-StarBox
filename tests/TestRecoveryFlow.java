package com.e02.rootconsole;

import java.util.*;

public final class TestRecoveryFlow {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static final class Fixture implements RecoveryFlow.Driver {
        final List<String> calls=new ArrayList<>();boolean current=true,busy,needRoot,root=true,web=true,remote=true;
        String cancelAt="",failAt="";
        void call(String name)throws Exception {calls.add(name);if(cancelAt.equals(name))current=false;if(failAt.equals(name))throw new Exception("fixture failure");}
        public boolean current(){return current;}public boolean rootBusy(){return busy;}
        public void checkRoot()throws Exception{call("root");}public boolean rootNeeded(){return needRoot;}public boolean rootAvailable(){return root;}
        public boolean restoreWeb()throws Exception{call("web");return web;}public boolean restoreRemote()throws Exception{call("remote");return remote;}
        public void maintainAdb(){calls.add("adb");if(cancelAt.equals("adb"))current=false;}
    }
    public static void main(String[] args)throws Exception {
        Fixture f=new Fixture();check(RecoveryFlow.restore(f)==RecoveryFlow.Result.READY);check(f.calls.equals(Arrays.asList("root","web","remote","adb")));
        f=new Fixture();f.current=false;check(RecoveryFlow.restore(f)==RecoveryFlow.Result.CANCELLED&&f.calls.isEmpty());
        f=new Fixture();f.busy=true;check(RecoveryFlow.restore(f)==RecoveryFlow.Result.BUSY&&f.calls.isEmpty());
        for(String phase:Arrays.asList("root","web","remote","adb")){f=new Fixture();f.cancelAt=phase;check(RecoveryFlow.restore(f)==RecoveryFlow.Result.CANCELLED);check(f.calls.get(f.calls.size()-1).equals(phase));}
        f=new Fixture();f.web=false;check(RecoveryFlow.restore(f)==RecoveryFlow.Result.WEB_WAIT&&f.calls.equals(Arrays.asList("root","web")));
        f=new Fixture();f.remote=false;check(RecoveryFlow.restore(f)==RecoveryFlow.Result.ADB_WAIT&&f.calls.equals(Arrays.asList("root","web","remote")));
        f=new Fixture();f.root=false;check(RecoveryFlow.restore(f)==RecoveryFlow.Result.READY);
        f=new Fixture();f.needRoot=true;f.root=false;check(RecoveryFlow.restore(f)==RecoveryFlow.Result.ROOT_WAIT);
        for(String phase:Arrays.asList("root","web","remote")){f=new Fixture();f.failAt=phase;boolean threw=false;try{RecoveryFlow.restore(f);}catch(Exception expected){threw=true;}check(threw&&f.calls.get(f.calls.size()-1).equals(phase));}
        System.out.println("PASS "+checks+" production recovery order checks: cancellation after every phase, no service work during Root commands, fail-stop sequencing and independent Root retry. Android calls are fixture drivers.");
    }
}
