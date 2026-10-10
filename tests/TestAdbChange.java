package com.e02.rootconsole;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class TestAdbChange {
 private static int checks;
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("ADB transaction "+checks);}
 private static CommandRunner.Result result(String text){CommandRunner.Result r=new CommandRunner.Result();r.exit=0;r.stdout=text;return r;}
 private static final class Runner extends CommandRunner {
  final CommandRunner.Result change,reading;int calls;
  Runner(CommandRunner.Result a,CommandRunner.Result b){super("unused","unused");change=a;reading=b;}
  public Result execute(String command,boolean root,int seconds){if(!root)throw new AssertionError("Root required");return calls++==0?change:reading;}
 }
 private static void rejected(CommandRunner.Result change,CommandRunner.Result read,boolean wireless,boolean on)throws Exception {
  AtomicBoolean committed=new AtomicBoolean();Runner runner=new Runner(change,read);boolean rejected=false;
  try{AdbChange.apply(runner,"change","read",wireless,on,()->committed.set(true));}catch(IOException expected){rejected=true;}
  check(rejected&&!committed.get());
 }
 public static void main(String[] args)throws Exception {
  CommandRunner.Result closed=result("LISTEN=true\nWIFI_ENABLED=false\nWIRELESS=false\nWIRED=true\n");
  Runner runner=new Runner(result(""),closed);AtomicBoolean managed=new AtomicBoolean(true);
  AdbStatus state=AdbChange.apply(runner,"change","read",true,false,()->{check(Thread.holdsLock(AdbControl.LOCK));managed.set(false);});
  check(!managed.get()&&state.wirelessState()==0&&state.listen==1&&runner.calls==2);
  AdbChange.apply(new Runner(result(""),result("LISTEN=true\nWIFI_ENABLED=true\nWIRELESS=true\n")),"change","read",true,true,()->managed.set(true));check(managed.get());
  AdbChange.apply(new Runner(result(""),closed),"change","read",false,true,()->{});check(true);
  rejected(result(""),closed,true,true);
  rejected(result(""),result("LISTEN=true\nWIFI_ENABLED=true\nWIRELESS=false\n"),true,true);
  rejected(result(""),result("LISTEN=unknown\nWIFI_ENABLED=unknown\n"),true,false);
  rejected(result(""),closed,false,false);
  CommandRunner.Result failure=result("");failure.exit=1;failure.stderr="denied";rejected(failure,closed,true,false);
  CommandRunner.Result timeout=result("");timeout.timedOut=true;rejected(timeout,closed,true,false);rejected(result(""),timeout,true,false);
  CommandRunner.Result bad=result("");bad.error="disconnected";rejected(result(""),bad,true,false);
  boolean saveFailed=false;try{AdbChange.apply(new Runner(result(""),closed),"change","read",true,false,()->{throw new IOException("disk full");});}catch(IOException expected){saveFailed=true;}check(saveFailed);
  // Maintenance arrives between the shell operation and intent persistence.
  for(int i=0;i<30;i++){
   AtomicBoolean wanted=new AtomicBoolean(true),restored=new AtomicBoolean();CountDownLatch applied=new CountDownLatch(1),observerReady=new CountDownLatch(1);
   AtomicReference<Throwable> error=new AtomicReference<>();
   Thread maintenance=new Thread(()->{try{if(!applied.await(2,TimeUnit.SECONDS))throw new AssertionError("manual operation absent");observerReady.countDown();synchronized(AdbControl.LOCK){if(wanted.get())restored.set(true);}}catch(Throwable e){error.set(e);}});
   maintenance.start();AdbChange.apply(new Runner(result(""),closed),"change","read",true,false,()->{
    applied.countDown();try{if(!observerReady.await(2,TimeUnit.SECONDS))throw new IOException("maintenance absent");}catch(InterruptedException e){throw new IOException(e);}wanted.set(false);
   });maintenance.join(2500);check(!maintenance.isAlive()&&error.get()==null&&!restored.get()&&!wanted.get());
  }
  System.out.println("ADB transaction: "+checks+" verified state, failures and maintenance race checks PASS");
 }
}
