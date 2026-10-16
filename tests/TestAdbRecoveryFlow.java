package com.e02.rootconsole;
import java.util.*;
public final class TestAdbRecoveryFlow {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static final class Device implements AdbRecoveryFlow.Driver {
        boolean current=true,opted=true,root=true,permission=true,supported=true,enable=true;
        int enabled=0,writes,requests,awaits;String cancel="";
        ReadOnlyAdbProbe.Status status=ReadOnlyAdbProbe.Status.UNAVAILABLE;
        public boolean current(){return current;}
        public boolean optedIn(){return opted;}
        public boolean rootAllowed(){return root;}
        public boolean permission(){return permission;}
        public int adbEnabled(){return enabled;}
        public boolean enableAdb(){writes++;if(cancel.equals("write"))current=false;if(enable){enabled=1;status=ReadOnlyAdbProbe.Status.SHELL_ONLY;}return enable;}
        public ReadOnlyAdbProbe.Result probe(){return new ReadOnlyAdbProbe.Result(status,status==ReadOnlyAdbProbe.Status.ROOT_READY?0:status==ReadOnlyAdbProbe.Status.SHELL_ONLY?2000:-1,"");}
        public boolean rootSupported(){return supported;}
        public void requestRoot(){requests++;if(cancel.equals("request"))current=false;status=ReadOnlyAdbProbe.Status.ROOT_READY;}
        public void awaitAdb(){awaits++;if(cancel.equals("await"))current=false;}
    }
    public static void main(String[] args) {
        for(int flags=0;flags<8;flags++){
            Device d=new Device();d.current=(flags&1)!=0;d.opted=(flags&2)!=0;d.root=(flags&4)!=0;
            AdbRecoveryFlow.prepare(d);check(d.writes==(flags==7?1:0));check(d.requests==(flags==7?1:0));
        }
        for(ReadOnlyAdbProbe.Status state:ReadOnlyAdbProbe.Status.values()){
            Device d=new Device();d.status=state;
            ReadOnlyAdbProbe.Result result=AdbRecoveryFlow.prepare(d);
            if(state==ReadOnlyAdbProbe.Status.UNAVAILABLE||state==ReadOnlyAdbProbe.Status.SHELL_ONLY)check(result.status==ReadOnlyAdbProbe.Status.ROOT_READY&&d.requests==1);
            else check(d.requests==0&&d.writes==0&&result.status==state);
        }
        Device d=new Device();d.permission=false;AdbRecoveryFlow.prepare(d);check(d.writes==0&&d.requests==0);
        d=new Device();d.enabled=1;AdbRecoveryFlow.prepare(d);check(d.writes==0&&d.requests==0);
        d=new Device();d.enable=false;AdbRecoveryFlow.prepare(d);check(d.writes==1&&d.requests==0);
        d=new Device();d.supported=false;AdbRecoveryFlow.prepare(d);check(d.writes==1&&d.requests==0);
        for(String phase:Arrays.asList("write","await","request")){
            d=new Device();d.cancel=phase;AdbRecoveryFlow.prepare(d);check(d.requests==(phase.equals("request")?1:0));
        }
        System.out.println("PASS "+checks+" native ADB recovery permission/cancellation checks. No vehicle test claim.");
    }
}
