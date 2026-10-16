package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Fixed stage/outcome fields only: no commands, output, credentials or exception messages. */
final class RootTransportLog {
 enum Stage { ACCEPT, AUTH, REQUEST, EXECUTE, REPLY, TERMINAL }
 enum Outcome { STARTED, COMPLETE, DENIED, INVALID, TIMEOUT, INCOMPLETE, IO_FAILED, FAILED, BUSY, CANCELLED }
 interface Sink {void record(long id,Stage stage,Outcome outcome,long millis);}
 static final Sink NONE=(id,stage,outcome,millis)->{};
 static final String FILE="root-transport-events";
 static final long RETENTION=7L*24*60*60*1000;
 static final int LIMIT=65536;
 static synchronized void record(File directory,long id,Stage stage,Outcome outcome,long millis){record(directory,id,stage,outcome,millis,System.currentTimeMillis());}
 static synchronized void record(File directory,long id,Stage stage,Outcome outcome,long millis,long now){
  if(id<=0||stage==null||outcome==null||millis<0||millis>86400000)return;
  Path temp=null;
  try{
   String text=retained(directory,now)+now+" "+id+" "+stage.name()+" "+outcome.name()+" "+millis+"\n";
   byte[] bytes=text.getBytes(StandardCharsets.US_ASCII);int begin=Math.max(0,bytes.length-LIMIT);
   if(begin>0){while(begin<bytes.length&&bytes[begin]!='\n')begin++;if(begin<bytes.length)begin++;}
   temp=Files.createTempFile(directory.toPath(),"root-transport-",".tmp");
   try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(bytes,begin,bytes.length-begin);out.getFD().sync();}
   Files.move(temp,new File(directory,FILE).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }catch(IOException|RuntimeException unavailable){}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
 }
 private static String retained(File directory,long now)throws IOException{
  File file=new File(directory,FILE);if(!file.isFile()||file.length()>LIMIT*2)return "";
  StringBuilder result=new StringBuilder();
  for(String line:new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII).split("\n")){
   String[] fields=valid(line,now);if(fields!=null)result.append(line).append('\n');
  }
  return result.toString();
 }
 private static String[] valid(String line,long now){try{
  String[] f=line.split(" ",-1);if(f.length!=5)return null;
  long at=Long.parseLong(f[0]),id=Long.parseLong(f[1]),ms=Long.parseLong(f[4]);
  Stage.valueOf(f[2]);Outcome.valueOf(f[3]);
  return at>now-RETENTION&&at<=now&&id>0&&ms>=0&&ms<=86400000?f:null;
 }catch(RuntimeException invalid){return null;}}
 static synchronized String read(File directory,long now){try{
  StringBuilder out=new StringBuilder();SimpleDateFormat date=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA);
  for(String line:retained(directory,now).split("\n")){
   String[] f=valid(line,now);if(f==null)continue;
   String stage;switch(Stage.valueOf(f[2])){case ACCEPT:stage="接收连接";break;case AUTH:stage="校验授权";break;case REQUEST:stage="接收请求";break;case EXECUTE:stage="执行请求";break;case REPLY:stage="返回结果";break;default:stage="连接结束";}
   String outcome;switch(Outcome.valueOf(f[3])){case STARTED:outcome="开始";break;case COMPLETE:outcome="完成";break;case DENIED:outcome="未获允许";break;case INVALID:outcome="请求格式无效";break;case TIMEOUT:outcome="等待超时";break;case INCOMPLETE:outcome="数据未接收完整";break;case IO_FAILED:outcome="连接中断";break;case BUSY:outcome="已有任务正在执行";break;case CANCELLED:outcome="已停止";break;default:outcome="未完成";}
   out.append(date.format(new Date(Long.parseLong(f[0])))).append("  Root连接 #").append(f[1]).append(" · ").append(stage).append(" · ").append(outcome).append(" · ").append(f[4]).append("毫秒\n");
  }return out.toString();
 }catch(IOException|RuntimeException unavailable){return "";}}
}
