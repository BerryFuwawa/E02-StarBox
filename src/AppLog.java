package com.e02.rootconsole;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
/** App-private, bounded status events. Never pass commands, credentials or clipboard text. */
final class AppLog {
 static final long RETENTION=7L*24*60*60*1000;private static final int LIMIT=256*1024;
 static synchronized void record(File directory,String category,String message){try{write(directory,retained(directory,System.currentTimeMillis())+System.currentTimeMillis()+"\t"+clean(category)+"\t"+clean(message)+"\n");}catch(IOException|RuntimeException ignored){}}
 static synchronized String read(File directory){try{String retained=retained(directory,System.currentTimeMillis());write(directory,retained);StringBuilder text=new StringBuilder();SimpleDateFormat format=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA);for(String line:retained.split("\n")){String[] parts=line.split("\t",3);if(parts.length==3)text.append(format.format(new Date(Long.parseLong(parts[0])))).append("  ").append(parts[1]).append(" · ").append(parts[2]).append('\n');}String own=text.length()==0?"暂无日志":text.toString();String wake=WakeDiagnosticLog.read(directory,System.currentTimeMillis());String transport=rootTransport(directory);return own+(wake.isEmpty()?"":"\n"+wake)+(transport.isEmpty()?"":"\n"+transport);}catch(IOException|RuntimeException failed){return "无法读取日志，请检查存储空间后重试";}}
 private static String clean(String text){String value=text==null?"":text.replace('\n',' ').replace('\r',' ').replace('\t',' ');return value.substring(0,Math.min(value.length(),200));}
 static String rootTransport(File directory){return RootTransportLog.read(directory,System.currentTimeMillis());}
 static String retained(File directory,long now)throws IOException{File target=new File(directory,"app-events.log");if(!target.isFile()||target.length()>LIMIT*2)return "";StringBuilder value=new StringBuilder();for(String line:new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8).split("\n")){String[] parts=line.split("\t",3);try{long time=Long.parseLong(parts[0]);if(parts.length==3&&time>now-RETENTION&&time<=now)value.append(line).append('\n');}catch(NumberFormatException ignored){}}return value.toString();}
 private static void write(File directory,String text)throws IOException{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);int start=0;if(bytes.length>LIMIT){start=bytes.length-LIMIT;while(start<bytes.length&&bytes[start]!='\n')start++;if(start<bytes.length)start++;}Path temp=Files.createTempFile(directory.toPath(),"app-log-",".tmp");try{try(FileOutputStream stream=new FileOutputStream(temp.toFile())){stream.write(bytes,start,bytes.length-start);stream.getFD().sync();}Files.move(temp,new File(directory,"app-events.log").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
 static void startup(File directory,String stage,String result){String text=null;
  if("received".equals(stage)){
   if("COLD_START".equals(result))text="收到开机通知";
   else if("REPLACED".equals(result))text="收到应用更新通知";
   else if("WAKE".equals(result))text="收到亮屏恢复通知";
   else text="收到后台恢复通知";
  }else if("service".equals(stage))text="requested".equals(result)?"已请求启动后台":"系统未允许启动后台";else if("skipped".equals(stage))text="当前设置不需要自动启动";else if("recovery".equals(stage))text="READY".equals(result)?"后台连接恢复准备完成":"ROOT_WAIT".equals(result)?"等待 Root 授权恢复":"WEB_WAIT".equals(result)?"网页回传等待授权":"ADB_WAIT".equals(result)?"远程连接等待 ADB 或授权":"等待后台恢复";if(text!=null)record(directory,"启动",text);}
}
