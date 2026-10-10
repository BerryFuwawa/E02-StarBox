package com.e02.rootconsole;

import android.content.*;
import android.content.pm.ApplicationInfo;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.e02.rootconsole.ReadOnlyAdbProbe.*;

/** Production RootAccess/RuntimeSettings and real protocol/file/child cancellation; Android context and UID are fixtures. */
public final class TestRootAccessIntegration {
    private static final String SECRET="0123456789abcdef0123456789abcdef";
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class App extends Context implements SharedPreferences,SharedPreferences.Editor {
        final File directory;final ApplicationInfo info=new ApplicationInfo();
        App(File directory){this.directory=directory;info.sourceDir=new File(directory,"fixture-base.apk").getAbsolutePath();}
        public File getFilesDir(){return directory;}
        public ApplicationInfo getApplicationInfo(){return info;}
        public SharedPreferences getSharedPreferences(String name,int mode){return this;}
        public int getInt(String key,int fallback){return key.equals("acceptedVersion")?1:fallback;}
        public Editor edit(){return this;}public Editor putBoolean(String key,boolean value){return this;}public void apply(){}
    }
    static final class Backend implements AutoCloseable {
        final ServerSocket listener;final Thread thread;final AtomicReference<Throwable> failure=new AtomicReference<>();
        Backend(int uid)throws IOException {
            listener=new ServerSocket(5555,1,InetAddress.getByName("127.0.0.1"));listener.setSoTimeout(3000);
            thread=new Thread(()->{try(Socket socket=listener.accept()){
                socket.setSoTimeout(3000);Packet hello=read(socket.getInputStream());check(hello.command==CNXN,"fixture CNXN");OutputStream out=socket.getOutputStream();
                send(out,CNXN,VERSION,MAX_PAYLOAD,text("device::fixture\0"));Packet open=read(socket.getInputStream());
                check(open.command==OPEN&&new String(open.payload,StandardCharsets.UTF_8).equals("shell:id -u\0"),"unapproved check sends only fixed identity");
                send(out,OKAY,7,LOCAL,new byte[0]);send(out,WRTE,7,LOCAL,text(uid+"\n"));check(read(socket.getInputStream()).command==OKAY,"fixture ack");
                send(out,CLSE,7,LOCAL,new byte[0]);read(socket.getInputStream());
            }catch(Throwable error){failure.set(error);}},"fixture-local-adb");thread.start();
        }
        public void close()throws Exception{listener.close();thread.join(3500);check(!thread.isAlive(),"fixture backend closed");if(failure.get()!=null)throw new AssertionError(failure.get());}
    }
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("starbox-root-adapter-");String shell="C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe";
        RootBridgeServer service=null;Thread serviceThread=null;CommandRunner proxy=null;
        try {
            // Never stop or replace a listener belonging to another task.
            for(int port:new int[]{8876,38766})check(!RootBootstrap.listening(port),"fixture port must already be free: "+port);
            Files.write(directory.resolve("bridge-token"),SECRET.getBytes(StandardCharsets.US_ASCII));
            Files.write(directory.resolve("fixture-base.apk"),new byte[]{0});
            App app=new App(directory.toFile());check(RuntimeSettings.open(app),"real persisted UI open gate");
            try(Backend backend=new Backend(0)){RootAccess.check(app,false);}
            check(!RootAccess.available()&&RootAccess.needsConsent(),"real adapter separates Root backend from owned service");
            check(RootPermit.active(directory.toFile())==null&&!RuntimeSettings.snapshot(app).rootAllowed,"first check grants neither consent nor lease");
            RootPermit.Ticket lease=RootPermit.grant(directory.toFile());CommandRunner childRunner=new CommandRunner(shell,shell);
            service=new RootBridgeServer(SECRET,0,12345,lease.nonce,childRunner,()->RootPermit.matches(lease));
            RootBridgeServer instance=service;AtomicReference<Throwable> failure=new AtomicReference<>();
            serviceThread=new Thread(()->{try{instance.serve(8876);}catch(Throwable error){failure.set(error);}},"fixture-owned-root");serviceThread.start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);boolean ready=false;
            while(System.nanoTime()<deadline){try{BridgeRunner.identity(SECRET,lease.nonce);ready=true;break;}catch(IOException waiting){Thread.sleep(15);}}
            check(ready,"fixture HMAC service listening");RootAccess.check(app,false);
            check(RootAccess.available()&&!RootAccess.needsConsent(),"production adapter accepts only matching owned identity");
            String source=app.info.sourceDir;app.info.sourceDir="/data/app/com.e02.rootconsole-fixture/base.apk";
            CommandRunner updater=RootAccess.updateRunner(app,"recover");try{check(updater.execute("not a shell command",true,1).error.contains("不支持更新"),"actual update adapter uses fixed update protocol and rejects missing capability");}finally{updater.close();app.info.sourceDir=source;}
            check(!RootAccess.busy()&&RootAccess.available(),"failed update capability check releases actual registry without revoking Root");
            proxy=RootAccess.runner(app,shell);CommandRunner.Result root=proxy.execute("Write-Output 'owned-fixture'",true,5);
            check(root.exit==0&&root.stdout.contains("owned-fixture"),"production root proxy uses fixture protocol");
            CommandRunner sameProxy=proxy;AtomicReference<CommandRunner.Result> blocked=new AtomicReference<>();
            Thread request=new Thread(()->blocked.set(sameProxy.execute("Start-Sleep -Seconds 30",true,45)),"fixture-active-root-request");request.start();
            Field processField=CommandRunner.class.getDeclaredField("current");processField.setAccessible(true);
            Process child=null;deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(System.nanoTime()<deadline&&(child=(Process)processField.get(childRunner))==null)Thread.sleep(10);
            check(child!=null&&child.isAlive(),"real Windows fixture child running");
            RootAccess.beginRevoke();CommandRunner.Result stopped=RootAccess.stop(app,true);RootAccess.finishRevoke();
            check(stopped.exit==0&&stopped.error.isEmpty(),"production stop adapter completes its fixture path");
            request.join(3500);serviceThread.join(3500);
            check(!request.isAlive()&&!serviceThread.isAlive()&&!child.isAlive(),"fixture client/service/direct child stopped");
            check(failure.get()==null,"fixture service completed without hidden exception");
            check(!RootAccess.available()&&RootPermit.userEnded(directory.toFile())&&!RuntimeSettings.snapshot(app).rootAllowed,"actual end writes both consent and explicit end gate");
            CommandRunner.Result ordinary=proxy.execute("Write-Output 'ordinary-still-works'",false,5);
            check(ordinary.exit==0&&ordinary.stdout.contains("ordinary-still-works"),"ending Root does not close ordinary shared command proxy");
            check(!proxy.execute("Write-Output 'denied'",true,2).error.isEmpty(),"ended Root commands remain denied");
            // Simulate retained older consent: explicit user end still wins on the real adapter.
            check(RuntimeSettings.approveRoot(app),"fixture retained consent approved without launching anything");
            try(Backend backend=new Backend(0)){RootAccess.check(app,false);}
            check(!RootAccess.available()&&RootPermit.userEnded(directory.toFile())&&RootPermit.active(directory.toFile())==null,"persisted end is not silently renewed by background check");
            check(RuntimeSettings.requestExit(app).has(RuntimePolicy.Effect.STOP_ALL_OWNED),"real all-off exit gate persisted");
            byte[] before=Files.readAllBytes(RootPermit.file(directory.toFile()).toPath());
            check(!RootAccess.check(app,false).error.isEmpty()&&!RootAccess.available(),"exited app refuses root acquisition");
            check(java.util.Arrays.equals(before,Files.readAllBytes(RootPermit.file(directory.toFile()).toPath())),"exit check does not alter the permission file");
            System.out.println("PASS "+checks+" production adapter checks: fixture context/UID, real local protocol/storage, first consent, owned identity, direct child stop, ordinary command continuity, explicit end vs stale consent, exit gate. No vehicle or real Root acquisition.");
        } finally {
            if(proxy!=null)proxy.close();if(service!=null)service.close();if(serviceThread!=null)serviceThread.join(3500);
            try(java.util.stream.Stream<Path> files=Files.list(directory)){for(Path path:(Iterable<Path>)files::iterator)Files.delete(path);}
            Files.delete(directory);
        }
    }
}
