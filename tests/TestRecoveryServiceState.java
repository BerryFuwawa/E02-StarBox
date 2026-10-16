package com.e02.rootconsole;
public final class TestRecoveryServiceState{
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("service parser "+checks);}
 public static void main(String[] args){
  String head="ACTIVITY MANAGER SERVICES (dumpsys activity services)\n";
  String own="  * ServiceRecord{6182f11 u0 com.e02.rootconsole/.ConsoleService}\n";
  check(RecoveryServiceState.parse(head+own+" app=ProcessRecord{ede6e33 19521:com.e02.rootconsole/u0a95}\n")==1);
  check(RecoveryServiceState.parse(head+own+" app=null\n")==0);
  check(RecoveryServiceState.parse(head+" (nothing)\n")==0);
  for(String bad:new String[]{"",null,"Error: permission denied",head,head+own,head+"ServiceRecord{x other.app/.ConsoleService}\n app=ProcessRecord{abc}"})check(RecoveryServiceState.parse(bad)==-1);
  check(RecoveryServiceState.parse(head+own+"ServiceRecord{x other.app/.Service}\napp=ProcessRecord{abc}")==-1);
  check(RecoveryServiceState.parse(head+own.replace("/.ConsoleService","/com.e02.rootconsole.ConsoleService")+"app=ProcessRecord{abc}")==1);
  System.out.println("PASS "+checks+" actual own-service output parser checks.");
 }
}
