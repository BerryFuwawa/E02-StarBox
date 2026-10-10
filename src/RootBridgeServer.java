package com.e02.rootconsole;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;

/** Local owned service. The accept loop remains available while a command executes. */
final class RootBridgeServer implements AutoCloseable {
    private final String secret,generation;
    private final int uid,pid;
    private final CommandRunner runner;
    private final CommandRunner updateRunner;private final String updateApk;
    private final BooleanSupplier permitted;
    private final java.util.function.Supplier<String> wakeStatus;
    private volatile boolean closed;
    private volatile ServerSocket listener;
    private volatile Socket commandClient;
    private volatile Thread commandThread;
    private final Object commandLock=new Object();
    private volatile Thread updateThread;private volatile Socket updateClient;private final Object updateLock=new Object();
    RootBridgeServer(String secret,int uid,int pid,String generation,CommandRunner runner,BooleanSupplier permitted) {
        this(secret,uid,pid,generation,runner,permitted,null,null);
    }
    RootBridgeServer(String secret,int uid,int pid,String generation,CommandRunner runner,BooleanSupplier permitted,CommandRunner updateRunner,String updateApk) {
        this(secret,uid,pid,generation,runner,permitted,updateRunner,updateApk,()->"unavailable");
    }
    RootBridgeServer(String secret,int uid,int pid,String generation,CommandRunner runner,BooleanSupplier permitted,CommandRunner updateRunner,String updateApk,java.util.function.Supplier<String> wakeStatus) {
        if(!RootPermit.nonce(secret)||uid!=0||pid<=0||!RootIdentity.generation(generation))throw new IllegalArgumentException("Invalid owned service identity");
        this.secret=secret;this.uid=uid;this.pid=pid;this.generation=generation;this.runner=runner;this.permitted=permitted;
        this.updateRunner=updateRunner;this.updateApk=updateApk;
        this.wakeStatus=wakeStatus;
    }
    void serve(int port)throws IOException,InterruptedException {
        try(ServerSocket server=new ServerSocket()) {
            listener=server;
            for(int attempt=0;;attempt++) {
                if(!allowed())return;
                try { server.bind(new InetSocketAddress("127.0.0.1",port),4);break; }
                catch(BindException e) { if(attempt>=49)throw e;Thread.sleep(100); }
            }
            server.setSoTimeout(500);
            while(allowed()) {
                Socket client;
                try { client=server.accept(); }
                catch(SocketTimeoutException e) { continue; }
                catch(SocketException e) { if(closed)break;throw e; }
                handle(client);
            }
        } finally { close();awaitCommand(); }
    }
    private boolean allowed() { return !closed&&permitted.getAsBoolean(); }
    private void handle(Socket client) {
        boolean handedOff=false;
        try {
            client.setSoTimeout(700);DataInputStream in=new DataInputStream(client.getInputStream());
            String candidate=in.readUTF();
            if("identity-v1".equals(candidate)) {
                String challenge=in.readUTF();if(!RootPermit.nonce(challenge)||!allowed())return;
                DataOutputStream out=new DataOutputStream(client.getOutputStream());
                out.writeInt(uid);out.writeInt(pid);out.writeUTF(generation);out.writeUTF(RootIdentity.proof(secret,challenge,uid,pid,generation));out.flush();return;
            }
            if(!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII),candidate.getBytes(StandardCharsets.US_ASCII)))return;
            String op=in.readUTF();
            if("wake-status".equals(op)){if(!allowed())return;DataOutputStream out=new DataOutputStream(client.getOutputStream());out.writeUTF(wakeStatus.get());out.flush();return;}
            if("features".equals(op)){DataOutputStream out=new DataOutputStream(client.getOutputStream());out.writeUTF(updateRunner!=null&&updateApk!=null?"UPDATE1":"ROOT1");out.flush();return;}
            if("stop".equals(op)&&"legacy".equals(generation)||"stop-managed".equals(op)&&generation.equals(in.readUTF())) {
                close();boolean done=awaitCommand();DataOutputStream out=new DataOutputStream(client.getOutputStream());out.writeUTF(done&&runner.cleanupComplete()&&(updateRunner==null||updateRunner.cleanupComplete())?"STOPPED":"STOP_INCOMPLETE");out.flush();return;
            }
            if("update".equals(op)){
                if(!allowed()||updateRunner==null||updateApk==null){send(client,failure("当前授权不支持更新，请重新启用独立授权"));return;}
                String operation=in.readUTF();final String command=ManagedUpdate.command(updateApk,operation);final int seconds=ManagedUpdate.timeout(operation);
                synchronized(updateLock){
                    if(updateThread!=null&&updateThread.isAlive()){send(client,failure("已有更新任务正在执行"));return;}
                    if(!allowed())return;updateClient=client;
                    updateThread=new Thread(()->{try(Socket socket=client){CommandRunner.Result result=allowed()?updateRunner.execute(command,true,seconds):failure("Root 授权已结束");if(allowed())send(socket,result);}catch(Exception ignored){}finally{synchronized(updateLock){if(updateClient==client)updateClient=null;}}},"owned-update-task");
                    updateThread.setDaemon(true);updateThread.start();handedOff=true;return;
                }
            }
            if(!"run".equals(op)||!allowed())return;
            int seconds=in.readInt();if(seconds<1||seconds>45)return;
            String command=BridgeRunner.readText(in);
            synchronized(commandLock) {
                if(commandThread!=null&&commandThread.isAlive()) { send(client,failure("已有 Root 命令正在执行，请稍后重试"));return; }
                if(!allowed())return;
                commandClient=client;
                Thread worker=new Thread(()->{
                    try(Socket socket=client) {
                        CommandRunner.Result result=allowed()?runner.execute(command,true,seconds):failure("Root 授权已结束");
                        if(allowed())send(socket,result);
                    } catch(Exception e) { /* Client disconnect and cancellation are expected. Never log command content. */ }
                    finally { synchronized(commandLock) { if(commandClient==client)commandClient=null; } }
                },"owned-root-command");
                worker.setDaemon(true);commandThread=worker;worker.start();handedOff=true;
            }
        } catch(Exception e) { /* Authentication/protocol failures do not reveal contents. */ }
        finally { if(!handedOff)try{client.close();}catch(IOException ignored){} }
    }
    private static CommandRunner.Result failure(String message) { CommandRunner.Result result=new CommandRunner.Result();result.error=message;return result; }
    private static void send(Socket socket,CommandRunner.Result result)throws IOException {
        DataOutputStream out=new DataOutputStream(socket.getOutputStream());out.writeInt(result.exit);out.writeBoolean(result.timedOut);out.writeBoolean(result.truncated);out.writeLong(result.millis);
        BridgeRunner.writeText(out,result.stdout);BridgeRunner.writeText(out,result.stderr);BridgeRunner.writeText(out,result.error);out.flush();
    }
    private boolean awaitCommand() {
        Thread worker=commandThread;
        if(worker!=null&&worker!=Thread.currentThread())try{worker.join(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        Thread update=updateThread;if(update!=null&&update!=Thread.currentThread())try{update.join(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        return (worker==null||!worker.isAlive())&&(update==null||!update.isAlive());
    }
    @Override public void close() {
        closed=true;runner.close();if(updateRunner!=null)updateRunner.close();
        ServerSocket server=listener;if(server!=null)try{server.close();}catch(IOException ignored){}
        Socket client=commandClient;if(client!=null)try{client.close();}catch(IOException ignored){}
        Socket update=updateClient;if(update!=null)try{update.close();}catch(IOException ignored){}
    }
}
