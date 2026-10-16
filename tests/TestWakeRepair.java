package com.e02.rootconsole;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class TestWakeRepair {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("wake repair check "+checks);}
 static final String EVCC="com.kooo.evcc/com.kooo.evcc.lifecycle.KeepAliveAccessibilityService",OTHER="example.app/.Service";
 static class Settings implements AuxiliaryRepair.Driver {
  String list=EVCC+":"+OTHER,master="1";boolean allowed=true,failWrite,changeList,denyBeforeWrite;int writes,reads;
  public boolean allowed(){return allowed;}
  public String readMaster(){return master;}
  public String readList(){reads++;if(denyBeforeWrite&&reads==2)allowed=false;if(changeList)list=reads%2==0?EVCC:OTHER;return list;}
  public void writeList(String next)throws IOException {writes++;if(failWrite)throw new IOException("fixture refused");list=next;}
 }
 public static void main(String[] args)throws Exception {
  Path home=Files.createTempDirectory("e02-wake-repair-");try{
   check(!AuxiliaryRepairChoice.enabled(home.toFile())); // Old preferences alone do not authorize repair.
   check(AuxiliaryRepairChoice.write(home.toFile(),true)&&AuxiliaryRepairChoice.enabled(home.toFile()));
   check(AuxiliaryRepairChoice.write(home.toFile(),false)&&!AuxiliaryRepairChoice.enabled(home.toFile()));
   Files.write(home.resolve("auxiliary-repair-choice"),"invalid".getBytes(StandardCharsets.US_ASCII));
   try{AuxiliaryRepairChoice.enabled(home.toFile());throw new AssertionError("bad choice accepted");}catch(IOException expected){checks++;}
   Settings settings=new Settings();check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.REPAIRED);check(settings.list.equals(EVCC+":"+OTHER+":"+AuxiliaryRepair.COMPONENT));check(settings.writes==1);
   check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.PRESENT&&settings.writes==1);
   settings=new Settings();settings.list=AuxiliaryRepair.SHORT+":"+EVCC;check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.PRESENT&&settings.writes==0);
   settings=new Settings();settings.allowed=false;check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.DENIED&&settings.writes==0);
   settings=new Settings();settings.master="0";check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.MASTER_OFF&&settings.writes==0&&settings.master.equals("0"));
   settings=new Settings();settings.denyBeforeWrite=true;check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.DENIED&&settings.writes==0);
   settings=new Settings();settings.changeList=true;check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.CONFLICT&&settings.writes==0);
   settings=new Settings();settings.failWrite=true;check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.FAILED);
   for(String invalid:new String[]{"x;reboot","bad/component:bad","x/y'","x/y\nz/a",":x/y","x/y:","x/y::z/a"}){
    settings=new Settings();settings.list=invalid;check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.FAILED&&settings.writes==0);
   }
   settings=new Settings();settings.list="null";check(AuxiliaryRepair.restore(settings)==AuxiliaryRepair.Result.REPAIRED&&settings.list.equals(AuxiliaryRepair.COMPONENT));
   File apk=home.resolve("candidate.apk").toFile();Files.write(apk.toPath(),new byte[]{1,2,3});String hash=RootServiceVersion.hash(apk);check(hash.matches("[0-9a-f]{64}"));
   check(RootServiceVersion.matches(RootServiceVersion.PREFIX+hash,apk));check(!RootServiceVersion.matches("registered-v2",apk));
   File old=home.resolve("old.apk").toFile();Files.write(old.toPath(),new byte[]{4});check(!RootServiceVersion.matches(RootServiceVersion.PREFIX+RootServiceVersion.hash(old),apk));
   long now=1700000000000L;Path log=home.resolve("wake-observer-events");
   Files.write(log,((now-WakeDiagnosticLog.RETENTION-1)+" 0 0 old old\n"+(now+1)+" 0 0 future future\n").getBytes(StandardCharsets.US_ASCII));
   WakeDiagnosticLog.record(home.toFile(),"signal","android.intent.action.SCREEN_ON",now,10,5);String text=new String(Files.readAllBytes(log),StandardCharsets.US_ASCII);
   check(!text.contains("old")&&!text.contains("future")&&text.contains("SCREEN_ON"));
   check(WakeDiagnosticLog.read(home.toFile(),now).contains("收到车辆自然唤醒通知"));check(!WakeDiagnosticLog.read(home.toFile(),now).contains("SCREEN_ON"));check(WakeDiagnosticLog.read(home.toFile(),now+WakeDiagnosticLog.RETENTION+1).isEmpty());
   byte[] before=Files.readAllBytes(log);WakeDiagnosticLog.record(home.toFile(),"signal","TOKEN=secret command text",now,10,5);check(Arrays.equals(before,Files.readAllBytes(log)));
   StringBuilder many=new StringBuilder();for(int i=0;i<1000;i++)many.append(now).append(" 1 1 signal android.intent.action.SCREEN_ON\n");
   Files.write(log,many.toString().getBytes(StandardCharsets.US_ASCII));WakeDiagnosticLog.record(home.toFile(),"service","receipt-confirmed",now,11,6);
   check(Files.size(log)<=WakeDiagnosticLog.LIMIT);check(new String(Files.readAllBytes(log),StandardCharsets.US_ASCII).endsWith("service receipt-confirmed\n"));
   try(java.util.stream.Stream<Path> children=Files.list(home)){check(children.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
   WakeRestoreController controller=new WakeRestoreController();final long[] clock={0};final int[] requests={0};long ticket=controller.begin();
   check(!controller.run(ticket,new WakeRestoreController.Driver(){public boolean allowed(){return true;}public long nowMillis(){return clock[0];}public void delay(int seconds){clock[0]+=seconds*1000;}public boolean request(){requests[0]++;clock[0]+=119000;return false;}}));check(requests[0]==1);
   controller=new WakeRestoreController();ticket=controller.begin();clock[0]=0;
   check(!controller.run(ticket,new WakeRestoreController.Driver(){public boolean allowed(){return true;}public long nowMillis(){return clock[0];}public void delay(int seconds){}public boolean request(){clock[0]+=120000;return true;}}));
   final WakeRestoreController cancel=new WakeRestoreController();long oldTicket=cancel.begin();cancel.cancel();long fresh=cancel.begin();check(fresh>oldTicket&&!cancel.current(oldTicket)&&cancel.current(fresh));
   check(!cancel.run(oldTicket,new WakeRestoreController.Driver(){public boolean allowed(){return true;}public void delay(int seconds){}public boolean request(){throw new AssertionError("cancelled round executed");}}));check(cancel.current(fresh));
   check(cancel.run(fresh,new WakeRestoreController.Driver(){public boolean allowed(){return true;}public void delay(int seconds){}public boolean request(){return true;}}));
   System.out.println("PASS "+checks+" wake repair checks: own-only merge, master switch preservation, revocation, concurrent settings, corrupt choice, bounded redacted log, loaded APK hash, and deadline. Android wake delivery remains a vehicle test.");
  }finally{try(java.util.stream.Stream<Path> files=Files.walk(home)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
 }
}
