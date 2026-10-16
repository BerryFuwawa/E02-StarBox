package com.e02.rootconsole;
import java.io.*;import java.util.*;import java.util.concurrent.*;
class FixtureState{
 static volatile boolean permit=true,auto=true,exit=false,aux=true,master=true,ack=true,denyAfterRead=false;
 static volatile int live=1;static String list=AuxiliaryRepair.COMPONENT+":other.app/.Service";
 static final List<String> commands=new CopyOnWriteArrayList<>(),events=new CopyOnWriteArrayList<>();
 static void reset(){permit=true;auto=true;exit=false;aux=true;master=true;ack=true;denyAfterRead=false;live=1;list=AuxiliaryRepair.COMPONENT+":other.app/.Service";commands.clear();events.clear();}
}
class RootPermit{static class Ticket{String nonce="0123456789abcdef0123456789abcdef";}static boolean matches(Ticket p){return FixtureState.permit;}}
class RuntimePolicy{static class Snapshot{boolean accepted=true,exited=FixtureState.exit,rootAllowed=FixtureState.permit,keepAlive=true;}}
class LifecycleFileStore{LifecycleFileStore(File f){}RuntimePolicy.Snapshot load(){return new RuntimePolicy.Snapshot();}}
class WakeRecoveryOptions{static boolean available(){return true;}static Object read(File f){return new Object();}static boolean mayRestore(RuntimePolicy.Snapshot s,Object o){return !s.exited&&s.rootAllowed&&FixtureState.auto;}}
class RootServiceVersion{static String PREFIX="registered-v5:";static String hash(File f){return "fixture";}}
class AuxiliaryRepairChoice{static boolean enabled(File f){return FixtureState.aux;}}
class WakeDiagnosticLog{static void record(File d,String s,String r,long w,long e,long u){FixtureState.events.add(s+":"+r);}}
class RootIdentity{static String challenge(){return "abcdef0123456789abcdef0123456789";}}
class WakeReceipt{static boolean matches(File f,String n,String r){return FixtureState.ack;}}
class OwnedRootCommandRunner extends CommandRunner{
 OwnedRootCommandRunner(){super("unused","unused");}
 @Override public Result execute(String c,boolean root,int seconds){
  FixtureState.commands.add(c);Result r=new Result();r.exit=0;
  if(c.contains("dumpsys activity services")){r.stdout="ACTIVITY MANAGER SERVICES\n"+(FixtureState.live==0?" (nothing)\n":FixtureState.live==1?"ServiceRecord{x com.e02.rootconsole/.ConsoleService}\napp=ProcessRecord{x}\n":" unknown");if(FixtureState.denyAfterRead)FixtureState.permit=false;}
  else if(c.contains(" get secure accessibility_enabled"))r.stdout=FixtureState.master?"1":"0";
  else if(c.contains(" get secure enabled_accessibility_services"))r.stdout=FixtureState.list;
  else if(c.contains(" put secure enabled_accessibility_services"))FixtureState.list=c.substring(c.indexOf("'")+1,c.lastIndexOf("'"));
  return r;
 }
}
