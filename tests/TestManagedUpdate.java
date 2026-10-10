package com.e02.rootconsole;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real desktop TCP with fixed task/UID fixtures, never an Android installer or vehicle test. */
public final class TestManagedUpdate {
    static final String SECRET="0123456789abcdef0123456789abcdef",APK="/data/app/com.e02.rootconsole-random/base.apk";
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    interface Action{void run()throws Exception;}
    static void rejected(Action action,String why)throws Exception{boolean denied=false;try{action.run();}catch(IOException expected){denied=true;}check(denied,why);}
    static class Task extends CommandRunner {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);final AtomicInteger calls=new AtomicInteger();
        volatile String command="";volatile int seconds;volatile boolean closed;final boolean blocked;
        Task(boolean blocked){super("fixture","fixture");this.blocked=blocked;}
        public Result execute(String command,boolean root,int seconds){this.command=command;this.seconds=seconds;calls.incrementAndGet();entered.countDown();Result r=new Result();try{if(blocked&&!release.await(5,TimeUnit.SECONDS))r.error="fixture deadline";}catch(InterruptedException e){Thread.currentThread().interrupt();}r.exit=closed?-1:0;r.stdout="fixed update fixture";return r;}
        public void close(){closed=true;release.countDown();}
    }
    static final class Server implements AutoCloseable {
        final RootBridgeServer server;final Thread thread;final AtomicReference<Throwable> failure=new AtomicReference<>();final int port;final String generation=RootIdentity.challenge();
        Server(int port,Task command,CommandRunner update)throws Exception {
            if(port==0)try(ServerSocket reserve=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=reserve.getLocalPort();}
            this.port=port;server=new RootBridgeServer(SECRET,0,12345,generation,command,()->true,update,update==null?null:APK);
            thread=new Thread(()->{try{server.serve(this.port);}catch(Throwable e){failure.set(e);}},"fixture-managed-update");thread.start();
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);boolean ready=false;while(System.nanoTime()<until){try{BridgeRunner.identityAt(SECRET,generation,port);ready=true;break;}catch(IOException wait){Thread.sleep(10);}}check(ready,"fixture server ready");
        }
        Socket request(String secret,String operation)throws Exception{Socket s=new Socket("127.0.0.1",port);s.setSoTimeout(3500);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(secret);out.writeUTF(operation);out.flush();return s;}
        Socket update(String op)throws Exception{Socket s=request(SECRET,"update");DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(op);out.flush();return s;}
        public void close()throws Exception{server.close();thread.join(3500);check(!thread.isAlive()&&failure.get()==null,"fixture server stopped cleanly");}
    }
    static CommandRunner.Result result(Socket socket)throws IOException{DataInputStream in=new DataInputStream(socket.getInputStream());CommandRunner.Result r=new CommandRunner.Result();r.exit=in.readInt();r.timedOut=in.readBoolean();r.truncated=in.readBoolean();r.millis=in.readLong();r.stdout=BridgeRunner.readText(in);r.stderr=BridgeRunner.readText(in);r.error=BridgeRunner.readText(in);return r;}
    static final class OlderService implements AutoCloseable {
        final ServerSocket listener=new ServerSocket(8876,4,InetAddress.getByName("127.0.0.1"));final Thread thread;final AtomicReference<Throwable> failure=new AtomicReference<>();volatile String command="";
        OlderService(boolean hasIdentity)throws IOException {
            listener.setSoTimeout(2500);thread=new Thread(()->{try{
                for(int step=0;step<(hasIdentity?2:3);step++)try(Socket socket=listener.accept()){
                    socket.setSoTimeout(2500);DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());String first=in.readUTF(),op=in.readUTF();
                    if(step==0){if(!SECRET.equals(first)||!"features".equals(op))throw new AssertionError("features query expected");}
                    else if(step==1){if(!"identity-v1".equals(first))throw new AssertionError("identity check before old fallback");if(hasIdentity){String generation=RootIdentity.challenge();out.writeInt(0);out.writeInt(12345);out.writeUTF(generation);out.writeUTF(RootIdentity.proof(SECRET,op,0,12345,generation));out.flush();}}
                    else{if(!SECRET.equals(first)||!"run".equals(op)||in.readInt()!=10)throw new AssertionError("only fixed compatibility command expected");command=BridgeRunner.readText(in);out.writeInt(0);out.writeBoolean(false);out.writeBoolean(false);out.writeLong(1);BridgeRunner.writeText(out,"");BridgeRunner.writeText(out,"");BridgeRunner.writeText(out,"");out.flush();}
                }
            }catch(Throwable e){failure.set(e);}},"fixture-old-update-service");thread.start();
        }
        public void close()throws Exception{thread.join(3500);listener.close();check(!thread.isAlive()&&failure.get()==null,"compatibility fixture sequence completed");}
    }
    public static void main(String[] args)throws Exception {
        for(String op:new String[]{"download","install","recover"}){String command=ManagedUpdate.command(APK,op);check(command.contains(" exec /system/bin/app_process ")&&!command.endsWith("&")&&command.endsWith("'/data/user/0/com.e02.rootconsole/files/update-task/task.json'"),"fixed foreground entry: "+op);}
        check(ManagedUpdate.timeout("download")==650&&ManagedUpdate.timeout("install")==210&&ManagedUpdate.timeout("recover")==45,"separate bounded task deadlines");
        for(String op:new String[]{"run","install;id","", "DOWNLOAD"})rejected(()->ManagedUpdate.command(APK,op),"reject arbitrary operation");
        for(String path:new String[]{"/system/app/base.apk","/data/app/a/../base.apk","/data/app/a/./base.apk","/data/app/a/base.apk\n","/data/app/a/not-base.apk"})rejected(()->ManagedUpdate.command(path,"install"),"reject foreign or malformed APK");
        check(ManagedUpdate.command("/data/app/a'$(id)/base.apk","install").contains("'\\''"),"APK safely shell quoted");
        Task ordinary=new Task(false),update=new Task(true);
        try(Server server=new Server(0,ordinary,update)){
            try(Socket s=server.request(SECRET,"features")){check("UPDATE1".equals(new DataInputStream(s.getInputStream()).readUTF()),"managed update capability");}
            try(Socket s=server.request("wrong","features")){check(s.getInputStream().read()==-1,"features requires authentication");}
            try(Socket s=server.update("install; id")){check(s.getInputStream().read()==-1&&update.calls.get()==0,"invalid operation never executes");}
            Socket first=server.update("download");check(update.entered.await(2,TimeUnit.SECONDS),"managed task starts");
            check(update.command.equals(ManagedUpdate.command(APK,"download"))&&update.seconds==650,"server chooses fixed command and deadline");
            try(Socket duplicate=server.update("install")){check(result(duplicate).error.contains("已有更新"),"only one update at a time");}
            try(Socket run=server.request(SECRET,"run")){DataOutputStream out=new DataOutputStream(run.getOutputStream());out.writeInt(2);BridgeRunner.writeText(out,"ordinary fixture");out.flush();check(result(run).exit==0&&ordinary.command.equals("ordinary fixture"),"ordinary command independent of managed task");}
            try(Socket run=server.request(SECRET,"run")){DataOutputStream out=new DataOutputStream(run.getOutputStream());out.writeInt(650);out.flush();check(run.getInputStream().read()==-1&&ordinary.calls.get()==1,"long arbitrary commands remain prohibited");}
            first.close();check(update.calls.get()==1&&!update.closed,"client disconnect keeps installer owned by service");
            check(BridgeRunner.stopManagedResult(SECRET,server.generation,server.port)==BridgeRunner.StopResult.STOPPED,"explicit stop acknowledges both runners");
            check(update.closed&&ordinary.closed,"explicit stop closes update and ordinary runners");
        }
        // Execute the real update client against the actual protocol, not a stubbed client result.
        check(!RootBootstrap.listening(8876),"fixed fixture port already free");
        Task clientTask=new Task(false);
        try(Server server=new Server(8876,new Task(false),clientTask);UpdateBridgeRunner client=new UpdateBridgeRunner(SECRET,"install",APK,false)){
            CommandRunner.Result r=client.execute("ignored and never executed",true,1);check(r.exit==0&&r.error.isEmpty()&&clientTask.command.equals(ManagedUpdate.command(APK,"install"))&&clientTask.seconds==210,"actual fixed update client protocol");
            client.close();check(!client.execute("",true,1).error.isEmpty(),"closed update client cannot restart");
        }
        try(Server server=new Server(8876,new Task(false),null);UpdateBridgeRunner client=new UpdateBridgeRunner(SECRET,"install",APK,false)){
            check(client.execute("",true,1).error.contains("不支持更新"),"unsupported current server fails clearly");
        }
        Task blocked=new Task(true);
        try(Server server=new Server(8876,new Task(false),blocked);UpdateBridgeRunner client=new UpdateBridgeRunner(SECRET,"download",APK,false)){
            AtomicReference<CommandRunner.Result> reply=new AtomicReference<>();Thread call=new Thread(()->reply.set(client.execute("",true,1)));call.start();check(blocked.entered.await(2,TimeUnit.SECONDS),"actual blocking update requested");client.close();call.join(1500);check(!call.isAlive()&&reply.get()!=null&&!reply.get().error.isEmpty(),"closing client cancels its wait promptly");check(!blocked.closed,"client cancellation does not kill detached app replacement");
            check(BridgeRunner.stopManagedResult(SECRET,server.generation,server.port)==BridgeRunner.StopResult.STOPPED&&blocked.closed,"service stop still terminates managed task");
        }
        Task dirty=new Task(false){@Override boolean cleanupComplete(){return false;}};
        try(Server server=new Server(0,new Task(false),dirty)){check(BridgeRunner.stopManagedResult(SECRET,server.generation,server.port)==BridgeRunner.StopResult.INCOMPLETE,"update cleanup failure cannot be reported successful");}
        try(OlderService old=new OlderService(false);UpdateBridgeRunner client=new UpdateBridgeRunner(SECRET,"download",APK,true)){
            check(client.execute("ignored",true,1).exit==0&&old.command.endsWith(" < /dev/null &")&&old.command.contains("RootUpdateWorker download '")&&!old.command.contains(" exec "),"installed older service uses only fixed compatible launch");
        }
        try(OlderService newer=new OlderService(true);UpdateBridgeRunner client=new UpdateBridgeRunner(SECRET,"download",APK,true)){
            check(client.execute("ignored",true,1).error.contains("不支持更新")&&newer.command.isEmpty(),"any proved new service identity forbids old detached fallback");
        }
        System.out.println("PASS "+checks+" managed update checks: fixed operation/deadline/path, real TCP client, ordinary command continuity, client disconnect, explicit stop and incomplete cleanup; desktop fixtures only.");
    }
}
