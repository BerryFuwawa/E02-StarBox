package com.e02.rootconsole;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class TestPhone {
 static int passed;static String session="",expectedOverride;static PhoneServer server;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;}
 static String[] request(String method,String path,String body,String origin,String host)throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+server.port()+path).openConnection();c.setReadTimeout(25000);c.setConnectTimeout(3000);c.setRequestMethod(method);c.setRequestProperty("X-Starbox-Session",session);c.setRequestProperty("X-Starbox-Target",URLEncoder.encode(expectedOverride==null?server.focusLabel():expectedOverride,"UTF-8"));if(origin!=null)c.setRequestProperty("Origin",origin);if(host!=null)c.setRequestProperty("Host",host);
  if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","text/plain;charset=UTF-8");byte[] data=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream out=c.getOutputStream()){out.write(data);}}
  int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null)try(InputStream source=in){byte[] data=new byte[4096];int n;while((n=source.read(data))!=-1)out.write(data,0,n);}c.disconnect();return new String[]{String.valueOf(code),out.toString("UTF-8")};
 }
 static String[] get(String path)throws Exception{return request("GET",path,null,null,null);}
 static String[] post(String path,String body)throws Exception{return request("POST",path,body,null,null);}
 public static void main(String[] args)throws Exception{
  AtomicReference<String> received=new AtomicReference<>();AtomicBoolean available=new AtomicBoolean();
  server=new PhoneServer(0,"phone page".getBytes(StandardCharsets.UTF_8),(text,focus,expected)->{if(focus&&!available.get())throw new IOException("没有焦点");received.set(text);return focus?"已填写 Token":"已发送到剪贴板";});server.setHosts(Arrays.asList("127.0.0.1"));server.start();
  try{
   check(get("/")[0].equals("200"),"page");check(get("/api/run")[0].equals("404"),"no command endpoint");check(get("/api/events?since=-1")[0].equals("401"),"unauthorized events");check(request("POST","/api/pair",server.pin(),"http://evil.example",null)[0].equals("403"),"cross origin pair");check(request("GET","/",null,null,"evil.example")[0].equals("403"),"host guard");
   String wrong=server.pin().equals("9999")?"9998":"9999";check(post("/api/pair",wrong)[0].equals("401"),"wrong PIN");String[] paired=post("/api/pair",server.pin());check(paired[0].equals("200"),"pair");session=paired[1].split("\"")[3];check(session.length()==64,"strong session");check(server.paired(),"connected");check(post("/api/pair",server.pin())[0].equals("409"),"one phone");
   check(post("/api/focus","token")[0].equals("409"),"no focus truthful error");available.set(true);server.focus("远程 → Token");String state=get("/api/events?since=-1")[1];check(state.contains("远程 → Token"),"target label");check(post("/api/focus","a\"b\n中😀")[0].equals("200")&&received.get().equals("a\"b\n中😀"),"UTF-8 target text");check(post("/api/clipboard","clipboard")[0].equals("200")&&received.get().equals("clipboard"),"phone to clipboard");check(post("/api/focus","")[0].equals("400"),"empty rejected");
   expectedOverride="旧焦点";String previous=received.get();check(post("/api/focus","wrong target")[0].equals("409")&&previous.equals(received.get()),"changed target does not overwrite input");expectedOverride=null;
   String large=String.join("",Collections.nCopies(65536,"中"));check(post("/api/focus",large)[0].equals("200")&&received.get().length()==65536,"long config");check(post("/api/focus",large+"x")[0].equals("400"),"oversize rejected");
   state=get("/api/events?since=-1")[1];long revision=Long.parseLong(state.split("\"revision\":")[1].split(",")[0]);ExecutorService pool=Executors.newSingleThreadExecutor();try{Future<String[]> event=pool.submit(()->get("/api/events?since="+revision));Thread.sleep(200);server.copied("secret\n\"<script>",true);String[] data=event.get(3,TimeUnit.SECONDS);check(data[0].equals("200")&&data[1].contains("\\n\\\"<script>")&&data[1].contains("\"sensitive\":true"),"event notification and JSON escaping");}finally{pool.shutdownNow();}
   check(request("POST","/api/clipboard","bad","http://evil.example",null)[0].equals("403"),"cross origin send");String oldSession=session;check(post("/api/disconnect","")[0].equals("200"),"disconnect");check(get("/api/events?since=-1")[0].equals("401"),"old session revoked");check(!server.paired(),"disconnect status");
   for(int i=0;i<4;i++)post("/api/pair",server.pin().equals("9999")?"9998":"9999");check(post("/api/pair",server.pin().equals("9999")?"9998":"9999")[0].equals("429"),"PIN attempt limit");check(post("/api/pair",server.pin())[0].equals("429"),"lock also blocks correct PIN");
   check(RemoteConfig.client("example.com",7000,"token","1234567890123456","192.168.1.2",8888,true,true).contains("localPort = 8888"),"actual FRP console port");
   System.out.println("Phone companion: "+passed+" checks passed.");
  }finally{server.close();}
 }
}
