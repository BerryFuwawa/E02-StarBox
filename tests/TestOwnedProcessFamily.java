package com.e02.rootconsole;

import java.io.IOException;
import java.util.*;

public final class TestOwnedProcessFamily {
    static int checks;
    static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    static OwnedProcessFamily.Member member(int pid,int parent,int sid,long born,boolean marker){return new OwnedProcessFamily.Member(pid,parent,sid,sid,0,born,marker,'S');}
    static final class Driver implements OwnedProcessFamily.Driver {
        final Map<Integer,OwnedProcessFamily.Member> live=new LinkedHashMap<>();final List<Integer> signalled=new ArrayList<>();boolean resist,late,reuse;int pauses;
        public OwnedProcessFamily.Member read(int pid){if(reuse&&pid==102){reuse=false;live.put(pid,member(pid,1,777,9999,false));}return live.get(pid);}
        public List<OwnedProcessFamily.Member> scan(){return new ArrayList<>(live.values());}
        public void signal(int pid,int signal)throws IOException{signalled.add(pid);if(!resist&&signal==9)live.remove(pid);if(resist)throw new IOException("fixture denied");}
        public void pause(){pauses++;if(late){late=false;live.put(106,member(106,1,100,201,false));}}
        OwnedProcessFamily family()throws IOException{return new OwnedProcessFamily(this,live.get(100),50);}
    }
    static Driver base(){Driver d=new Driver();d.live.put(100,member(100,50,100,200,true));return d;}
    public static void main(String[] args)throws Exception {
        Driver d=base();d.live.put(102,member(102,100,100,201,false));d.live.put(103,member(103,1,103,202,true));d.live.put(104,member(104,1,104,202,false));d.live.put(1,member(1,0,100,999,true));d.live.put(50,member(50,1,100,999,true));d.live.put(105,member(105,1,100,199,false));d.late=true;
        check(d.family().stop(),"session plus marked escaped child stopped");
        for(int pid:new int[]{100,102,103,106})check(d.signalled.contains(pid),"owned child signalled "+pid);
        for(int pid:new int[]{1,50,104,105})check(!d.signalled.contains(pid)&&d.live.containsKey(pid),"foreign or older identity untouched "+pid);
        d=base();d.live.put(102,member(102,100,100,201,false));d.reuse=true;check(d.family().stop(),"PID reuse is not old owned process");check(!d.signalled.contains(102),"reused PID never signalled");
        d=base();d.resist=true;boolean failed=false;try{d.family().stop();}catch(IOException expected){failed=true;}check(failed,"cannot signal is reported");check(d.pauses==4,"cleanup is bounded");
        d=base();d.live.remove(100);failed=false;try{d.family();}catch(IOException expected){failed=true;}check(failed,"missing anchor fails closed");
        for(OwnedProcessFamily.Member invalid:new OwnedProcessFamily.Member[]{member(1,50,1,1,true),member(100,51,100,200,true),member(100,50,101,200,true),member(100,50,100,200,false),new OwnedProcessFamily.Member(100,50,100,100,2000,200,true,'S'),member(100,50,100,0,true)}){
            failed=false;try{new OwnedProcessFamily(base(),invalid,50);}catch(IOException expected){failed=true;}check(failed,"unverified anchor rejected");
        }
        StringBuilder stat=new StringBuilder("100 (a tricky ) process) S 50 100 100");for(int i=4;i<19;i++)stat.append(" 0");stat.append(" 2345 0 0");
        OwnedProcessFamily.Member parsed=OwnedProcessFamily.parse(stat.toString(),0,true);check(parsed.pid==100&&parsed.parent==50&&parsed.group==100&&parsed.session==100&&parsed.born==2345&&parsed.state=='S',"proc stat spaces and parentheses parsed");
        for(String invalid:new String[]{"", "100 () S 1", "100 (x) NN 1 2 3", "bad (x) S"}){failed=false;try{OwnedProcessFamily.parse(invalid,0,true);}catch(IOException expected){failed=true;}check(failed,"malformed proc rejected");}
        d=base();OwnedProcessFamily f=d.family();check(f.stop()&&f.stop(),"repeated cleanup is idempotent");
        System.out.println("Owned process family: "+checks+" desktop policy checks PASS; Android signals not exercised");
    }
}
