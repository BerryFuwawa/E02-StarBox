package com.e02.rootconsole;

import android.content.Context;
import java.io.*;
import java.util.*;

/** Shared actual authorization. First acquisition needs consent; checks never change system ADB. */
public final class RootAccess {
    private static final RootState state=new RootState();
    private static final Set<CommandRunner> clients=new HashSet<>();
    private static final Set<Integer> knownPorts=new HashSet<>();
    private static volatile String secret="",message="尚未获得 Root 授权";
    private static volatile long checkedAt;
    private static RootIdentity identity;
    private static RootAcquisition.Mode mode=RootAcquisition.Mode.UNAVAILABLE;
    private static boolean refreshing,revoking,checking,authorizing;
    private static int rootCommands,launchAttempts;
    public static synchronized void init(Context context) {
        if(!secret.isEmpty())return;
        try {
            byte[] bytes=new byte[32];
            try(DataInputStream input=new DataInputStream(new FileInputStream(new File(context.getFilesDir(),"bridge-token")))) {
                input.readFully(bytes);if(input.read()!=-1)return;
            }
            String value=new String(bytes,"US-ASCII");if(value.matches("[0-9a-f]{32}"))secret=value;
        } catch(IOException ignored) {}
    }
    public static synchronized boolean busy() { return rootCommands>0||checking||revoking||authorizing; }
    public static boolean available() { return state.available(); }
    public static boolean bridge() { return true; }
    public static synchronized boolean needsConsent() { return mode==RootAcquisition.Mode.CONSENT_REQUIRED; }
    public static String message() { return message; }
    private static boolean valid(CommandRunner.Result result) {
        return result.exit==0&&!result.timedOut&&result.error.isEmpty()&&result.stdout.matches("(?s).*\\buid=0\\(root\\).*");
    }
    private static CommandRunner.Result failure(String value) { CommandRunner.Result result=new CommandRunner.Result();result.error=value;return result; }
    public static CommandRunner.Result check(Context context,boolean manual) { return check(context,manual,false); }
    public static CommandRunner.Result enableIndependent(Context context) {
        synchronized(RootAccess.class) {
            if(busy())return failure("正在处理授权，请稍后重试");
            if(!RuntimeSettings.approveRoot(context))return failure("无法保存独立授权设置，请重新打开星匣后重试");
            try {RootPermit.grant(context.getFilesDir());}catch(IOException error){return failure("无法保存授权许可，请检查存储后重试");}
            authorizing=true;
        }
        try {return check(context,true,true);}finally{synchronized(RootAccess.class){authorizing=false;}}
    }
    private static CommandRunner.Result check(Context context,boolean manual,boolean migrate) {
        final Context app=context.getApplicationContext();init(app);final long ticket;
        synchronized(RootAccess.class) {
            if(revoking||checking||rootCommands>0||authorizing&&!migrate)return failure(revoking?"正在结束提权":rootCommands>0?"已有 Root 命令正在执行，请稍后检测":"正在检测 Root，请稍后重试");
            if(!RuntimeSettings.runningAllowed(app))return failure("星匣当前未运行，请重新打开");
            if(secret.isEmpty())return failure("授权信息异常，请重新生成授权命令");
            authorizing=false;checking=true;checkedAt=System.currentTimeMillis();ticket=state.begin();if(manual)launchAttempts=0;
        }
        RootAcquisition.Result result;
        try {
            result=RootAcquisition.check(new RootAcquisition.Driver() {
                private File directory() { return app.getFilesDir(); }
                public boolean current() { synchronized(RootAccess.class){return !revoking&&state.current(ticket)&&RuntimeSettings.runningAllowed(app);} }
                public boolean automaticAllowed() { synchronized(RootAccess.class){try{return current()&&RuntimeSettings.automaticRootAllowed(app)&&!RootPermit.userEnded(directory())&&launchAttempts<3;}catch(IOException unreadable){return false;}} }
                public RootPermit.Ticket permit() throws IOException { return RootPermit.active(directory()); }
                public boolean permissionFileExists() { return RootPermit.file(directory()).exists(); }
                public RootIdentity identity(RootPermit.Ticket lease) throws IOException { return BridgeRunner.identity(secret,lease.nonce); }
                public boolean legacyAuthorized() { return valid(legacyRead()); }
                public ReadOnlyAdbProbe.Result backend() { return ReadOnlyAdbProbe.probe(5555); }
                public RootPermit.Ticket grant() throws IOException {
                    synchronized(RootAccess.class) { return automaticAllowed()?RootPermit.grant(directory()):null; }
                }
                public boolean migrateLegacy(RootPermit.Ticket lease) {
                    synchronized(RootAccess.class) {
                        if(!current()||!RootPermit.matches(lease)||!RuntimeSettings.automaticRootAllowed(app))return false;
                        // This fixed authenticated operation targets only the app's older service.
                        BridgeRunner.stop(secret);
                    }
                    long until=System.nanoTime()+2500000000L;
                    do {
                        if(!current()||!RootPermit.matches(lease))return false;
                        if(!valid(legacyRead()))return true;
                        try {Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
                    } while(System.nanoTime()<until);
                    return false;
                }
                public RootBootstrap.Result launch(RootPermit.Ticket lease) {
                    synchronized(RootAccess.class) {
                        if(!automaticAllowed()||!RootPermit.matches(lease))return new RootBootstrap.Result(false,"Root 授权已结束",null);
                        launchAttempts++;
                    }
                    return RootBootstrap.acquire(new File(app.getApplicationInfo().sourceDir),new File(directory(),"bridge-token"),lease,secret);
                }
                public void cancel(RootPermit.Ticket lease) {
                    try {RootPermit.revokeIfCurrent(lease);}catch(IOException ignored) {}
                    BridgeRunner.stopManaged(secret,lease.nonce);
                }
            },migrate);
        } catch(RuntimeException error) { result=new RootAcquisition.Result(RootAcquisition.Mode.UNAVAILABLE,"授权检测未完成，请稍后重试",null); }
        synchronized(RootAccess.class) {
            checking=false;
            if(!revoking&&state.publish(ticket,result.available(),true)) {
                identity=result.identity;mode=result.mode;message=result.message;checkedAt=System.currentTimeMillis();
                if(result.mode==RootAcquisition.Mode.OWNED)knownPorts.add(8876);
                if(!result.available()&&launchAttempts>=3&&RuntimeSettings.automaticRootAllowed(app))message="自动授权暂未成功，请点击重新检测 Root 重试";
                app.getSharedPreferences("connection",0).edit().putBoolean("bridgeMode",true).apply();
            }
        }
        CommandRunner.Result report=new CommandRunner.Result();report.exit=result.available()?0:1;
        report.stdout=result.message+(result.identity==null?"":"\nuid="+result.identity.uid+" pid="+result.identity.pid);
        return report;
    }
    private static CommandRunner.Result legacyRead() {
        BridgeRunner runner=new BridgeRunner(secret,false);try{CommandRunner.Result result=runner.execute("id",true,2);if(runner.authenticated())synchronized(RootAccess.class){knownPorts.add(runner.connectedPort());}return result;}finally{runner.close();}
    }
    public static synchronized void refresh(Context context) {
        if(refreshing||busy()||System.currentTimeMillis()-checkedAt<15000)return;
        refreshing=true;Context app=context.getApplicationContext();
        new Thread(()->{try{check(app,false);}finally{synchronized(RootAccess.class){refreshing=false;}}},"root-status").start();
    }
    public static synchronized void resetRecovery() { launchAttempts=0;checkedAt=0; }
    public static synchronized RootPermit.Ticket fallbackPermit(Context context)throws IOException {
        if(busy()||!RuntimeSettings.runningAllowed(context))throw new IOException("正在处理授权，请稍后重试");
        RootPermit.Ticket lease=RootPermit.active(context.getFilesDir());
        if(lease!=null)return lease;
        lease=RootPermit.grant(context.getFilesDir());invalidate();return lease;
    }
    public static synchronized void invalidate() { state.clear();identity=null;mode=RootAcquisition.Mode.UNAVAILABLE;checkedAt=System.currentTimeMillis(); }
    public static synchronized void beginRevoke() {
        revoking=true;invalidate();message="正在结束提权";
        for(CommandRunner client:new ArrayList<>(clients))client.close();
    }
    public static synchronized void finishRevoke() { revoking=false;checkedAt=System.currentTimeMillis(); }
    /** The caller has already closed the in-memory gate. Revocation excludes late children. */
    public static CommandRunner.Result stop(Context context,boolean endConsent) {
        init(context);boolean saved=!endConsent||RuntimeSettings.endRoot(context);RootPermit.Ticket lease=null;boolean revoked=true;
        try {lease=RootPermit.active(context.getFilesDir());}catch(IOException ignored) {}
        if(lease!=null)try {BridgeRunner.identity(secret,lease.nonce);synchronized(RootAccess.class){knownPorts.add(8876);}}catch(IOException ignored){}
        legacyRead();
        // Ask for the cleanup result before revocation makes the service close its listener.
        BridgeRunner.StopResult stopped=lease==null?BridgeRunner.StopResult.UNAVAILABLE:BridgeRunner.stopManagedResult(secret,lease.nonce,8876);
        try {if(endConsent||RootPermit.userEnded(context.getFilesDir()))RootPermit.revokeByUser(context.getFilesDir());else RootPermit.revoke(context.getFilesDir());}catch(IOException error){revoked=false;}
        // The compatibility stop is an explicit end/exit action, never a silent detector.
        BridgeRunner.stop(secret);
        Set<Integer> endpoints;synchronized(RootAccess.class){endpoints=new HashSet<>(knownPorts);}
        long deadline=System.nanoTime()+3000000000L;boolean alive;
        do {alive=false;for(int port:endpoints)if(RootBootstrap.listening(port))alive=true;if(!alive)break;try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}while(System.nanoTime()<deadline);
        synchronized(RootAccess.class) { state.clear();identity=null;mode=RootAcquisition.Mode.UNAVAILABLE;message=stopped==BridgeRunner.StopResult.INCOMPLETE?"提权已结束，但命令后台进程清理未完成，请检查":alive||!revoked?"结束提权未完成，请重试":"提权已结束"; }
        if(alive||!revoked||stopped==BridgeRunner.StopResult.INCOMPLETE)return failure(message);
        if(!saved)return failure("提权已停止，但授权设置保存失败，请检查存储后重新打开星匣");
        synchronized(RootAccess.class){knownPorts.clear();}
        CommandRunner.Result result=new CommandRunner.Result();result.exit=0;result.stdout="提权已结束";return result;
    }
    /** A root command uses a verified current service, with a cancellable client registry. */
    public static CommandRunner runner(Context context) {
        return runner(context,"/system/bin/sh");
    }
    /** Desktop integration fixture injects only the ordinary command shell; Root stays on the fixed protocol. */
    static CommandRunner runner(Context context,String normalShell) {
        return runner(context,normalShell,null);
    }
    static CommandRunner updateRunner(Context context,String operation)throws IOException {
        ManagedUpdate.command(context.getApplicationInfo().sourceDir,operation);
        return runner(context,"/system/bin/sh",operation);
    }
    private static CommandRunner runner(Context context,String normalShell,String updateOperation) {
        init(context);Context app=context.getApplicationContext();
        return new CommandRunner(normalShell,normalShell) {
            private volatile CommandRunner rootRunner;private volatile boolean closed;
            private final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
            @Override public Result execute(String command,boolean root,int seconds) {
                if(closed)return failure("命令连接已关闭");if(!busy.compareAndSet(false,true))return failure("已有命令正在执行，请稍后重试");
                try {
                    if(!root)return super.execute(command,false,seconds);
                    RootIdentity owned;long epoch;CommandRunner selected;
                    synchronized(RootAccess.class) {
                        if(!available()||revoking||checking||authorizing||!RuntimeSettings.runningAllowed(app))return failure("Root 授权不可用，请重新授权");
                        owned=identity;
                        try{selected=updateOperation==null?new BridgeRunner(secret,false):new UpdateBridgeRunner(secret,updateOperation,app.getApplicationInfo().sourceDir,owned==null);}
                        catch(IOException invalid){return failure("无法准备更新任务，请重新打开星匣后重试");}
                        epoch=state.begin();rootCommands++;clients.add(selected);rootRunner=selected;
                    }
                    try {
                        if(owned!=null) {
                            RootPermit.Ticket lease=RootPermit.active(app.getFilesDir());
                            if(lease==null||!owned.generation.equals(lease.nonce))throw new IOException("Owned authorization ended");
                            BridgeRunner.identity(secret,owned.generation);
                        }
                        synchronized(RootAccess.class) {if(closed||revoking||!state.current(epoch)||!RuntimeSettings.runningAllowed(app))return failure("Root 授权已结束");}
                        return selected.execute(command,true,seconds);
                    } catch(IOException unavailable) {
                        synchronized(RootAccess.class){if(state.current(epoch))invalidate();}
                        return failure("Root 授权已失效，请重新检测或授权");
                    } finally {
                        selected.close();rootRunner=null;
                        synchronized(RootAccess.class) {rootCommands--;clients.remove(selected);}
                    }
                } finally {busy.set(false);}
            }
            @Override public void close() {closed=true;CommandRunner active=rootRunner;if(active!=null)active.close();super.close();}
        };
    }
}
