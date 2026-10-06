package com.e02.rootconsole;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A separate, unprivileged text companion. It has no command execution endpoint. */
public final class PhoneServer implements Closeable {
 public interface Target { String send(String text, boolean toFocus,String expectedFocus) throws Exception; }
 private final ServerSocket listener;
 private final byte[] page;
 private final Target target;
 private final SecureRandom random=new SecureRandom();
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(5,5,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(12));
 private final Set<Socket> clients=Collections.synchronizedSet(new HashSet<Socket>());
 private final AtomicBoolean polling=new AtomicBoolean();
 private volatile Set<String> authorities=Collections.emptySet();
 private volatile boolean closed;
 private String pin,session="",focus="未选择输入框",clipboard="",result="";
 private boolean sensitive;
 private int failures;
 private long lockedUntil,pinDeadline,lastSeen,revision=1;
 public PhoneServer(int port,byte[] page,Target target)throws IOException {
  this.page=page;this.target=target;listener=new ServerSocket();listener.setReuseAddress(true);
  try{listener.bind(new InetSocketAddress("0.0.0.0",port),8);}catch(IOException e){listener.close();throw e;}resetPairing();
 }
 public int port(){return listener.getLocalPort();}
 public synchronized String pin(){if(System.nanoTime()>pinDeadline&&!paired())resetPairing();return pin;}
 public synchronized boolean paired(){return !session.isEmpty()&&System.nanoTime()-lastSeen<TimeUnit.SECONDS.toNanos(60);}
 public void setHosts(Collection<String> hosts){Set<String> fresh=new HashSet<>();for(String h:hosts)fresh.add(h+":"+port());authorities=Collections.unmodifiableSet(fresh);}
 public synchronized void resetPairing(){pin=String.format(Locale.US,"%04d",random.nextInt(10000));pinDeadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);session="";clipboard="";sensitive=false;result="";lastSeen=0;revision++;notifyAll();}
 public synchronized void focus(String label){String value=label==null?"未选择输入框":label;if(!value.equals(focus)){focus=value;revision++;notifyAll();}}
 public synchronized String focusLabel(){return focus;}
 public synchronized void copied(String text,boolean secret){clipboard=text==null?"":text;sensitive=secret;revision++;notifyAll();}
 public void start(){Thread accept=new Thread(()->{while(!closed)try{Socket socket=listener.accept();clients.add(socket);try{workers.execute(()->serve(socket));}catch(RejectedExecutionException e){clients.remove(socket);socket.close();}}catch(IOException e){if(!closed)close();}},"phone-listener");accept.setDaemon(true);accept.start();}
 public static String quote(String text){StringBuilder b=new StringBuilder("\"");for(int i=0;i<text.length();i++){char c=text.charAt(i);switch(c){case '\\':b.append("\\\\");break;case '"':b.append("\\\"");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format(Locale.US,"\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();}
 private synchronized String state(){return "{\"revision\":"+revision+",\"focus\":"+quote(focus)+",\"clipboard\":"+quote(clipboard)+",\"sensitive\":"+sensitive+",\"result\":"+quote(result)+"}";}
 private synchronized int pair(String candidate){long now=System.nanoTime();if(now<lockedUntil)return 429;if(now>pinDeadline)return 410;if(!MessageDigest.isEqual(pin.getBytes(StandardCharsets.UTF_8),candidate.getBytes(StandardCharsets.UTF_8))){if(++failures>=5){lockedUntil=now+TimeUnit.SECONDS.toNanos(30);failures=0;return 429;}return 401;}if(paired())return 409;byte[] bytes=new byte[32];random.nextBytes(bytes);StringBuilder b=new StringBuilder();for(byte n:bytes)b.append(String.format(Locale.US,"%02x",n&255));session=b.toString();failures=0;lastSeen=now;clipboard="";sensitive=false;revision++;notifyAll();return 200;}
 private synchronized boolean authorized(String key){boolean ok=!session.isEmpty()&&MessageDigest.isEqual(session.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8));if(ok)lastSeen=System.nanoTime();return ok;}
 private static String line(InputStream input)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=input.read())!=-1){if(c=='\n')break;if(c!='\r')b.write(c);if(b.size()>8192)throw new IOException("header");}return c==-1&&b.size()==0?null:b.toString("ISO-8859-1");}
 private static String body(InputStream in,Map<String,String> h)throws IOException{if(h.containsKey("transfer-encoding"))throw new IOException("不支持分块请求");if(!h.getOrDefault("content-type","").toLowerCase(Locale.US).startsWith("text/plain"))throw new IOException("请发送文本");int length;try{length=Integer.parseInt(h.getOrDefault("content-length","-1"));}catch(Exception e){throw new IOException("内容长度无效");}if(length<0||length>262144)throw new IOException("内容过长");byte[] b=new byte[length];new DataInputStream(in).readFully(b);String text=new String(b,StandardCharsets.UTF_8);if(text.length()>65536)throw new IOException("最多支持 65536 个字符");return text;}
 private void serve(Socket client){try{Socket socket=client;socket.setSoTimeout(8000);InputStream in=socket.getInputStream();String first=line(in);if(first==null)return;String[] request=first.split(" ");if(request.length!=3){reply(socket,400,"text/plain","请求格式无效");return;}Map<String,String> h=new HashMap<>();String l;int size=first.length();while((l=line(in))!=null&&!l.isEmpty()){size+=l.length();int colon=l.indexOf(':');if(size>16384||colon<1||h.put(l.substring(0,colon).trim().toLowerCase(Locale.US),l.substring(colon+1).trim())!=null){reply(socket,400,"text/plain","请求头无效");return;}}
  String host=h.getOrDefault("host","");if(!authorities.contains(host)){reply(socket,403,"text/plain","地址无效");return;}
  String origin=h.get("origin");if(origin!=null&&!origin.equals("http://"+host)){reply(socket,403,"text/plain","来源无效");return;}
  String path=request[1];boolean post="POST".equals(request[0]);
  if("GET".equals(request[0])&&path.equals("/")){reply(socket,200,"text/html; charset=utf-8",page);return;}
  if(post&&path.equals("/api/pair")){int code=pair(body(in,h).trim());String payload; synchronized(this){payload=code==200?"{\"session\":"+quote(session)+"}":"{\"error\":"+quote(code==429?"连续输错，请等待 30 秒":code==409?"已有手机连接，请在车机解除连接":code==410?"连接码已过期，请在车机刷新":"连接码错误")+"}";}reply(socket,code,"application/json",payload);return;}
  if(!(path.startsWith("/api/events?")||path.equals("/api/focus")||path.equals("/api/clipboard")||path.equals("/api/disconnect"))){reply(socket,404,"text/plain","没有此功能");return;}
  String key=h.getOrDefault("x-starbox-session","");if(!authorized(key)){reply(socket,401,"application/json","{\"error\":\"连接已失效，请重新扫码\"}");return;}
  if("GET".equals(request[0])&&path.startsWith("/api/events?")){long since;try{since=Long.parseLong(path.substring(path.indexOf('?')+1).replace("since=",""));}catch(Exception e){reply(socket,400,"text/plain","接收请求无效，请重新连接");return;}if(!polling.compareAndSet(false,true)){reply(socket,409,"text/plain","正在接收内容，请勿重复打开页面");return;}String data;try{synchronized(this){long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!closed&&revision<=since&&session.equals(key)){long remaining=deadline-System.nanoTime();if(remaining<=0)break;TimeUnit.NANOSECONDS.timedWait(this,remaining);}if(closed||!session.equals(key)){reply(socket,401,"text/plain","连接已结束");return;}lastSeen=System.nanoTime();data=state();}}finally{polling.set(false);}reply(socket,200,"application/json; charset=utf-8",data);return;}
  if(!post){reply(socket,405,"text/plain","请求方式无效");return;}
  if(path.equals("/api/disconnect")){resetPairing();reply(socket,200,"application/json","{\"ok\":true}");return;}
  String text=body(in,h);if(text.isEmpty()){reply(socket,400,"application/json","{\"error\":\"请先输入内容\"}");return;}
  String expected=URLDecoder.decode(h.getOrDefault("x-starbox-target",""),"UTF-8");if(path.equals("/api/focus")&&!expected.equals(focusLabel())){reply(socket,409,"application/json; charset=utf-8","{\"error\":\"当前输入框已切换，请确认后重新发送\"}");return;}String message;try{message=target.send(text,path.equals("/api/focus"),expected);}catch(Exception e){reply(socket,409,"application/json; charset=utf-8","{\"error\":"+quote(e.getMessage()==null?"发送失败，请重试":e.getMessage())+"}");return;}synchronized(this){result=message;revision++;notifyAll();}reply(socket,200,"application/json; charset=utf-8","{\"ok\":true,\"message\":"+quote(message)+"}");
 }catch(Exception e){try{reply(client,400,"application/json","{\"error\":"+quote(e.getMessage()==null?"发送失败，请重试":e.getMessage())+"}");}catch(Exception ignored){}}finally{clients.remove(client);try{client.close();}catch(Exception ignored){}}}
 private static void reply(Socket s,int status,String type,String body)throws IOException{reply(s,status,type,body.getBytes(StandardCharsets.UTF_8));}
 private static void reply(Socket s,int status,String type,byte[] body)throws IOException{OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 "+status+" Result\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();}
 public void close(){closed=true;synchronized(this){session="";clipboard="";revision++;notifyAll();}try{listener.close();}catch(Exception ignored){}synchronized(clients){for(Socket s:clients)try{s.close();}catch(Exception ignored){}clients.clear();}workers.shutdownNow();}
}
