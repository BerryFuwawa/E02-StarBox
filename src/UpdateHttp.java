package com.e02.rootconsole;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** HTTPS transport with cancellable deadlines, also used inside the root worker. */
public final class UpdateHttp implements Closeable {
 private volatile HttpURLConnection active;private volatile boolean stopped,expired;
 private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"update-deadline");t.setDaemon(true);return t;});
 public HttpURLConnection open(String address,boolean sample)throws Exception{
  URL url=new URL(address);
  for(int i=0;i<6;i++){
   if(stopped)throw new IOException(expired?"连接超时":"已取消");
   if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null)throw new IOException("更新线路必须使用 HTTPS");
   HttpURLConnection c=(HttpURLConnection)url.openConnection();active=c;c.setConnectTimeout(sample?5000:15000);c.setReadTimeout(sample?5000:20000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","E02-Starbox");c.setRequestProperty("Cache-Control","no-cache");if(sample)c.setRequestProperty("Range","bytes=0-4095");
   int status=c.getResponseCode();
   if(status>=300&&status<=399){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("线路返回无效跳转");url=new URL(url,next);continue;}
   if(status!=200&&!(sample&&status==206)){c.disconnect();throw new IOException("线路返回 HTTP "+status);}return c;
  }throw new IOException("线路跳转过多");
 }
 public byte[] manifest(String route)throws Exception{
  HttpURLConnection c=open(UpdatePolicy.url(route,UpdatePolicy.MANIFEST),false);
  try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(stopped)throw new IOException(expired?"连接超时":"已取消");out.write(b,0,n);if(out.size()>65536)throw new IOException("版本文件过大");}return out.toByteArray();}finally{c.disconnect();active=null;}
 }
 public void probe(String route,String url)throws Exception{
  HttpURLConnection c=open(UpdatePolicy.url(route,url),true);
  try(InputStream in=c.getInputStream()){byte[] header=new byte[4];int n=0,k;while(n<4&&(k=in.read(header,n,4-n))>0)n+=k;if(n!=4||header[0]!='P'||header[1]!='K'||header[2]!=3||header[3]!=4)throw new IOException("线路未返回有效安装包");}finally{c.disconnect();active=null;}
 }
 public Closeable deadline(long millis){ScheduledFuture<?> alarm=TIMER.schedule(()->{expired=true;close();},millis,TimeUnit.MILLISECONDS);return ()->alarm.cancel(false);}
 public void close(){stopped=true;HttpURLConnection c=active;if(c!=null)c.disconnect();}
}
