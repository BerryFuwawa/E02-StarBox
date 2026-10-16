package com.e02.rootconsole;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.e02.rootconsole.ReadOnlyAdbProbe.*;

/** Exercise the real wire client against peers; never connects to vehicle ADB. */
public final class TestLocalAdbRootRequest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
    static LocalAdbRootRequest.Result peer(String mode)throws Exception {
        return peer(mode,"127.0.0.1");
    }
    static LocalAdbRootRequest.Result peer(String mode,String host)throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName(host))) {
            ExecutorService executor=Executors.newSingleThreadExecutor();
            AtomicInteger allowedCalls=new AtomicInteger();
            Future<?> future=executor.submit(()->{
                try(Socket socket=server.accept()) {
                    socket.setSoTimeout(3000);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
                    Packet hello=read(in);if(hello.command!=CNXN)throw new AssertionError("Handshake required");
                    if(mode.equals("auth")){send(out,AUTH,1,0,new byte[20]);if(in.read()!=-1)throw new AssertionError("Must not authorize");return;}
                    send(out,CNXN,VERSION,MAX_PAYLOAD,text("device::test\0"));
                    if(mode.equals("cancel")){if(in.read()!=-1)throw new AssertionError("Must not request after cancellation");return;}
                    Packet open=read(in);if(open.command!=OPEN||!java.util.Arrays.equals(open.payload,text("root:\0")))throw new AssertionError("Only fixed root service permitted");
                    if(mode.equals("stream")){send(out,OKAY,9,99,new byte[0]);return;}
                    send(out,OKAY,9,LOCAL,new byte[0]);
                    if(mode.equals("oversize")){send(out,WRTE,9,LOCAL,new byte[1025]);return;}
                    if(mode.equals("stall")){Thread.sleep(2400);return;}
                    String reply=mode.equals("deny")?"adbd cannot run as root in production builds\n":mode.equals("already")?"adbd is already running as root\n":"restarting adbd as root\n";
                    send(out,WRTE,9,LOCAL,text(reply));Packet ack=read(in);if(ack.command!=OKAY)throw new AssertionError("Missing stream acknowledgement");
                    send(out,CLSE,9,LOCAL,new byte[0]);if(read(in).command!=CLSE)throw new AssertionError("Missing close acknowledgement");
                }catch(Exception e){throw new RuntimeException(e);}
            });
            try {
                LocalAdbRootRequest.Result result=LocalAdbRootRequest.request(server.getLocalPort(),host,()->!mode.equals("cancel")||allowedCalls.incrementAndGet()==1);
                future.get(5,TimeUnit.SECONDS);return result;
            } finally {executor.shutdownNow();}
        }
    }
    public static void main(String[] args)throws Exception {
        check(LocalAdbRootRequest.request(1,()->false)==LocalAdbRootRequest.Result.CANCELLED);
        check(peer("auth")==LocalAdbRootRequest.Result.AUTH_REQUIRED);
        check(peer("cancel")==LocalAdbRootRequest.Result.CANCELLED);
        check(peer("restart")==LocalAdbRootRequest.Result.REQUESTED);
        check(peer("already")==LocalAdbRootRequest.Result.REQUESTED);
        check(peer("deny")==LocalAdbRootRequest.Result.DENIED);
        check(peer("stream")==LocalAdbRootRequest.Result.DENIED);
        check(peer("oversize")==LocalAdbRootRequest.Result.DENIED);
        long started=System.nanoTime();check(peer("stall")==LocalAdbRootRequest.Result.UNCONFIRMED);
        check(System.nanoTime()-started<5000000000L);
        check(peer("already","::1")==LocalAdbRootRequest.Result.REQUESTED);
        check(peer("auth","::1")==LocalAdbRootRequest.Result.AUTH_REQUIRED);
        check(peer("cancel","::1")==LocalAdbRootRequest.Result.CANCELLED);
        System.out.println("PASS "+checks+" fixed native root wire checks; replies do not establish UID.");
    }
}
