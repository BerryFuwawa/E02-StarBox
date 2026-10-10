package com.e02.rootconsole;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Generates a user-approved authorization command accepted by both verified authorization tools. */
public final class RootAuthorization {
 private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
 public static void prepare(Context c,File token,RootPermit.Ticket lease)throws IOException{
  if(lease==null||!RootPermit.matches(lease))throw new IOException("提权命令已失效，请重新复制");
  String command="#!/system/bin/sh\n"+RootBootstrap.script(c.getApplicationInfo().sourceDir,token.getCanonicalPath(),lease.nonce)+"\n";
  try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"root-authorize.sh"))){out.write(command.getBytes(StandardCharsets.UTF_8));}
 }
}
