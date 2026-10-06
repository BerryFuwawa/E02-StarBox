package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommandRunner {
 public static final int LIMIT=131072;
 private final String shell, su;
 private final AtomicBoolean busy=new AtomicBoolean(false);
 private volatile boolean closed;
 private volatile Process current;
 public CommandRunner(String shell,String su){this.shell=shell;this.su=su;}
 public static final class Result {
  public String stdout="",stderr="",error=""; public int exit=-1; public boolean timedOut,truncated;public long millis;
  public String json(){return "{\"stdout\":"+quote(stdout)+",\"stderr\":"+quote(stderr)+",\"error\":"+quote(error)+",\"exitCode\":"+exit+",\"timedOut\":"+timedOut+",\"truncated\":"+truncated+",\"durationMs\":"+millis+"}";}
 }
 private static final class Capture implements Runnable {
  final InputStream stream; final ByteArrayOutputStream saved=new ByteArrayOutputStream(); boolean truncated;
  Capture(InputStream stream){this.stream=stream;}
  public void run(){byte[] buffer=new byte[4096];try{int n;while((n=stream.read(buffer))!=-1){synchronized(this){int retain=Math.min(n,LIMIT-saved.size());if(retain>0)saved.write(buffer,0,retain);if(retain<n)truncated=true;}}}catch(IOException e){}finally{try{stream.close();}catch(IOException e){}}}
  synchronized String text(){return new String(saved.toByteArray(),StandardCharsets.UTF_8);}
  synchronized boolean clipped(){return truncated;}
 }
 public Result execute(String command,boolean root,int seconds){
  Result result=new Result();long start=System.nanoTime();
  if(command==null||command.trim().isEmpty()||command.length()>8192||command.indexOf('\0')>=0){result.error="命令为空、过长或包含无效字符";return result;}
  if(closed){result.error="命令连接已关闭";return result;}
  if(!busy.compareAndSet(false,true)){result.error="已有命令正在执行，请稍后重试";return result;}
  Process process=null;
  try {
   // The entire input is one -c argument, including multiline commands.
   process=new ProcessBuilder(root?su:shell,"-c",command).start();
   current=process;
   if(closed){terminate(process);throw new IOException("命令连接已关闭");}
   process.getOutputStream().close();
   Capture stdout=new Capture(process.getInputStream()),stderr=new Capture(process.getErrorStream());
   Thread out=new Thread(stdout,"stdout"),err=new Thread(stderr,"stderr");out.setDaemon(true);err.setDaemon(true);out.start();err.start();
   if(!process.waitFor(seconds,TimeUnit.SECONDS)){result.timedOut=true;terminate(process);}
   if(!process.isAlive())result.exit=process.exitValue();
   out.join(700);err.join(700);
   result.stdout=stdout.text();result.stderr=stderr.text();result.truncated=stdout.clipped()||stderr.clipped();
   if(out.isAlive()||err.isAlive()){result.truncated=true;result.error="命令仍有输出；请勿运行后台任务或需要继续输入的程序";}
  } catch(Exception e){result.error=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
  finally{if(process!=null){if(process.isAlive())terminate(process);try{process.getInputStream().close();}catch(Exception e){}try{process.getErrorStream().close();}catch(Exception e){}}current=null;busy.set(false);result.millis=(System.nanoTime()-start)/1000000;}
  return result;
 }
 private static void terminate(Process p){p.destroy();try{if(!p.waitFor(300,TimeUnit.MILLISECONDS))p.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();p.destroyForcibly();}}
 public void close(){closed=true;Process p=current;if(p!=null)terminate(p);}
 public static String quote(String s){StringBuilder b=new StringBuilder("\"");for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();}
}
