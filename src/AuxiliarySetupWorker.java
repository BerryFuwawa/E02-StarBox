package com.e02.rootconsole;
import java.io.File;
/** Explicit setup in the current APK; never grants Root or changes another component. */
public final class AuxiliarySetupWorker {
 public static void main(String[] args)throws Exception {
  if(android.os.Process.myUid()!=0||args.length!=2||!RootPermit.nonce(args[1]))throw new SecurityException("Invalid setup request");
  File directory=new File(args[0]).getCanonicalFile();if(!directory.getName().equals("files")||!directory.getParentFile().getName().equals("com.e02.rootconsole"))throw new SecurityException("Invalid setup directory");
  RootPermit.Ticket ticket=RootPermit.active(directory);if(ticket==null||!ticket.nonce.equals(args[1])||!RootPermit.matches(ticket))throw new SecurityException("Permission ended");
  try(RootWakeObserver worker=new RootWakeObserver(directory,ticket)){
   AuxiliaryRepair.Result result=worker.repairAccessibility(android.os.SystemClock.elapsedRealtime()+30000);
   System.out.println(result.name());if(result!=AuxiliaryRepair.Result.PRESENT&&result!=AuxiliaryRepair.Result.REPAIRED)System.exit(1);
  }
 }
}
