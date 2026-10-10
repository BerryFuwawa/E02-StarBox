package com.e02.rootconsole;
import java.io.IOException;

/** Keep a manual change, its verification and restoration intent in one transaction. */
public final class AdbChange {
 public interface Committer { void save() throws IOException; }
 private static void require(CommandRunner.Result r,String operation)throws IOException {
  if(r.timedOut)throw new IOException(operation+"超时，请重新检测实际状态");
  if(r.exit!=0||!r.error.isEmpty())throw new IOException(operation+"失败："+(r.error.isEmpty()?r.stderr.trim():r.error));
 }
 public static AdbStatus apply(CommandRunner runner,String command,String inspection,boolean wireless,boolean on,Committer committer)throws IOException {
  synchronized(AdbControl.LOCK){
   require(runner.execute(command,true,10),"修改 ADB 设置");
   CommandRunner.Result result=runner.execute(inspection,true,5);require(result,"检测 ADB 状态");
   AdbStatus status=new AdbStatus(result.stdout);
   if((wireless?status.wirelessState():status.wired)!=(on?1:0)||wireless&&on&&status.allowed!=1)
    throw new IOException("实际状态未符合本次操作，请重新检测后重试");
   // Maintenance takes the same lock and rechecks this intent before restoring rules.
   committer.save();return status;
  }
 }
}
