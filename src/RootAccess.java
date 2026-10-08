package com.e02.rootconsole;
import android.content.*;
import java.io.*;
/** One authorization state for foreground pages and background services. */
public final class RootAccess {
 private static final RootState state=new RootState();
 private static volatile String secret="";private static volatile long checkedAt;
 private static boolean refreshing,revoking,checking;private static int rootCommands;
 public static void init(Context c){if(!secret.isEmpty())return;try{byte[] bytes=new byte[32];try(DataInputStream in=new DataInputStream(new FileInputStream(new File(c.getFilesDir(),"bridge-token")))){in.readFully(bytes);if(in.read()!=-1)return;}String value=new String(bytes,"US-ASCII");if(value.matches("[0-9a-f]{32}"))secret=value;}catch(IOException ignored){}}
 public static synchronized boolean busy(){return rootCommands>0||checking||revoking;}
 public static boolean available(){return state.available();}
 public static boolean bridge(){return state.bridge();}
 private static boolean valid(CommandRunner.Result r){return r.exit==0&&!r.timedOut&&r.error.isEmpty()&&r.stdout.matches("(?s).*\\buid=0\\(root\\).*");}
 public static CommandRunner.Result check(Context c,boolean manual){
  init(c);long ticket;boolean knownSu;
  synchronized(RootAccess.class){if(revoking||checking||rootCommands>0){CommandRunner.Result r=new CommandRunner.Result();r.error=revoking?"正在结束提权":rootCommands>0?"已有 Root 命令正在执行，请稍后检测":"正在检测 Root，请稍后重试";return r;}checking=true;checkedAt=System.currentTimeMillis();knownSu=state.available()&&!state.bridge();ticket=state.begin();}
  CommandRunner runner=new BridgeRunner(secret,false);CommandRunner.Result result=runner.execute("id",true,2);runner.close();boolean viaBridge=valid(result);
  if(!viaBridge&&(manual||knownSu)){runner=new CommandRunner("/system/bin/sh","su");result=runner.execute("id",true,manual?45:2);runner.close();}
  synchronized(RootAccess.class){checking=false;if(!revoking&&state.publish(ticket,valid(result),viaBridge)){checkedAt=System.currentTimeMillis();c.getSharedPreferences("connection",0).edit().putBoolean("bridgeMode",viaBridge||!valid(result)).apply();}}
  return result;
 }
 public static synchronized void refresh(Context c){if(refreshing||revoking||checking||rootCommands>0||System.currentTimeMillis()-checkedAt<15000)return;refreshing=true;Context app=c.getApplicationContext();new Thread(()->{try{check(app,false);}finally{synchronized(RootAccess.class){refreshing=false;}}},"root-status").start();}
 public static synchronized void invalidate(){state.clear();checkedAt=System.currentTimeMillis();}
 public static synchronized void beginRevoke(){revoking=true;invalidate();}
 public static synchronized void finishRevoke(){revoking=false;checkedAt=System.currentTimeMillis();}
 /** Silent operations never attempt to acquire a new su grant. */
 public static CommandRunner runner(Context c){init(c);return new CommandRunner("/system/bin/sh","/system/bin/sh"){
  private volatile CommandRunner rootRunner;private volatile boolean closed;private final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
  private Result failure(String message){Result r=new Result();r.error=message;return r;}
  @Override public Result execute(String command,boolean root,int seconds){if(closed)return failure("命令连接已关闭");if(!busy.compareAndSet(false,true))return failure("已有命令正在执行，请稍后重试");try{if(!root)return super.execute(command,false,seconds);boolean viaBridge;synchronized(RootAccess.class){if(!available()||revoking)return failure("Root 授权不可用，请重新授权");viaBridge=bridge();rootCommands++;}CommandRunner selected=viaBridge?new BridgeRunner(secret,false):new CommandRunner("/system/bin/sh","su");rootRunner=selected;try{if(closed)return failure("命令连接已关闭");return selected.execute(command,true,seconds);}finally{selected.close();rootRunner=null;synchronized(RootAccess.class){rootCommands--;}}}finally{busy.set(false);}}
  @Override public void close(){closed=true;CommandRunner active=rootRunner;if(active!=null)active.close();super.close();}
 };}
}
