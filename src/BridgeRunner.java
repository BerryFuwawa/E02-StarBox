package com.e02.rootconsole;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Local bridge only; the user launches its root process through an existing authorized service. */
public class BridgeRunner extends CommandRunner {
 private final String secret;private final boolean stopOnClose;private volatile boolean closed;
 public BridgeRunner(String secret,boolean stopOnClose){super("/system/bin/sh","su");this.secret=secret;this.stopOnClose=stopOnClose;}
 public static String readText(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>262144)throw new IOException("Invalid bridge output length");byte[] bytes=new byte[n];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}
 public static void writeText(DataOutputStream out,String s)throws IOException{byte[] bytes=s.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
 /** Only connection refusal may fall back to the previous version; never retry an executed command. */
 private Socket connect()throws IOException {Socket socket=new Socket();try{socket.connect(new InetSocketAddress("127.0.0.1",8876),1000);return socket;}catch(ConnectException missing){socket.close();Socket legacy=new Socket();try{legacy.connect(new InetSocketAddress("127.0.0.1",38766),1000);return legacy;}catch(IOException e){legacy.close();throw e;}}catch(IOException e){socket.close();throw e;}}
 @Override public Result execute(String command,boolean root,int seconds){if(!root)return super.execute(command,false,seconds);Result r=new Result();if(closed){r.error="Root 连接已结束";return r;}try(Socket s=connect()){s.setSoTimeout((seconds+4)*1000);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(secret);out.writeUTF("run");out.writeInt(seconds);writeText(out,command);out.flush();DataInputStream in=new DataInputStream(s.getInputStream());r.exit=in.readInt();r.timedOut=in.readBoolean();r.truncated=in.readBoolean();r.millis=in.readLong();r.stdout=readText(in);r.stderr=readText(in);r.error=readText(in);}catch(Exception e){r.error="Root 授权不可用，请重新授权："+UserMessages.explain(e);}return r;}
 @Override public void close(){closed=true;super.close();if(stopOnClose)stop(secret);}
 public static void stop(String secret){for(int port:new int[]{8876,38766})try(Socket s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",port),500);s.setSoTimeout(500);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(secret);out.writeUTF("stop");out.flush();}catch(Exception e){}}
}
