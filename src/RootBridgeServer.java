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
    private final RootTransportLog.Sink trace;
    private final java.util.concurrent.atomic.AtomicLong requests=new java.util.concurrent.atomic.AtomicLong();
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
        this(secret,uid,pid,generation,runner,permitted,updateRunner,updateApk,wakeStatus,RootTransportLog.NONE);
    }
    RootBridgeServer(String secret,int uid,int pid,String generation,CommandRunner runner,BooleanSupplier permitted,CommandRunner updateRunner,String updateApk,java.util.function.Supplier<String> wakeStatus,RootTransportLog.Sink trace) {
        if(!RootPermit.nonce(secret)||uid!=0||pid<=0||!RootIdentity.generation(generation))throw new IllegalArgumentException("Invalid owned service identity");
        this.secret=secret;this.uid=uid;this.pid=pid;this.generation=generation;this.runner=runner;this.permitted=permitted;
        this.updateRunner=updateRunner;this.updateApk=updateApk;
        this.wakeStatus=wakeStatus;
        this.trace=trace==null?RootTransportLog.NONE:trace;
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
    private final class TraceRequest {
        final long id=requests.incrementAndGet(),began=System.nanoTime();
        RootTransportLog.Stage stage=RootTransportLog.Stage.ACCEPT;
        private boolean enabled;private int buffered;
        private final RootTransportLog.Stage[] stages=new RootTransportLog.Stage[4];
        private final RootTransportLog.Outcome[] outcomes=new RootTransportLog.Outcome[4];private final long[] durations=new long[4];
        void enable(){if(enabled)return;enabled=true;for(int i=0;i<buffered;i++)emit(stages[i],outcomes[i],durations[i]);buffered=0;}
        private void emit(RootTransportLog.Stage next,RootTransportLog.Outcome outcome,long ms){try{trace.record(id,next,outcome,ms);}catch(RuntimeException ignored){}}
        void at(RootTransportLog.Stage next,RootTransportLog.Outcome outcome){stage=next;long ms=(System.nanoTime()-began)/1000000;if(enabled)emit(next,outcome,ms);else if(buffered<4){stages[buffered]=next;outcomes[buffered]=outcome;durations[buffered++]=ms;}}
        void response(Socket client,CommandRunner.Result result)throws IOException{stage=RootTransportLog.Stage.REPLY;send(client,result);at(RootTransportLog.Stage.REPLY,RootTransportLog.Outcome.COMPLETE);}
        void executeResult(CommandRunner.Result result){at(RootTransportLog.Stage.EXECUTE,result.timedOut?RootTransportLog.Outcome.TIMEOUT:result.error.isEmpty()?RootTransportLog.Outcome.COMPLETE:RootTransportLog.Outcome.FAILED);}
        void failed(Exception error){enable();at(stage,error instanceof SocketTimeoutException?RootTransportLog.Outcome.TIMEOUT:error instanceof EOFException?RootTransportLog.Outcome.INCOMPLETE:error instanceof IOException?RootTransportLog.Outcome.IO_FAILED:RootTransportLog.Outcome.FAILED);}
    }
    private void handle(Socket client) {
        boolean handedOff=false;
        TraceRequest request=new TraceRequest();request.at(RootTransportLog.Stage.ACCEPT,RootTransportLog.Outcome.COMPLETE);
        try {
            request.stage=RootTransportLog.Stage.AUTH;
            client.setSoTimeout(700);DataInputStream in=new DataInputStream(client.getInputStream());
            String candidate=in.readUTF();
            if("identity-v1".equals(candidate)) {
                String challenge=in.readUTF();if(!RootPermit.nonce(challenge)||!allowed())return;
                DataOutputStream out=new DataOutputStream(client.getOutputStream());
                out.writeInt(uid);out.writeInt(pid);out.writeUTF(generation);out.writeUTF(RootIdentity.proof(secret,challenge,uid,pid,generation));out.flush();return;
            }
            if(!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII),candidate.getBytes(StandardCharsets.US_ASCII))){request.enable();request.at(RootTransportLog.Stage.AUTH,RootTransportLog.Outcome.DENIED);return;}
            request.at(RootTransportLog.Stage.AUTH,RootTransportLog.Outcome.COMPLETE);request.stage=RootTransportLog.Stage.REQUEST;
            String op=in.readUTF();
            if("wake-status".equals(op)){if(!allowed())return;DataOutputStream out=new DataOutputStream(client.getOutputStream());out.writeUTF(wakeStatus.get());out.flush();return;}
            if("features".equals(op)){DataOutputStream out=new DataOutputStream(client.getOutputStream());out.writeUTF(updateRunner!=null&&updateApk!=null?"UPDATE1":"ROOT1");out.flush();return;}
            if("stop".equals(op)&&"legacy".equals(generation)||"stop-managed".equals(op)&&generation.equals(in.readUTF())) {
                close();boolean done=awaitCommand();DataOutputStream out=new DataOutputStream(client.getOutputStream());out.writeUTF(done&&runner.cleanupComplete()&&(updateRunner==null||updateRunner.cleanupComplete())?"STOPPED":"STOP_INCOMPLETE");out.flush();return;
            }
            if("update".equals(op)){
                request.enable();
                if(!allowed()||updateRunner==null||updateApk==null){send(client,failure("当前授权不支持更新，请重新启用独立授权"));return;}
                String operation=in.readUTF();final String command=ManagedUpdate.command(updateApk,operation);final int seconds=ManagedUpdate.timeout(operation);
                synchronized(updateLock){
                    if(updateThread!=null&&updateThread.isAlive()){send(client,failure("已有更新任务正在执行"));return;}
                    if(!allowed())return;updateClient=client;
                    request.at(RootTransportLog.Stage.REQUEST,RootTransportLog.Outcome.COMPLETE);
                    updateThread=new Thread(()->{try(Socket socket=client){request.at(RootTransportLog.Stage.EXECUTE,RootTransportLog.Outcome.STARTED);CommandRunner.Result result=allowed()?updateRunner.execute(command,true,seconds):failure("Root 授权已结束");request.executeResult(result);if(allowed())request.response(socket,result);else request.at(RootTransportLog.Stage.REPLY,RootTransportLog.Outcome.CANCELLED);}catch(Exception error){request.failed(error);}finally{synchronized(updateLock){if(updateClient==client)updateClient=null;}}},"owned-update-task");
                    updateThread.setDaemon(true);updateThread.start();handedOff=true;return;
                }
            }
            request.enable();
            if(!"run".equals(op)||!allowed()){request.at(RootTransportLog.Stage.REQUEST,RootTransportLog.Outcome.DENIED);return;}
            int seconds=in.readInt();if(seconds<1||seconds>45){request.at(RootTransportLog.Stage.REQUEST,RootTransportLog.Outcome.INVALID);return;}
            String command=BridgeRunner.readText(in);
            request.at(RootTransportLog.Stage.REQUEST,RootTransportLog.Outcome.COMPLETE);
            synchronized(commandLock) {
                if(commandThread!=null&&commandThread.isAlive()) { request.at(RootTransportLog.Stage.EXECUTE,RootTransportLog.Outcome.BUSY);send(client,failure("已有 Root 命令正在执行，请稍后重试"));return; }
                if(!allowed()){request.at(RootTransportLog.Stage.EXECUTE,RootTransportLog.Outcome.CANCELLED);return;}
                commandClient=client;
                Thread worker=new Thread(()->{
                    try(Socket socket=client) {
                        request.at(RootTransportLog.Stage.EXECUTE,RootTransportLog.Outcome.STARTED);
                        CommandRunner.Result result=allowed()?runner.execute(command,true,seconds):failure("Root 授权已结束");
                        request.executeResult(result);
                        if(allowed())request.response(socket,result);
                        else request.at(RootTransportLog.Stage.REPLY,RootTransportLog.Outcome.CANCELLED);
                    } catch(Exception e) {request.failed(e);}
                    finally { synchronized(commandLock) { if(commandClient==client)commandClient=null; } }
                },"owned-root-command");
                worker.setDaemon(true);commandThread=worker;worker.start();handedOff=true;
            }
        } catch(Exception e) {request.failed(e);}
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
