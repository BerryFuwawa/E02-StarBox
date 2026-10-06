package com.e02.rootconsole;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class ConsoleServer implements Closeable {
 private final ServerSocket server;
 private final String token,authority;
 private volatile String remoteAuthority;
 private volatile String remoteOrigin;
 public void setRemoteAuthority(String value){setRemoteOrigin(value==null?null:"http://"+value);}
 public void setRemoteOrigin(String value){if(value==null){remoteOrigin=null;remoteAuthority=null;return;}URI u=URI.create(value);if(!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException("请填写远程网页根地址，例如 http://服务器:端口");remoteOrigin=u.getScheme()+"://"+u.getRawAuthority();remoteAuthority=u.getRawAuthority();}
 private final byte[] page;
 private final boolean rootAllowed;
 private final CommandRunner runner;
 private final Set<Socket> clients=Collections.synchronizedSet(new HashSet<Socket>());
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
 private volatile boolean closed;
 private volatile long lastConnected;
 public boolean hasConnectedClient(){return lastConnected!=0&&System.nanoTime()-lastConnected<TimeUnit.SECONDS.toNanos(90);}
 private int failedAttempts; private long lockedUntil;
 private synchronized int authenticate(String candidate){long now=System.nanoTime();if(now<lockedUntil)return 429;
  if(lockedUntil!=0){lockedUntil=0;failedAttempts=0;}
  if(MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),candidate.getBytes(StandardCharsets.UTF_8))){failedAttempts=0;return 200;}
  if(++failedAttempts>=5){lockedUntil=now+TimeUnit.SECONDS.toNanos(30);return 429;}return 401;
 }
 public ConsoleServer(String host,int port,String token,byte[] page,boolean rootAllowed,CommandRunner runner)throws IOException {
  this.server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName(host),port),8);
  this.authority=host+":"+server.getLocalPort();this.token=token;this.page=page;this.rootAllowed=rootAllowed;this.runner=runner;
 }
 public int port(){return server.getLocalPort();}
 public void start(){Thread t=new Thread(()->{while(!closed){try{Socket s=server.accept();clients.add(s);try{workers.execute(()->serve(s));}catch(RejectedExecutionException e){clients.remove(s);s.close();}}catch(IOException e){if(!closed)close();}}},"http-listener");t.setDaemon(true);t.start();}
 private static String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c=='\n')break;if(c!='\r')b.write(c);if(b.size()>8192)throw new IOException("Header too long");}if(c==-1&&b.size()==0)return null;return new String(b.toByteArray(),StandardCharsets.ISO_8859_1);}
 private void serve(Socket socket){try(Socket s=socket){s.setSoTimeout(8000);InputStream in=s.getInputStream();String first=line(in);if(first==null)return;String[] request=first.split(" ");if(request.length!=3){reply(s,400,"text/plain","Bad request");return;}
  Map<String,String> headers=new HashMap<>();String l;int size=first.length();while((l=line(in))!=null&&!l.isEmpty()){size+=l.length();if(size>16384){reply(s,431,"text/plain","Headers too large");return;}int colon=l.indexOf(':');if(colon<1){reply(s,400,"text/plain","Bad header");return;}String key=l.substring(0,colon).trim().toLowerCase(Locale.US);if(headers.put(key,l.substring(colon+1).trim())!=null){reply(s,400,"text/plain","Duplicate header");return;}}
  String requestAuthority=headers.get("host");if(!authority.equals(requestAuthority)&&!(remoteAuthority!=null&&remoteAuthority.equals(requestAuthority))){reply(s,403,"text/plain","Wrong host");return;}
  if(request[0].equals("GET")&&request[1].equals("/")){reply(s,200,"text/html; charset=utf-8",page);return;}
  if(!request[1].equals("/api/run")&&!request[1].equals("/api/status")){reply(s,404,"text/plain","Not found");return;}
  int auth=authenticate(headers.getOrDefault("x-e02-token",""));if(auth!=200){reply(s,auth,"application/json",auth==429?"{\"error\":\"连接码连续输错，请等待 30 秒再试\"}":"{\"error\":\"连接码无效或连接已重启\"}");return;}
  String origin=headers.get("origin");if(origin!=null&&!origin.equals(authority.equals(requestAuthority)?"http://"+authority:remoteOrigin)){reply(s,403,"application/json","{\"error\":\"跨站请求被拒绝\"}");return;}lastConnected=System.nanoTime();
  if(request[1].equals("/api/status")&&request[0].equals("GET")){reply(s,200,"application/json","{\"rootAllowed\":"+rootAllowed+",\"timeoutSeconds\":30}");return;}
  if(!request[0].equals("POST")||!request[1].equals("/api/run")){reply(s,405,"text/plain","Method not allowed");return;}
  if(headers.containsKey("transfer-encoding")){reply(s,400,"text/plain","Chunked body unsupported");return;}
  if(!headers.getOrDefault("content-type","").toLowerCase(Locale.US).startsWith("text/plain")){reply(s,415,"text/plain","Use text/plain");return;}
  int length;try{length=Integer.parseInt(headers.getOrDefault("content-length","-1"));}catch(NumberFormatException e){length=-1;}if(length<1||length>32768){reply(s,413,"text/plain","Body missing or too large");return;}
  byte[] body=new byte[length];int offset=0,n;while(offset<length&&(n=in.read(body,offset,length-offset))>0)offset+=n;if(offset!=length){reply(s,400,"text/plain","Incomplete body");return;}
  String mode=headers.getOrDefault("x-e02-mode","normal");if(!mode.equals("normal")&&!mode.equals("root")){reply(s,400,"text/plain","Unknown mode");return;}
  if(mode.equals("root")&&!rootAllowed){reply(s,403,"application/json","{\"error\":\"本次连接尚未允许执行 Root 命令\"}");return;}
  CommandRunner.Result r=runner.execute(new String(body,StandardCharsets.UTF_8),mode.equals("root"),30);reply(s,200,"application/json; charset=utf-8",r.json());
 }catch(Exception e){}finally{clients.remove(socket);}}
 private static void reply(Socket s,int status,String type,String text)throws IOException{reply(s,status,type,text.getBytes(StandardCharsets.UTF_8));}
 private static void reply(Socket s,int status,String type,byte[] body)throws IOException{OutputStream out=s.getOutputStream();String head="HTTP/1.1 "+status+" Result\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n";out.write(head.getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();}
 public void close(){closed=true;try{server.close();}catch(Exception e){}runner.close();synchronized(clients){for(Socket s:clients){try{s.close();}catch(Exception e){}}clients.clear();}workers.shutdownNow();}
}
