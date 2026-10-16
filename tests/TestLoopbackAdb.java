package com.e02.rootconsole;

import java.net.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import static com.e02.rootconsole.ReadOnlyAdbProbe.*;

/** Real IPv6 loopback sockets, fixed UID wire exchange, and authorization boundaries. */
public final class TestLoopbackAdb {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static void identity(Socket socket,int uid,boolean auth)throws Exception {
        socket.setSoTimeout(2500);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
        check(read(in).command==CNXN);
        if(auth){send(out,AUTH,1,0,new byte[20]);check(in.read()==-1);return;}
        send(out,CNXN,VERSION,MAX_PAYLOAD,text("device::fixture\0"));
        Packet open=read(in);check(open.command==OPEN&&new String(open.payload,"UTF-8").equals("shell:id -u\0"));
        send(out,OKAY,9,LOCAL,new byte[0]);send(out,WRTE,9,LOCAL,text(uid+"\n"));check(read(in).command==OKAY);
        send(out,CLSE,9,LOCAL,new byte[0]);check(read(in).command==CLSE);
    }
    public static void main(String[] args)throws Exception {
        for(int uid:new int[]{0,2000,10095})try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("::1"))){
            ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> peer=pool.submit(()->{try(Socket socket=listener.accept()){identity(socket,uid,false);}catch(Exception e){throw new RuntimeException(e);}});
            try{Result result=probe(listener.getLocalPort());check(result.uid==uid&&result.host.equals("::1"));check(result.status==(uid==0?Status.ROOT_READY:uid==2000?Status.SHELL_ONLY:Status.OTHER_UID));peer.get(4,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        }
        // An explicit IPv4 AUTH response must not try another route around authorization.
        try(ServerSocket ipv6=new ServerSocket(0,1,InetAddress.getByName("::1"));ServerSocket ipv4=new ServerSocket(ipv6.getLocalPort(),1,InetAddress.getByName("127.0.0.1"))){
            ipv6.setSoTimeout(300);ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> peer=pool.submit(()->{try(Socket socket=ipv4.accept()){identity(socket,0,true);}catch(Exception e){throw new RuntimeException(e);}});
            try{Result result=probe(ipv4.getLocalPort());check(result.status==Status.AUTH_REQUIRED&&result.host.equals("127.0.0.1"));peer.get(4,TimeUnit.SECONDS);try{ipv6.accept().close();throw new AssertionError("Must not bypass explicit authorization");}catch(SocketTimeoutException expected){checks++;}}finally{pool.shutdownNow();}
        }
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("::1"))){
            ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> peer=pool.submit(()->{try(Socket socket=listener.accept()){
                socket.setSoTimeout(2500);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();check(read(in).command==CNXN);send(out,CNXN,VERSION,MAX_PAYLOAD,text("device::fixture\0"));
                Packet request=read(in);check(new String(request.payload,"UTF-8").equals("shell:echo E02_STARTED\0"));
                send(out,OKAY,11,LOCAL,new byte[0]);send(out,WRTE,11,LOCAL,text("E02_STARTED\n"));check(read(in).command==OKAY);send(out,CLSE,11,LOCAL,new byte[0]);check(read(in).command==CLSE);
            }catch(Exception e){throw new RuntimeException(e);}});
            try{Method method=RootBootstrap.class.getDeclaredMethod("execute",String.class,int.class,String.class);method.setAccessible(true);check(method.invoke(null,"echo E02_STARTED",listener.getLocalPort(),"::1").equals("E02_STARTED\n"));peer.get(4,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        }
        for(String host:new String[]{"localhost","192.168.68.196","::","127.0.0.2"}){
            try{probe(5555,host,System.nanoTime()+1000000000L);throw new AssertionError("Non-loopback accepted");}catch(IllegalArgumentException expected){checks++;}
            try{LocalAdbRootRequest.request(5555,host,()->true);throw new AssertionError("Non-loopback root request accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        System.out.println("PASS "+checks+" real loopback fallback/UID/launch-wire checks; no vehicle startup claim.");
    }
}
