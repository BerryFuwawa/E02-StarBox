package com.e02.rootconsole;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Generates a timed authorization command accepted by both verified authorization tools. */
public final class RootAuthorization {
 private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
 public static void prepare(Context c,File token,long seconds)throws IOException{
  if(seconds<0||(seconds>0&&seconds<60)||seconds>90000)throw new IllegalArgumentException("请选择至少 1 分钟，或勾选不设时间上限");
  String command="#!/system/bin/sh\n[ \"$(id -u)\" = 0 ] || { echo '请使用 Root 执行'; exit 1; }\n"+
   "CLASSPATH="+quote(c.getApplicationInfo().sourceDir)+" app_process /system/bin com.e02.rootconsole.RootBridge "+quote(token.getAbsolutePath())+" "+seconds+
   " > "+quote(new File(c.getFilesDir(),"bridge.log").getAbsolutePath())+" 2>&1 < /dev/null &\n";
  try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"rootbridge-start.sh"))){out.write(command.getBytes(StandardCharsets.UTF_8));}
 }
}
