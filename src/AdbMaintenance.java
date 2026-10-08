package com.e02.rootconsole;
import android.content.*;
import java.io.*;
import java.util.*;
/** Tracks the explicitly enabled peer and restores only Starbox-owned ADB settings. */
public final class AdbMaintenance {
 public static volatile String message="";
 private static volatile boolean busy;
 private static long last;
 private static void report(Context c,String value){if(value.equals(message))return;message=value;try{File f=new File(c.getFilesDir(),"adb-network.log");if(f.length()>32768)new FileOutputStream(f).close();try(FileOutputStream out=new FileOutputStream(f,true)){out.write((new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date())+" "+value+"\n").getBytes("UTF-8"));}}catch(IOException ignored){}}
 public static synchronized void refresh(Context context){
  if(!RiskNotice.accepted(context)||busy||RootAccess.busy()||System.currentTimeMillis()-last<10000)return;
  last=System.currentTimeMillis();Context c=context.getApplicationContext();SharedPreferences prefs=c.getSharedPreferences("connection",0);
  boolean managed=prefs.getBoolean("adbManaged",false);boolean remote=RemoteService.running&&c.getSharedPreferences("remote",0).getInt("mode",0)==0&&c.getSharedPreferences("remote",0).getBoolean("adb",false);if(!managed&&!remote)return;
  String peer=prefs.getString("computerIp","");List<NetworkState.Address> all=NetworkState.list();NetworkState.Address chosen=NetworkState.maintained(all,peer,prefs.getString("adbInterface",prefs.getString("adbNetwork","")));
  final NetworkState.Address address=chosen;busy=true;
  new Thread(()->{
   CommandRunner runner=null;
   try{
    RootAccess.init(c);if(!RootAccess.available())return;runner=RootAccess.runner(c);
    synchronized(AdbControl.LOCK){
     if(!managed){if(remote&&!LocalAdb.available()){CommandRunner.Result local=runner.execute(AdbControl.ensureLocal(),true,10);if(local.exit!=0||local.timedOut||!local.error.isEmpty())throw new IOException("ADB 恢复失败，请重新检测 Root");}return;}if(!prefs.getBoolean("adbManaged",false)||!peer.equals(prefs.getString("computerIp","")))return;
     String signature=address==null?"unresolved|"+peer:address.iface+"|"+address.ip+"|"+peer;
     boolean change=!signature.equals(prefs.getString("adbNetwork",""));
     if(!change&&address==null){CommandRunner.Result check=runner.execute(AdbControl.localStatus(),true,3);change=check.exit!=0||!check.stdout.contains("LOCAL_ADB=true");}
     if(!change&&address!=null){
      CommandRunner.Result check=runner.execute(AdbControl.status(address.ip,peer,address.iface),true,3);
      if(check.exit!=0||check.timedOut||!check.error.isEmpty()||!check.stdout.contains("LISTEN="))throw new IOException("Root 不可用，无法读取 ADB 状态");
      change=!check.stdout.contains("WIRELESS=true")||!check.stdout.contains("LISTEN=true");
      if(change)report(c,"检测到端口或星匣规则异常，正在恢复无线 ADB。");
     }
     if(!change)return;
     CommandRunner.Result result=runner.execute(address==null?AdbControl.localOnly():AdbControl.wireless(true,address.ip,peer,address.iface),true,10);
     if(result.exit!=0||result.timedOut||!result.error.isEmpty())throw new IOException("规则更新失败，请重新检测 Root");
     SharedPreferences.Editor saved=prefs.edit().putString("adbNetwork",signature);if(address!=null)saved.putString("adbInterface",address.iface);saved.apply();
     report(c,address==null?"网络已变化：局域网电脑访问已暂停，内网穿透仍可使用。":"无线 ADB 配置已更新："+address.label()+"，允许电脑 "+peer+"；仍需电脑验证实际连通性。");
    }
   }catch(Exception e){report(c,"无线 ADB 检查失败："+e.getMessage());}
   finally{if(runner!=null)runner.close();busy=false;}
  },"adb-network-refresh").start();
 }
}
