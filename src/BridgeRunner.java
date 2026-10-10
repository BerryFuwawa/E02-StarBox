package com.e02.rootconsole;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Local owned service only. Identity checks never execute an arbitrary command. */
public class BridgeRunner extends CommandRunner {
 private final String secret;private final boolean stopOnClose;private volatile boolean closed;
 private volatile Socket active;
 private volatile int connectedPort;private volatile boolean authenticated;
 int connectedPort(){return connectedPort;}boolean authenticated(){return authenticated;}
 public BridgeRunner(String secret,boolean stopOnClose){super("/system/bin/sh","su");this.secret=secret;this.stopOnClose=stopOnClose;}
 public static String readText(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>262144)throw new IOException("Invalid bridge output length");byte[] bytes=new byte[n];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}
 public static void writeText(DataOutputStream out,String s)throws IOException{byte[] bytes=s.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
 /** Only connection refusal may fall back to the previous version; never retry an executed command. */
 private Socket connect()throws IOException {Socket socket=new Socket();try{socket.connect(new InetSocketAddress("127.0.0.1",8876),1000);return socket;}catch(ConnectException missing){socket.close();Socket legacy=new Socket();try{legacy.connect(new InetSocketAddress("127.0.0.1",38766),1000);return legacy;}catch(IOException e){legacy.close();throw e;}}catch(IOException e){socket.close();throw e;}}
 @Override public Result execute(String command,boolean root,int seconds){if(!root)return super.execute(command,false,seconds);authenticated=false;Result r=new Result();if(closed){r.error="Root 连接已结束";return r;}if(seconds<1||seconds>45){r.error="命令执行时限无效";return r;}try(Socket s=connect()){active=s;connectedPort=s.getPort();if(closed)throw new IOException("命令连接已关闭");s.setSoTimeout((seconds+4)*1000);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(secret);out.writeUTF("run");out.writeInt(seconds);writeText(out,command);out.flush();DataInputStream in=new DataInputStream(s.getInputStream());r.exit=in.readInt();r.timedOut=in.readBoolean();r.truncated=in.readBoolean();r.millis=in.readLong();r.stdout=readText(in);r.stderr=readText(in);r.error=readText(in);authenticated=true;}catch(Exception e){r.error="Root 授权不可用，请重新授权："+UserMessages.explain(e);}finally{active=null;}return r;}
 @Override public void close(){closed=true;Socket socket=active;if(socket!=null)try{socket.close();}catch(IOException ignored){}super.close();if(stopOnClose)stop(secret);}
 public static void stop(String secret){for(int port:new int[]{8876,38766})try(Socket s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",port),500);s.setSoTimeout(500);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(secret);out.writeUTF("stop");out.flush();}catch(Exception e){}}
 enum StopResult{STOPPED,INCOMPLETE,UNAVAILABLE}
 static boolean stopManaged(String secret,String generation){return stopManagedResult(secret,generation,8876)==StopResult.STOPPED;}
 static StopResult stopManagedResult(String secret,String generation,int port){if(!RootPermit.nonce(generation))return StopResult.UNAVAILABLE;try(Socket s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",port),500);s.setSoTimeout(5500);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(secret);out.writeUTF("stop-managed");out.writeUTF(generation);out.flush();String response=new DataInputStream(s.getInputStream()).readUTF();return "STOPPED".equals(response)?StopResult.STOPPED:"STOP_INCOMPLETE".equals(response)?StopResult.INCOMPLETE:StopResult.UNAVAILABLE;}catch(IOException e){return StopResult.UNAVAILABLE;}}
 static RootIdentity identity(String secret,String expected)throws IOException{return identityAt(secret,expected,8876);}
 static String wakeStatus(String secret)throws IOException{try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",8876),700);socket.setSoTimeout(1200);DataOutputStream out=new DataOutputStream(socket.getOutputStream());out.writeUTF(secret);out.writeUTF("wake-status");out.flush();return new DataInputStream(socket.getInputStream()).readUTF();}}
 static RootIdentity identityAt(String secret,String expected,int port)throws IOException{String challenge=RootIdentity.challenge();try(Socket s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",port),700);s.setSoTimeout(1200);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF("identity-v1");out.writeUTF(challenge);out.flush();DataInputStream in=new DataInputStream(s.getInputStream());int uid=in.readInt(),pid=in.readInt();String generation=in.readUTF(),proof=in.readUTF();if(!RootIdentity.verify(secret,challenge,uid,pid,generation,proof,expected))throw new IOException("Owned Root identity not verified");return new RootIdentity(uid,pid,generation);}}
}
