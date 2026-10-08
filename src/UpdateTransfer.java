package com.e02.rootconsole;
import java.io.*;
import java.security.*;

/** Streaming verification: never keep an APK-sized buffer in memory. */
public final class UpdateTransfer {
 public interface Progress {boolean cancelled();void received(long bytes)throws Exception;}
 public static String digest(File file)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}return hex(hash.digest());}
 private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.US,"%02x",b&255));return s.toString();}
 public static void verify(File file,long size,String sha)throws Exception{if(!file.isFile()||file.length()!=size||!digest(file).equalsIgnoreCase(sha))throw new IOException("安装包完整性校验失败");}
 public static void download(InputStream in,File part,File apk,long size,String sha,Progress progress)throws Exception{
  boolean complete=false,created=false;try{if(part.exists()||apk.exists()||!part.createNewFile())throw new IOException("下载文件已存在，请重新开始下载");created=true;MessageDigest hash=MessageDigest.getInstance("SHA-256");long bytes=0;try(FileOutputStream out=new FileOutputStream(part)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(progress.cancelled())throw new IOException("已取消下载");bytes+=n;if(bytes>size)throw new IOException("安装包大小不匹配");out.write(b,0,n);hash.update(b,0,n);progress.received(bytes);}out.getFD().sync();}if(progress.cancelled())throw new IOException("已取消下载");if(bytes!=size||!hex(hash.digest()).equalsIgnoreCase(sha))throw new IOException("安装包完整性校验失败");if(apk.exists()||!part.renameTo(apk))throw new IOException("无法保存安装包");complete=true;}finally{if(created&&!complete&&part.exists())part.delete();}
 }
}
