package com.e02.rootconsole;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.e02.rootconsole.ReadOnlyAdbProbe.*;

/** Desktop protocol tests. UID/PID and runner are fixtures, never claims of vehicle Root. */
public final class TestIndependentRoot {
    private static final String SECRET="0123456789abcdef0123456789abcdef";
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static void rejected(Throwing action,String message)throws Exception { boolean rejected=false;try{action.run();}catch(IOException|IllegalArgumentException expected){rejected=true;}check(rejected,message); }
    interface Throwing { void run()throws Exception; }
    static final class BlockingRunner extends CommandRunner {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        volatile boolean closed;
        BlockingRunner() { super("fixture","fixture"); }
        @Override public Result execute(String command,boolean root,int seconds) {
            entered.countDown();Result result=new Result();
            try { if(!release.await(seconds,TimeUnit.SECONDS))result.timedOut=true; }catch(InterruptedException e){Thread.currentThread().interrupt();}
            result.exit=closed?-1:0;result.stdout="fixture output";return result;
        }
        @Override public void close() { closed=true;release.countDown(); }
    }
    static final class Service implements AutoCloseable {
        final int port;final RootBridgeServer server;final Thread thread;final AtomicReference<Throwable> failure=new AtomicReference<>();
        Service(File directory,RootPermit.Ticket ticket,CommandRunner runner)throws Exception {
            try(ServerSocket reserve=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=reserve.getLocalPort();}
            server=new RootBridgeServer(SECRET,0,12345,ticket.nonce,runner,()->RootPermit.matches(ticket));
            thread=new Thread(()->{try{server.serve(port);}catch(Throwable e){failure.set(e);}},"fixture-root-server");thread.start();
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);boolean started=false;
            do { try { BridgeRunner.identityAt(SECRET,ticket.nonce,port);started=true;break; }catch(IOException e){Thread.sleep(15);} }while(System.nanoTime()<until);
            check(started,"server started");
        }
        Socket request(String candidate,String op)throws IOException {
            Socket socket=new Socket("127.0.0.1",port);socket.setSoTimeout(4000);DataOutputStream out=new DataOutputStream(socket.getOutputStream());out.writeUTF(candidate);out.writeUTF(op);out.flush();return socket;
        }
        void stopped()throws Exception { thread.join(3500);check(!thread.isAlive(),"server stopped");if(failure.get()!=null)throw new AssertionError(failure.get()); }
        @Override public void close()throws Exception { server.close();stopped(); }
    }
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("e02-root-fixture-");
        try {
            permit(directory.toFile());identity();script();probe();launchStream();server(directory.toFile());realCancellation(directory.toFile());incompleteCleanup(directory.toFile());exitPresentation();
            System.out.println("Independent Root: "+checks+" desktop checks PASS; no vehicle deployment");
        } finally {
            // Only files directly created in this test's own temp directory.
            try(java.util.stream.Stream<Path> files=Files.list(directory)){for(Path file:(Iterable<Path>)files::iterator)Files.delete(file);}
            Files.delete(directory);
        }
    }
    private static void incompleteCleanup(File directory)throws Exception {
        RootPermit.Ticket ticket=RootPermit.grant(directory);
        CommandRunner runner=new CommandRunner("fixture","fixture"){@Override boolean cleanupComplete(){return false;}};
        try(Service service=new Service(directory,ticket,runner)){
            check(BridgeRunner.stopManagedResult(SECRET,ticket.nonce,service.port)==BridgeRunner.StopResult.INCOMPLETE,"cleanup failure is not reported as a successful stop");
            service.stopped();
        }
    }
    private static void realCancellation(File directory)throws Exception {
        String shell="C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe";
        RootPermit.Ticket ticket=RootPermit.grant(directory);CommandRunner runner=new CommandRunner(shell,shell);
        Field field=CommandRunner.class.getDeclaredField("current");field.setAccessible(true);
        try(Service service=new Service(directory,ticket,runner);Socket socket=service.request(SECRET,"run")) {
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());out.writeInt(45);BridgeRunner.writeText(out,"Start-Sleep -Seconds 30");out.flush();
            Process child=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(System.nanoTime()<deadline&&(child=(Process)field.get(runner))==null)Thread.sleep(10);
            check(child!=null&&child.isAlive(),"actual desktop command child started");
            long started=System.nanoTime();
            try(Socket stop=service.request(SECRET,"stop-managed")){DataOutputStream control=new DataOutputStream(stop.getOutputStream());control.writeUTF(ticket.nonce);control.flush();check("STOPPED".equals(new DataInputStream(stop.getInputStream()).readUTF()),"actual child stop acknowledged");}
            service.stopped();check(!child.isAlive()&&TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<2500,"actual direct child terminated promptly");
        }
    }
    private static void exitPresentation() {
        for(int flags=0;flags<8;flags++) {
            boolean keep=(flags&1)!=0,boot=(flags&2)!=0,park=(flags&4)!=0;
            check(ExitPolicy.blocked(keep,boot,park)==keep,"all exit setting combinations");
            String message=ExitPolicy.message(keep,boot,park);
            check(message.contains("• 星匣后台保活")==keep&&!message.contains("• 开机自启")&&!message.contains("• 熄火后远程连接保活"),"only selected options listed");
            check(message.contains("请先在【后台】中关闭")&&!message.contains("继续退出"),"enabled settings cannot be bypassed");
        }
    }
    private static void permit(File directory)throws Exception {
        check(RootPermit.active(directory)==null,"no prior approval");
        RootPermit.Ticket first=RootPermit.grant(directory);check(RootPermit.matches(first),"grant read back");
        check(first.nonce.equals(RootPermit.active(directory).nonce),"persisted generation");
        RootPermit.revoke(directory);check(!RootPermit.matches(first)&&RootPermit.active(directory)==null,"revoke invalidates pending launch");
        RootPermit.Ticket second=RootPermit.grant(directory);check(!second.nonce.equals(first.nonce)&&!RootPermit.matches(first),"later user launch does not reuse old generation");
        check(!RootPermit.revokeIfCurrent(first)&&RootPermit.matches(second),"late failed launch cannot revoke newer approval");
        Files.write(RootPermit.file(directory).toPath(),"malformed".getBytes(StandardCharsets.US_ASCII));check(!RootPermit.matches(second),"corruption fails closed");
        rejected(()->RootPermit.active(directory),"malformed read rejected");
        Files.write(RootPermit.file(directory).toPath(),new byte[129]);check(!RootPermit.matches(second),"oversized state fails closed");
        RootPermit.revoke(directory);
        RootPermit.revokeByUser(directory);check(RootPermit.userEnded(directory)&&RootPermit.active(directory)==null,"explicit end blocks automatic renewal");
        RootPermit.Ticket renewed=RootPermit.grant(directory);check(!RootPermit.userEnded(directory)&&RootPermit.matches(renewed),"explicit new grant clears end marker");
        RootPermit.Ticket canceled=RootPermit.grant(directory);check(RootPermit.revokeIfCurrent(canceled)&&!RootPermit.matches(canceled),"failed launch revokes its lease");
        rejected(()->RootPermit.grant(new File(directory,"missing")),"storage failure reported");
    }
    private static void identity()throws Exception {
        String challenge=RootIdentity.challenge(),generation=RootIdentity.challenge();String proof=RootIdentity.proof(SECRET,challenge,0,42,generation);
        check(RootIdentity.verify(SECRET,challenge,0,42,generation,proof,generation),"valid identity");
        check(!RootIdentity.verify(SECRET,RootIdentity.challenge(),0,42,generation,proof,generation),"fresh challenge rejects replay");
        check(!RootIdentity.verify(SECRET,challenge,2000,42,generation,proof,generation),"shell not root");
        check(!RootIdentity.verify(SECRET,challenge,0,43,generation,proof,generation),"pid tampering");
        check(!RootIdentity.verify(SECRET,challenge,0,42,generation,proof,RootIdentity.challenge()),"stale generation");
        check(!RootIdentity.verify(SECRET,challenge,0,42,generation,"0",generation),"malformed proof");
        check(!RootIdentity.verify("ffffffffffffffffffffffffffffffff",challenge,0,42,generation,proof,generation),"wrong secret");
    }
    private static void script()throws Exception {
        String nonce=RootIdentity.challenge();String token="/data/user/0/com.e02.rootconsole/files/bridge-token";
        String script=RootBootstrap.script("/data/app/pkg/base.apk",token,nonce);
        check(script.startsWith("[ \"$(id -u)\" = 0 ] || exit 1;"),"launch checks actual UID again");
        check(script.contains("/system/bin/setsid /system/bin/nohup /system/bin/app_process")&&script.contains("--managed '"+nonce+"'"),"detached owned generation");
        check(!script.contains("setprop")&&!script.contains(" root:")&&!script.contains("tcpip:")&&!script.contains("su -c"),"no ADB configuration or elevation request");
        rejected(()->RootBootstrap.script("/system/app/pkg/base.apk",token,nonce),"foreign APK path");
        rejected(()->RootBootstrap.script("/data/app/../base.apk",token,nonce),"traversal");
        rejected(()->RootBootstrap.script("/data/app/pkg/base.apk\n",token,nonce),"newline");
        rejected(()->RootBootstrap.script("/data/app/pkg/base.apk","/data/user/0/foreign/files/bridge-token",nonce),"foreign token path");
        rejected(()->RootBootstrap.script("/data/app/pkg/base.apk",token,"0"),"missing generation");
        String quoted=RootBootstrap.script("/data/app/pkg'$(echo bad)/base.apk",token,nonce);
        check(quoted.contains("'\\''"),"APK shell quoting");
    }
    private static void probe()throws Exception {
        for(final int uid:new int[]{0,2000,10067}) {
            try(MockAdb peer=new MockAdb(false,""+uid+"\n",false,false)) {
                ReadOnlyAdbProbe.Result result=ReadOnlyAdbProbe.probe(peer.port());
                check(result.uid==uid,"read actual fixture UID");
                check(result.status==(uid==0?Status.ROOT_READY:uid==2000?Status.SHELL_ONLY:Status.OTHER_UID),"separate backend state");peer.done();
                check("shell:id -u\0".equals(peer.request.get()),"probe only fixed read-only identity");
            }
        }
        try(MockAdb peer=new MockAdb(true,"",false,false)) {check(ReadOnlyAdbProbe.probe(peer.port()).status==Status.AUTH_REQUIRED,"no authorization key sent");peer.done();check(peer.request.get()==null,"AUTH sends no OPEN");}
        try(MockAdb peer=new MockAdb(false,"not root\n",false,false)){check(ReadOnlyAdbProbe.probe(peer.port()).status==Status.UNAVAILABLE,"UID text must parse");peer.done();}
        try(MockAdb peer=new MockAdb(false,"0\n",true,false)){check(ReadOnlyAdbProbe.probe(peer.port()).status==Status.UNAVAILABLE,"stream identity checked");peer.done();}
        // Incomplete headers with continuing bytes must obey the absolute deadline.
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));Socket client=new Socket("127.0.0.1",listener.getLocalPort());Socket peer=listener.accept()) {
            Thread drip=new Thread(()->{try{for(int i=0;i<24;i++){peer.getOutputStream().write(0);peer.getOutputStream().flush();Thread.sleep(50);}}catch(Exception ignored){}});drip.start();
            long start=System.nanoTime();boolean expired=false;
            try{ReadOnlyAdbProbe.receive(client,start+TimeUnit.MILLISECONDS.toNanos(200));}catch(SocketTimeoutException expected){expired=true;}
            check(expired&&TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<650,"absolute packet deadline");client.close();peer.close();drip.join(1000);
        }
    }
    private static void launchStream()throws Exception {
        Method method=RootBootstrap.class.getDeclaredMethod("execute",String.class,int.class);method.setAccessible(true);
        String script=RootBootstrap.script("/data/app/pkg/base.apk","/data/user/0/com.e02.rootconsole/files/bridge-token",RootIdentity.challenge());
        try(MockAdb peer=new MockAdb(false,"E02_STARTED\n",false,false)) {
            String reply=(String)method.invoke(null,script,peer.port());check("E02_STARTED\n".equals(reply),"launch response returned");peer.done();check(("shell:"+script+"\0").equals(peer.request.get()),"exact owned launch only");
        }
        try(MockAdb peer=new MockAdb(true,"",false,false)) {boolean denied=false;try{method.invoke(null,script,peer.port());}catch(InvocationTargetException e){denied=e.getCause() instanceof IOException;}check(denied,"launch rejects AUTH");peer.done();check(peer.request.get()==null,"launch never sends AUTH key");}
        try(MockAdb peer=new MockAdb(false,"E02_STARTED\n",true,false)){boolean denied=false;try{method.invoke(null,script,peer.port());}catch(InvocationTargetException e){denied=e.getCause() instanceof IOException;}check(denied,"launch rejects wrong stream");peer.done();}
    }
    private static void server(File directory)throws Exception {
        RootPermit.Ticket ticket=RootPermit.grant(directory);BlockingRunner runner=new BlockingRunner();
        try(Service service=new Service(directory,ticket,runner)) {
            RootIdentity identity=BridgeRunner.identityAt(SECRET,ticket.nonce,service.port);check(identity.uid==0&&identity.pid==12345,"owned service proof response");
            rejected(()->BridgeRunner.identityAt("ffffffffffffffffffffffffffffffff",ticket.nonce,service.port),"wrong identity key");
            try(Socket ignored=service.request("wrong","stop")){check(ignored.getInputStream().read()==-1,"invalid token refused");}
            check(service.thread.isAlive(),"invalid stop leaves service running");
            try(Socket oldStop=service.request(SECRET,"stop")){check(oldStop.getInputStream().read()==-1,"legacy stop cannot terminate a managed generation");}
            check(BridgeRunner.identityAt(SECRET,ticket.nonce,service.port).pid==12345,"managed service survives legacy stop");
            try(Socket stale=service.request(SECRET,"stop-managed")){DataOutputStream out=new DataOutputStream(stale.getOutputStream());out.writeUTF(RootIdentity.challenge());out.flush();check(stale.getInputStream().read()==-1,"old cleanup cannot stop a newer service");}
            try(Socket run=service.request(SECRET,"run")) {
                DataOutputStream out=new DataOutputStream(run.getOutputStream());out.writeInt(45);BridgeRunner.writeText(out,"fixture long command");out.flush();check(runner.entered.await(2,TimeUnit.SECONDS),"long command started");
                check(BridgeRunner.identityAt(SECRET,ticket.nonce,service.port).pid==12345,"identity remains responsive during command");
                try(Socket busy=service.request(SECRET,"run")){DataOutputStream other=new DataOutputStream(busy.getOutputStream());other.writeInt(2);BridgeRunner.writeText(other,"second");other.flush();DataInputStream response=new DataInputStream(busy.getInputStream());response.readInt();response.readBoolean();response.readBoolean();response.readLong();BridgeRunner.readText(response);BridgeRunner.readText(response);check(BridgeRunner.readText(response).contains("已有"),"second command rejected");}
                long start=System.nanoTime();
                try(Socket stop=service.request(SECRET,"stop-managed")){DataOutputStream control=new DataOutputStream(stop.getOutputStream());control.writeUTF(ticket.nonce);control.flush();check("STOPPED".equals(new DataInputStream(stop.getInputStream()).readUTF()),"stop acknowledged");}
                service.stopped();check(runner.closed&&TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500,"stop does not wait for 45-second command");
                check(run.getInputStream().read()==-1,"command connection closed on stop");
            }
        }
        RootPermit.Ticket next=RootPermit.grant(directory);BlockingRunner nextRunner=new BlockingRunner();
        try(Service service=new Service(directory,next,nextRunner)){RootPermit.revoke(directory);service.stopped();check(nextRunner.closed,"revoked launch stops itself without callback");}
        RootPermit.Ticket old=RootPermit.grant(directory);RootPermit.revoke(directory);
        RootBootstrap.Result revoked=RootBootstrap.acquire(new File("does-not-exist"),new File(directory,"bridge-token"),old,SECRET);
        check(!revoked.ready&&revoked.message.contains("已结束"),"revoked bootstrap does not contact ADB");
    }
    static final class MockAdb implements AutoCloseable {
        final ServerSocket listener;final Thread thread;final AtomicReference<String> request=new AtomicReference<>();final AtomicReference<Throwable> failure=new AtomicReference<>();
        MockAdb(boolean auth,String output,boolean wrongStream,boolean unused)throws IOException {
            listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));listener.setSoTimeout(3000);
            thread=new Thread(()->{
                try(Socket socket=listener.accept()) {
                    socket.setSoTimeout(3000);Packet hello=read(socket.getInputStream());if(hello.command!=CNXN)throw new AssertionError("CNXN expected");OutputStream out=socket.getOutputStream();
                    if(auth){send(out,AUTH,1,0,new byte[20]);checkNoOpen(socket);return;}
                    send(out,CNXN,VERSION,MAX_PAYLOAD,text("device::fixture\0"));Packet open=read(socket.getInputStream());if(open.command!=OPEN)throw new AssertionError("OPEN expected");request.set(new String(open.payload,StandardCharsets.UTF_8));
                    send(out,OKAY,7,LOCAL,new byte[0]);send(out,WRTE,wrongStream?8:7,LOCAL,text(output));
                    if(wrongStream)return;Packet okay=read(socket.getInputStream());if(okay.command!=OKAY)throw new AssertionError("OKAY expected");send(out,CLSE,7,LOCAL,new byte[0]);read(socket.getInputStream());
                } catch(Throwable e) { failure.set(e); }
            },"fixture-adbd");thread.start();
        }
        int port(){return listener.getLocalPort();}
        void done()throws Exception {thread.join(3500);check(!thread.isAlive(),"mock ADB stopped");if(failure.get()!=null)throw new AssertionError(failure.get());}
        static void checkNoOpen(Socket socket)throws IOException {if(socket.getInputStream().read()!=-1)throw new AssertionError("AUTH must not be answered");}
        @Override public void close()throws Exception {listener.close();thread.join(3500);}
    }
}
