package com.e02.rootconsole;
import android.content.Context;
import android.content.pm.*;
import java.io.*;
import java.net.*;
import org.json.JSONObject;

/** One bounded task, launched through the user's existing root authorization. */
public final class RootUpdateWorker {
 private static final String PACKAGE="com.e02.rootconsole";
 private File folder,apk,part;private JSONObject task;private UpdateRelease release;private int uid;private Context context;
 private static Context systemContext()throws Exception{if(android.os.Looper.myLooper()==null)android.os.Looper.prepareMainLooper();Class<?> type=Class.forName("android.app.ActivityThread");Object thread=type.getMethod("systemMain").invoke(null);return (Context)type.getMethod("getSystemContext").invoke(thread);}
 private boolean cancelled(){return new File(folder,"cancel").exists();}
 private void status(String phase,String message,long bytes)throws Exception{UpdateTask.write(new File(folder,"status.json"),new JSONObject().put("id",task.getString("id")).put("phase",phase).put("message",message).put("bytes",bytes).put("size",release.size).put("version",release.version).put("time",System.currentTimeMillis()),uid);}
 private PackageInfo installed()throws Exception{return context.getPackageManager().getPackageInfo(PACKAGE,PackageManager.GET_SIGNATURES);}
 private void verify()throws Exception{
  UpdateTransfer.verify(apk,release.size,release.sha);PackageInfo local=installed(),archive=context.getPackageManager().getPackageArchiveInfo(apk.getPath(),PackageManager.GET_SIGNATURES);
  if(archive==null||!PACKAGE.equals(archive.packageName)||archive.getLongVersionCode()!=release.code||release.code<=local.getLongVersionCode())throw new IOException("安装包版本或包名不匹配");
  if(local.signatures==null||archive.signatures==null||!java.util.Arrays.equals(local.signatures,archive.signatures))throw new IOException("安装包签名不匹配");
 }
 private void run(String op,File input)throws Exception{
  if(android.os.Process.myUid()!=0)throw new SecurityException("需要已有 Root 授权");context=systemContext();uid=installed().applicationInfo.uid;
  File expected=new File("/data/user/0/"+PACKAGE+"/files/update-task").getCanonicalFile();folder=input.getParentFile().getCanonicalFile();
  if(!folder.equals(expected)||!input.getName().equals("task.json")||android.system.Os.stat(input.getPath()).st_uid!=uid)throw new SecurityException("更新任务位置无效");
  task=UpdateTask.read(input);release=new UpdateRelease(task.getJSONObject("release"));String id=task.getString("id");if(!id.matches("[a-f0-9]{32}"))throw new IOException("更新任务信息异常");
  File downloads=new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getPath()).getCanonicalFile();if(!downloads.isDirectory()&&!downloads.mkdirs())throw new IOException("无法创建下载文件夹");
  apk=new File(downloads,"E02-Starbox-v"+release.version+"-"+id.substring(0,8)+".apk");part=new File(apk.getPath()+".part");
  boolean ownDownload=false;try{
   if(op.equals("download")){
    status("downloading","正在下载 "+release.version,0);UpdateHttp http=new UpdateHttp();Thread monitor=new Thread(()->{while(!Thread.currentThread().isInterrupted()){if(cancelled()){http.close();return;}try{Thread.sleep(250);}catch(InterruptedException e){return;}}},"update-cancel");monitor.setDaemon(true);monitor.start();
    try(Closeable deadline=http.deadline(600000)){HttpURLConnection c=http.open(UpdatePolicy.url(task.getString("route"),release.url),false);try(InputStream in=c.getInputStream()){UpdateTransfer.download(in,part,apk,release.size,release.sha,new UpdateTransfer.Progress(){long last;public boolean cancelled(){return RootUpdateWorker.this.cancelled();}public void received(long n)throws Exception{if(System.currentTimeMillis()-last>=500){last=System.currentTimeMillis();status("downloading","下载中 "+(n*100/release.size)+"%",n);}}});ownDownload=true;}finally{c.disconnect();}verify();if(cancelled())throw new IOException("已取消下载");status("ready","下载完成，点击安装 "+release.version,release.size);}finally{http.close();monitor.interrupt();}
   }else if(op.equals("install")){
    if(cancelled())throw new IOException("已取消安装");verify();status("installing","正在安装 "+release.version,release.size);
    CommandRunner runner=new CommandRunner("/system/bin/sh","/system/bin/sh");CommandRunner.Result result;try{result=runner.execute("pm install -r --user 0 --pkg "+PACKAGE+" "+UpdateTask.quote(apk.getPath()),true,180);}finally{runner.close();}
    PackageInfo after=installed();if(after.getLongVersionCode()!=release.code||!release.version.equals(after.versionName)){System.err.println(result.stdout+" "+result.stderr+" "+result.error);throw new IOException(result.timedOut?"安装等待超时，请重新打开后查看实际版本":result.stdout.contains("INSUFFICIENT_STORAGE")?"车机可用空间不足，请清理后重试":"安装未完成，安装包已保留，请重新检测 Root 后重试");}
    boolean deleted=cleanup();status("completed",deleted?"已更新至 "+release.version:"已更新至 "+release.version+"，安装包未能删除，可在下载文件夹手动删除",release.size);
    CommandRunner reopen=new CommandRunner("/system/bin/sh","/system/bin/sh");try{reopen.execute("am start -n "+PACKAGE+"/.MainActivity",true,10);}finally{reopen.close();}
   }else if(op.equals("recover")){
    if(installed().getLongVersionCode()>=release.code){boolean deleted=cleanup();status("completed",deleted?"更新已完成":"更新已完成，安装包未能删除",release.size);}else {verify();status("ready","安装包已保留，点击重试安装",release.size);}
   }else throw new IOException("更新操作无效");
  }catch(Exception e){if(op.equals("download")&&ownDownload&&apk.exists())apk.delete();status(cancelled()?"cancelled":op.equals("download")?"download_failed":"install_failed",cancelled()?"下载已取消":(op.equals("download")?"下载失败：":"安装失败：")+UserMessages.explain(e),0);}
 }
 private boolean cleanup(){if(!apk.exists())return true;try{UpdateTransfer.verify(apk,release.size,release.sha);return apk.delete();}catch(Exception changed){return false;}}
 public static void main(String[] args){try{if(args.length!=2)throw new IOException("更新任务参数无效");new RootUpdateWorker().run(args[0],new File(args[1]));}catch(Exception e){e.printStackTrace();}}
}
