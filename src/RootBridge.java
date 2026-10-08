package com.e02.rootconsole;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Entry point for Android app_process, not an exported Android service. */
public class RootBridge {
 public static void main(String[] args)throws Exception{
  if(android.os.Process.myUid()!=0)throw new SecurityException("RootService UID 0 required");
  RootLaunch.validate(args);
  byte[] bytes=new byte[32];try(FileInputStream in=new FileInputStream(args[0])){int offset=0,n;while(offset<bytes.length&&(n=in.read(bytes,offset,bytes.length-offset))>0)offset+=n;if(offset!=32||in.read()!=-1)throw new IOException("Invalid token file");}
  String secret=new String(bytes,StandardCharsets.US_ASCII);if(!secret.matches("[0-9a-f]{32}"))throw new IOException("Invalid token");
  CommandRunner runner=new CommandRunner("/system/bin/sh","/system/bin/sh");
  BridgeRunner.stop(secret);
  try(ServerSocket server=new ServerSocket()){for(int attempt=0;;attempt++){try{server.bind(new InetSocketAddress("127.0.0.1",8876),4);break;}catch(BindException e){if(attempt>=49)throw e;Thread.sleep(100);}}server.setSoTimeout(1000);System.out.println("Root authorization ready; loopback only.");
   while(true){Socket client;try{client=server.accept();}catch(SocketTimeoutException e){continue;}
    try(Socket s=client){s.setSoTimeout(3000);DataInputStream in=new DataInputStream(s.getInputStream());String candidate=in.readUTF();if(!MessageDigest.isEqual(bytes,candidate.getBytes(StandardCharsets.US_ASCII)))continue;
     String op=in.readUTF();if(op.equals("stop"))break;if(!op.equals("run"))continue;int seconds=in.readInt();if(seconds<1||seconds>45)continue;String command=BridgeRunner.readText(in);CommandRunner.Result r=runner.execute(command,true,seconds);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeInt(r.exit);out.writeBoolean(r.timedOut);out.writeBoolean(r.truncated);out.writeLong(r.millis);BridgeRunner.writeText(out,r.stdout);BridgeRunner.writeText(out,r.stderr);BridgeRunner.writeText(out,r.error);out.flush();
    }catch(Exception e){System.err.println(e.getClass().getSimpleName());}
   }
  }finally{runner.close();}
 }
}
