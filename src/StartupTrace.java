package com.e02.rootconsole;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
/** Bounded app-private startup diagnostics, without credentials or command text. */
final class StartupTrace {
 static synchronized void record(File directory,String stage,String result){AppLog.startup(directory,stage,result);Path temp=null;try{
  File target=new File(directory,"startup-events");String previous=target.isFile()&&target.length()<=16384?new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8):"";
  String[] lines=previous.split("\n");StringBuilder value=new StringBuilder();for(int i=Math.max(0,lines.length-19);i<lines.length;i++)if(!lines[i].isEmpty()){try{long timestamp=Long.parseLong(lines[i].split(" ",2)[0]);if(timestamp>System.currentTimeMillis()-AppLog.RETENTION&&timestamp<=System.currentTimeMillis())value.append(lines[i]).append('\n');}catch(NumberFormatException ignored){}}
  value.append(System.currentTimeMillis()).append(' ').append(clean(stage)).append(' ').append(clean(result)).append('\n');temp=Files.createTempFile(directory.toPath(),"startup-",".tmp");
  try(FileOutputStream stream=new FileOutputStream(temp.toFile())){stream.write(value.toString().getBytes(StandardCharsets.UTF_8));stream.getFD().sync();}Files.move(temp,target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
 }catch(IOException|RuntimeException ignored){}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}}
 private static String clean(String text){if(text==null)return "";String value=text.replaceAll("[^a-zA-Z0-9_.-]","_");return value.substring(0,Math.min(value.length(),120));}
}
