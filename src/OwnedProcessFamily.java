package com.e02.rootconsole;

import java.io.IOException;
import java.util.*;

/** Cleanup is limited to a verified new session and inherited per-command owner marker. */
final class OwnedProcessFamily {
    static final class Member {
        final int pid,parent,group,session,uid;final long born;final boolean marked;final char state;
        Member(int pid,int parent,int group,int session,int uid,long born,boolean marked,char state){this.pid=pid;this.parent=parent;this.group=group;this.session=session;this.uid=uid;this.born=born;this.marked=marked;this.state=state;}
        boolean live(){return state!='Z'&&state!='X';}
    }
    interface Driver {Member read(int pid)throws IOException;List<Member> scan()throws IOException;void signal(int pid,int signal)throws IOException;void pause()throws InterruptedException;}
    private final Driver driver;private final Member anchor;private final int owner;
    private final Map<Integer,Long> known=new HashMap<>();
    OwnedProcessFamily(Driver driver,Member anchor,int owner)throws IOException {
        if(anchor==null||owner<=1||anchor.pid<=1||anchor.pid==owner||anchor.parent!=owner||anchor.uid!=0||anchor.session!=anchor.pid||anchor.group!=anchor.pid||anchor.born<=0||!anchor.marked||!anchor.live())throw new IOException("无法核验命令进程身份");
        this.driver=driver;this.anchor=anchor;this.owner=owner;known.put(anchor.pid,anchor.born);
    }
    private boolean owned(Member m){return m!=null&&m.pid>1&&m.pid!=owner&&m.born>=anchor.born&&(m.session==anchor.pid||m.marked||Long.valueOf(m.born).equals(known.get(m.pid)));}
    private List<Member> collect()throws IOException {
        List<Member> all=driver.scan(),selected=new ArrayList<>();
        for(Member m:all)if(owned(m)&&m.live()){known.put(m.pid,m.born);selected.add(m);}
        return selected;
    }
    private void signal(List<Member> members,int signal)throws IOException {
        IOException failure=null;
        for(Member prior:members){
            Member now=driver.read(prior.pid);
            // Re-read start time immediately before signalling; never use a stale PID alone.
            if(now==null||now.born!=prior.born||!now.live()||!owned(now))continue;
            try{driver.signal(now.pid,signal);}catch(IOException error){failure=error;}
        }
        if(failure!=null)throw failure;
    }
    synchronized boolean stop()throws IOException {
        List<Member> first=collect();IOException failure=null;
        try{signal(first,15);}catch(IOException error){failure=error;}
        try{driver.pause();}catch(InterruptedException e){Thread.currentThread().interrupt();}
        // Repeat to include children created before TERM was delivered and escaped setsid children.
        for(int i=0;i<3;i++){
            List<Member> remaining=collect();if(remaining.isEmpty())return true;
            try{signal(remaining,9);}catch(IOException error){failure=error;}
            try{driver.pause();}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
        boolean stopped=collect().isEmpty();if(!stopped&&failure!=null)throw failure;return stopped;
    }
    static Member parse(String stat,int uid,boolean marked)throws IOException {
        try{
            int open=stat.indexOf('('),close=stat.lastIndexOf(')');
            if(open<1||close<=open)throw new IllegalArgumentException();
            int pid=Integer.parseInt(stat.substring(0,open).trim());String[] values=stat.substring(close+1).trim().split("\\s+");
            if(values.length<20||values[0].length()!=1)throw new IllegalArgumentException();
            return new Member(pid,Integer.parseInt(values[1]),Integer.parseInt(values[2]),Integer.parseInt(values[3]),uid,Long.parseLong(values[19]),marked,values[0].charAt(0));
        }catch(RuntimeException error){throw new IOException("无法读取命令进程身份",error);}
    }
}
