package com.e02.rootconsole;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.Process;

/** Owns exactly one unprivileged frpc child, stopped together with this service. */
public class RemoteService extends Service {
 public static volatile boolean running,connected;
 public static volatile String status="未启动",log="";
 private static volatile RemoteService owner;
 private final Object processLock=new Object();
 private volatile Process process;
 private volatile boolean stopping;
 public static void start(Context c){if(!RiskNotice.accepted(c))throw new IllegalStateException("请先阅读并同意使用提示");c.startForegroundService(new Intent(c,RemoteService.class));}
 public static void stop(Context c){status="已停止";c.stopService(new Intent(c,RemoteService.class));}
 private void record(){if(owner!=this)return;try{File folder=new File(getFilesDir(),"frpc");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,"runtime.log"))){out.write((status+"\n"+log).getBytes("UTF-8"));}}catch(IOException ignored){}}
 private synchronized void append(String s){if(owner!=this)return;String clean=s.replaceAll("\u001B\\[[;\\d]*m","");log+=clean+"\n";if(log.length()>16000)log=log.substring(log.length()-16000);if(clean.contains("login to server success")){connected=true;status="已连接服务器，网页和 ADB 是否可用请查看日志";}if(clean.contains("connect to server error")||clean.contains("connection closed")||clean.contains("heartbeat timeout")){connected=false;status="正在重连，请查看日志";}record();}
 public void onCreate(){super.onCreate();owner=this;NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("starbox-remote","星匣远程连接",NotificationManager.IMPORTANCE_LOW));startForeground(22,new Notification.Builder(this,"starbox-remote").setSmallIcon(getApplicationInfo().icon).setContentTitle("E02星匣 · 远程").setContentText("远程连接正在运行，点击查看日志").setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT)).addAction(0,"停止远程",PendingIntent.getService(this,0,new Intent(this,RemoteService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT)).build());}
 private Process launch(File executable,File folder,String... args)throws IOException{synchronized(processLock){if(stopping)throw new IOException("已停止");java.util.List<String> command=new java.util.ArrayList<>();command.add(executable.getAbsolutePath());java.util.Collections.addAll(command,args);process=new ProcessBuilder(command).directory(folder).redirectErrorStream(true).start();return process;}}
 public int onStartCommand(Intent intent,int flags,int id){
  if(!RiskNotice.accepted(this)){stopSelf();return START_NOT_STICKY;}
  if(intent!=null&&"stop".equals(intent.getAction())){status="已停止";stopSelf();return START_NOT_STICKY;}
  if(running)return START_NOT_STICKY;
  running=true;connected=false;stopping=false;status="正在检查配置并启动远程连接…";log="";
  startForegroundService(new Intent(this,ConsoleService.class));
  new Thread(()->{
   try{
    String config=getSharedPreferences("remote",0).getString("config","");if(config.trim().isEmpty())throw new IOException("请先保存远程配置");
    File folder=new File(getFilesDir(),"frpc");if(!folder.exists()&&!folder.mkdirs())throw new IOException("无法创建远程连接所需目录");
    String arch=null;for(String abi:Build.SUPPORTED_ABIS){if(abi.equals("arm64-v8a")){arch="arm64";break;}if(abi.startsWith("armeabi"))arch="arm";}
    if(arch==null)throw new IOException("当前设备不支持内置远程连接组件");
    File executable=new File(folder,"frpc");
    try(InputStream in=getAssets().open("frpc/"+arch+"/frpc");FileOutputStream out=new FileOutputStream(executable)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(stopping)return;out.write(b,0,n);}}
    if(!executable.setExecutable(true,true))throw new IOException("无法启动远程连接组件，请检查应用权限");
    String type=getSharedPreferences("remote",0).getString("format","toml");if(!type.matches("toml|yaml|json"))throw new IOException("配置格式不支持");
    config=RemoteDns.adapt(this,config,type);
    File file=new File(folder,"frpc."+type);try(FileOutputStream out=new FileOutputStream(file)){out.write(config.getBytes("UTF-8"));}
    Process verifier=launch(executable,folder,"verify","-c",file.getAbsolutePath());read(verifier);if(verifier.waitFor()!=0)throw new IOException("远程配置检查失败，详情见日志");
    if(stopping)return;
    status="远程连接已启动，正在连接服务器…";record();
    if(ConsoleService.server!=null)ConsoleService.server.setRemoteOrigin(getSharedPreferences("remote",0).getString("origin","http://127.0.0.1:8875"));
    Process client=launch(executable,folder,"-c",file.getAbsolutePath());read(client);int code=client.waitFor();if(!stopping&&owner==this)status="远程连接已停止，错误代码 "+code+"，请查看日志";
   }catch(Exception e){if(!stopping&&owner==this){log+="\n"+e.getClass().getSimpleName()+": "+e.getMessage();status="远程启动失败："+UserMessages.explain(e);}}
   finally{if(owner==this){running=false;connected=false;record();if(ConsoleService.server!=null)ConsoleService.server.setRemoteAuthority(null);}stopForeground(true);stopSelf();}
  },"starbox-frpc").start();return START_NOT_STICKY;
 }
 private void read(Process p)throws IOException{try(BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream(),"UTF-8"))){String line;while((line=reader.readLine())!=null)append(line);}}
 public void onDestroy(){synchronized(processLock){stopping=true;if(process!=null)process.destroy();}if(owner==this){running=false;connected=false;record();if(ConsoleService.server!=null)ConsoleService.server.setRemoteAuthority(null);}super.onDestroy();}
 public IBinder onBind(Intent intent){return null;}
}
