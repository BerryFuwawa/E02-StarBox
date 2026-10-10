package com.e02.rootconsole;

import java.util.*;

/** Executes the same preparation sequence as both Android startup buttons. */
public final class TestRemoteStartFlow {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static final class Driver implements RemoteStartFlow.Driver {
        final RemoteStartGate gate=new RemoteStartGate();final long ticket=gate.begin();
        final List<String> calls=new ArrayList<>();
        boolean root,adb,web,enableWorks=true,saveWorks=true;String cancelAfter="";
        private void called(String name){calls.add(name);if(cancelAfter.equals(name))gate.cancel();}
        public boolean current(){return gate.current(ticket);}
        public void checkRoot(){called("root");}
        public boolean rootAvailable(){return root;}
        public boolean adbAvailable(){return adb;}
        public void enableAdb(){called("enableAdb");adb=enableWorks;}
        public boolean webRunning(){return web;}
        public void startWeb(boolean withRoot){called(withRoot?"webRoot":"webPlain");web=true;}
        public void saveConfig(){called("save");if(!saveWorks)throw new IllegalStateException("write failed");}
        public void launch(){check(current());called("launch");}
    }
    private static void fails(Driver d,boolean web,boolean adb)throws Exception {
        try{RemoteStartFlow.run(d,true,web,adb);throw new AssertionError("must fail");}
        catch(IllegalStateException|IllegalArgumentException expected){check(!d.calls.contains("launch"));}
    }
    public static void main(String[] args)throws Exception {
        RemoteStartGate g=new RemoteStartGate();check(g.claimAutomatic());check(!g.claimAutomatic());
        long first=g.begin();check(first>0&&g.begin()==-1&&g.current(first));
        g.cancel();check(!g.current(first)&&!g.preparing());
        long next=g.begin();g.finish(first);check(g.current(next));g.finish(next);check(!g.preparing());
        check(!g.claimAutomatic()); // Activity recreation/page changes cannot restart a manually stopped session.
        Driver d=new Driver();check(RemoteStartFlow.run(d,true,true,false));
        check(d.calls.equals(Arrays.asList("root","webPlain","save","launch")));
        d=new Driver();d.root=true;d.web=true;check(RemoteStartFlow.run(d,true,true,false));
        check(d.calls.equals(Arrays.asList("root","save","launch"))); // Existing web authorization is unchanged.
        d=new Driver();d.adb=true;check(RemoteStartFlow.run(d,true,false,true));
        check(d.calls.equals(Arrays.asList("root","save","launch"))); // Ready ADB does not require app Root.
        d=new Driver();d.root=true;check(RemoteStartFlow.run(d,true,true,true));
        check(d.calls.equals(Arrays.asList("root","enableAdb","webRoot","save","launch")));
        d=new Driver();check(RemoteStartFlow.run(d,false,false,false));check(d.calls.equals(Arrays.asList("launch")));
        fails(new Driver(),false,false);fails(new Driver(),false,true);
        d=new Driver();d.root=true;d.enableWorks=false;fails(d,false,true);
        d=new Driver();d.saveWorks=false;fails(d,true,false);
        for(String step:new String[]{"root","enableAdb","webRoot","save"}){
            d=new Driver();d.root=true;d.cancelAfter=step;
            check(!RemoteStartFlow.run(d,true,true,true));check(!d.calls.contains("launch"));
        }
        d=new Driver();d.gate.cancel();check(!RemoteStartFlow.run(d,false,true,true)&&d.calls.isEmpty());
        System.out.println("PASS "+checks+" shared remote start checks: one automatic attempt, duplicate suppression, manual stop cancellation, selected channels, existing permissions, imports untouched and save failure. No real Android or FRP server.");
    }
}
