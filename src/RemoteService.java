package com.e02.rootconsole;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.lang.Process;

/** Exactly one unprivileged frpc child; manual stop always wins over recovery. */
public class RemoteService extends Service {
    public static volatile boolean running,connected;
    public static volatile String status="未启动",log="";
    private static volatile RemoteService owner;
    private static final Object CLIENT_LOCK=new Object();
    private static boolean manuallyStopped;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile FrpcSupervisor session;
    private volatile boolean destroyed;
    private boolean restartPending;
    private int latestStartId;
    private String networkKey="";
    private ConnectivityManager manager;
    private ConnectivityManager.NetworkCallback callback;

    public static synchronized void start(Context context){
        if(!RuntimeSettings.runningAllowed(context))throw new IllegalStateException("星匣已退出，请重新打开");
        if(!RiskNotice.accepted(context))throw new IllegalStateException("请先阅读并同意使用提示");
        if(!context.getSharedPreferences("remote",0).edit().putBoolean("resumeRequested",true).commit())throw new IllegalStateException("无法保存远程连接设置，请检查存储");
        manuallyStopped=false;context.startForegroundService(new Intent(context,RemoteService.class));
    }
    public static synchronized void stop(Context context){
        RemoteStartController.gate.cancel();
        manuallyStopped=true;
        boolean saved=context.getSharedPreferences("remote",0).edit().putBoolean("resumeRequested",false).commit();
        RemoteService current=owner;if(current!=null)current.stopChild();
        status=saved?"已停止":"已停止，但设置未保存，请重新打开后检查";
        context.stopService(new Intent(context,RemoteService.class));
    }
    static synchronized void startPrepared(Context context,long ticket){
        if(RemoteStartController.gate.current(ticket)&&RuntimeSettings.runningAllowed(context))start(context);
    }
    static synchronized boolean restore(Context context)throws IOException {
        if(manuallyStopped||running||!context.getSharedPreferences("remote",0).getBoolean("resumeRequested",false))return true;
        if(!RuntimeSettings.runningAllowed(context))return false;
        SharedPreferences prefs=context.getSharedPreferences("remote",0);
        if(prefs.getInt("mode",0)==0){
            if(prefs.getBoolean("console",true)&&ConsoleService.server==null)return false;
            if(prefs.getBoolean("adb",false)&&!LocalAdb.available()){
                if(!RootAccess.available())return false;
                CommandRunner runner=RootAccess.runner(context);CommandRunner.Result result;
                try{synchronized(AdbControl.LOCK){result=runner.execute(AdbControl.ensureLocal(),true,10);}}finally{runner.close();}
                if(result.exit!=0||result.timedOut||!result.error.isEmpty()||!LocalAdb.available())return false;
            }
            String config=RemoteConfig.client(prefs.getString("host",""),RemoteConfig.port(prefs.getString("port","7000")),prefs.getString("token",""),prefs.getString("secret",""),"127.0.0.1",ConsoleService.webPort(),prefs.getBoolean("console",true),prefs.getBoolean("adb",false),prefs.getString("user",""));
            if(!prefs.edit().putString("config",config).putString("format","toml").commit())throw new IOException("无法保存远程配置");
        }
        context.startForegroundService(new Intent(context,RemoteService.class));return true;
    }
    private void stopChild(){restartPending=false;FrpcSupervisor current=session;if(current!=null)current.stop();}
    private boolean current(FrpcSupervisor task){return owner==this&&!destroyed&&session==task&&!task.stopped();}
    private void record(){
        if(owner!=this)return;
        try{File folder=new File(getFilesDir(),"frpc");folder.mkdirs();
            try(FileOutputStream output=new FileOutputStream(new File(folder,"runtime.log"))){output.write((status+"\n"+log).getBytes("UTF-8"));}
        }catch(IOException ignored){}
    }
    private synchronized void append(FrpcSupervisor task,String line){
        if(!current(task))return;
        String clean=line.replaceAll("\u001B\\[[;\\d]*m","");
        log+=clean+"\n";if(log.length()>16000)log=log.substring(log.length()-16000);
        if(clean.contains("login to server success")){connected=true;status="已连接服务器，通道是否可用请查看日志";}
        if(clean.contains("connect to server error")||clean.contains("connection closed")||clean.contains("heartbeat timeout")){connected=false;status="正在重连，请查看日志";}
        record();
    }
    @Override public void onCreate(){
        super.onCreate();owner=this;
        NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel("starbox-remote","星匣远程连接",NotificationManager.IMPORTANCE_LOW));
        startForeground(22,new Notification.Builder(this,"starbox-remote").setSmallIcon(getApplicationInfo().icon).setContentTitle("E02星匣 · 远程").setContentText("远程连接正在运行，点击查看日志").setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT)).addAction(0,"停止远程",PendingIntent.getService(this,0,new Intent(this,RemoteService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT)).build());
        manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);networkKey=defaultNetworkKey();
        callback=new ConnectivityManager.NetworkCallback(){public void onAvailable(Network network){networkChanged();}public void onLost(Network network){networkChanged();}public void onLinkPropertiesChanged(Network network,LinkProperties properties){networkChanged();}};
        try{if(manager!=null)manager.registerDefaultNetworkCallback(callback);}catch(RuntimeException denied){callback=null;}
    }
    private String defaultNetworkKey(){
        if(manager==null)return "none";
        Network network=manager.getActiveNetwork();LinkProperties properties=network==null?null:manager.getLinkProperties(network);
        return String.valueOf(network)+"|"+(properties==null?"":properties.getDnsServers().toString()+properties.getInterfaceName());
    }
    private synchronized void networkChanged(){
        if(destroyed)return;String key=defaultNetworkKey();if(key.equals(networkKey))return;networkKey=key;
        FrpcSupervisor task=session;if(task==null||task.stopped())return;
        connected=false;status="网络已变化，正在重新连接…";task.networkChanged();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        latestStartId=id;
        if(intent!=null&&"stop".equals(intent.getAction())){stop(this);return START_NOT_STICKY;}
        if(!wanted()){stopChild();stopSelfResult(id);return START_NOT_STICKY;}
        if(session!=null){if(session.stopped())restartPending=true;return START_NOT_STICKY;}
        begin();return START_NOT_STICKY;
    }
    private boolean wanted(){return !destroyed&&!manuallyStopped&&RiskNotice.accepted(this)&&RuntimeSettings.runningAllowed(this)&&getSharedPreferences("remote",0).getBoolean("resumeRequested",false);}
    private void begin(){
        FrpcSupervisor task=new FrpcSupervisor();session=task;running=true;connected=false;restartPending=false;
        status="正在检查配置并启动远程连接…";log=callback==null?"网络变化检测暂不可用，连接仍会尝试重连\n":"";
        startForegroundService(new Intent(this,ConsoleService.class));
        new Thread(()->runClient(task),"starbox-frpc").start();
    }
    private void runClient(FrpcSupervisor task){
        try{synchronized(CLIENT_LOCK){
            if(!current(task))return;
            String original=getSharedPreferences("remote",0).getString("config","");
            if(original.trim().isEmpty())throw new IOException("请先保存远程配置");
            File folder=new File(getFilesDir(),"frpc");if(!folder.exists()&&!folder.mkdirs())throw new IOException("无法创建远程连接所需目录");
            String arch=null;for(String abi:Build.SUPPORTED_ABIS){if(abi.equals("arm64-v8a")){arch="arm64";break;}if(abi.startsWith("armeabi"))arch="arm";}
            if(arch==null)throw new IOException("当前设备不支持内置远程连接组件");
            File executable=new File(folder,"frpc");
            try(InputStream input=getAssets().open("frpc/"+arch+"/frpc");FileOutputStream output=new FileOutputStream(executable)){
                byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1){if(!current(task))return;output.write(buffer,0,count);}
            }
            if(!executable.setExecutable(true,true))throw new IOException("无法启动远程连接组件，请检查应用权限");
            String type=getSharedPreferences("remote",0).getString("format","toml");
            if(!type.matches("toml|yaml|json"))throw new IOException("配置格式不支持");
            File file=new File(folder,"frpc."+type);
            task.run(new FrpcSupervisor.Driver(){
                public void prepare()throws IOException {
                    String config=RemoteDns.adapt(RemoteService.this,original,type);
                    try(FileOutputStream output=new FileOutputStream(file)){output.write(config.getBytes("UTF-8"));}
                }
                public Process launch(boolean verify)throws IOException {
                    java.util.List<String> command=new java.util.ArrayList<>();command.add(executable.getAbsolutePath());
                    if(verify)command.add("verify");command.add("-c");command.add(file.getAbsolutePath());
                    return new ProcessBuilder(command).directory(folder).redirectErrorStream(true).start();
                }
                public void line(String line){append(task,line);}
                public void connecting(){
                    if(!current(task))return;connected=false;status="远程连接已启动，正在连接服务器…";record();
                    if(ConsoleService.server!=null)ConsoleService.server.setRemoteOrigin(getSharedPreferences("remote",0).getString("origin","http://127.0.0.1:8875"));
                }
                public void retry(int exit,long delayMillis){
                    if(!current(task))return;connected=false;
                    status="远程连接中断，"+(delayMillis/1000)+"秒后重试";
                    append(task,"远程连接已停止（"+exit+"），"+(delayMillis/1000)+"秒后重试");
                }
            });
        }}catch(Exception failure){
            if(current(task)){connected=false;status="远程启动失败："+UserMessages.explain(failure);log+="\n"+UserMessages.explain(failure);record();}
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
        }finally{task.stop();main.post(()->finished(task));}
    }
    private void finished(FrpcSupervisor task){
        if(owner!=this||destroyed||session!=task)return;
        session=null;running=false;connected=false;record();
        if(restartPending&&wanted()){begin();return;}
        if(ConsoleService.server!=null)ConsoleService.server.setRemoteAuthority(null);
        stopSelfResult(latestStartId);
    }
    @Override public void onDestroy(){
        destroyed=true;stopChild();
        if(manager!=null&&callback!=null)try{manager.unregisterNetworkCallback(callback);}catch(RuntimeException ignored){}
        if(owner==this){running=false;connected=false;if(ConsoleService.server!=null)ConsoleService.server.setRemoteAuthority(null);owner=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
