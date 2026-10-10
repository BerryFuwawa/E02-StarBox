package com.e02.rootconsole;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Entry point for Android app_process, not an exported Android service. */
public class RootBridge {
 public static void main(String[] args)throws Exception{
  if(android.os.Process.myUid()!=0)throw new SecurityException("RootService UID 0 required");
  RootLaunch.validate(args);
  byte[] bytes=new byte[32];try(FileInputStream in=new FileInputStream(args[0])){int offset=0,n;while(offset<bytes.length&&(n=in.read(bytes,offset,bytes.length-offset))>0)offset+=n;if(offset!=32||in.read()!=-1)throw new IOException("Invalid token file");}
  String secret=new String(bytes,StandardCharsets.US_ASCII);if(!secret.matches("[0-9a-f]{32}"))throw new IOException("Invalid token");
  File directory=new File(args[0]).getCanonicalFile().getParentFile();
  String requested=RootLaunch.generation(args);RootPermit.Ticket ticket=RootPermit.active(directory);
  if(requested!=null&&(ticket==null||!requested.equals(ticket.nonce)))throw new SecurityException("Root launch permission ended");
  if(ticket==null&&RootPermit.file(directory).exists())throw new SecurityException("Root launch permission ended");
  if(ticket!=null){
   if(!RootPermit.matches(ticket))throw new SecurityException("Root launch permission ended");
   try{BridgeRunner.identity(secret,ticket.nonce);return;}catch(IOException missing){}
   // Modern managed services ignore the legacy stop, so a newer generation cannot be stopped here.
   BridgeRunner.stop(secret);
   if(!RootPermit.matches(ticket))throw new SecurityException("Root launch permission ended");
  }
  else BridgeRunner.stop(secret);
  final RootPermit.Ticket active=ticket;
  String updateApk=System.getProperty("java.class.path","");try{ManagedUpdate.command(updateApk,"recover");}catch(IOException invalid){updateApk=null;}
  try(RootWakeObserver observer=new RootWakeObserver(directory,active);RootBridgeServer server=new RootBridgeServer(secret,android.os.Process.myUid(),android.os.Process.myPid(),ticket==null?"legacy":ticket.nonce,
      new OwnedRootCommandRunner(),()->active==null?!RootPermit.file(directory).exists():RootPermit.matches(active),new OwnedRootCommandRunner(),updateApk,observer::status)) {
   observer.register();server.serve(8876);
  }
 }
}
