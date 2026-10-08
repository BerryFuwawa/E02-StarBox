package com.e02.rootconsole;
import java.io.*;
import java.net.*;
import java.util.*;
public final class TestUpdateHttp {
 static class Response {int code;byte[] body;String redirect;boolean hang;Response(int code,byte[] body){this.code=code;this.body=body;}}
 static Queue<Response> replies=new ArrayDeque<>();static boolean sawRange;static int checks;
 static void require(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url){Response response=replies.remove();return new HttpURLConnection(url){volatile boolean closed;public void disconnect(){closed=true;}public boolean usingProxy(){return false;}public void connect(){}public int getResponseCode()throws IOException{sawRange|="bytes=0-4095".equals(getRequestProperty("Range"));while(response.hang&&!closed)try{Thread.sleep(2);}catch(InterruptedException e){throw new IOException(e);}if(closed)throw new IOException("cancelled");return response.code;}public String getHeaderField(String name){return name.equals("Location")?response.redirect:null;}public InputStream getInputStream(){return new ByteArrayInputStream(response.body);}};}}:null);
  try(UpdateHttp http=new UpdateHttp()){replies.add(new Response(206,new byte[]{'P','K',3,4}));http.probe("", "https://github.com/BerryFuwawa/E02-StarBox/releases/download/v1.7.3/E02-Starbox-v1.7.3.apk");require(sawRange,"APK probe uses a small GET range");}
  try(UpdateHttp http=new UpdateHttp()){replies.add(new Response(200,"<html>error</html>".getBytes("UTF-8")));try{http.probe("", "https://github.com/BerryFuwawa/E02-StarBox/releases/download/v1.7.3/E02-Starbox-v1.7.3.apk");throw new AssertionError("HTML masquerading as APK");}catch(IOException expected){checks++;}}
  try(UpdateHttp http=new UpdateHttp()){Response response=new Response(302,new byte[0]);response.redirect="http://unsafe.example/file";replies.add(response);try{http.open(UpdatePolicy.MANIFEST,false);throw new AssertionError("downgrade to HTTP");}catch(IOException expected){checks++;}}
  try(UpdateHttp http=new UpdateHttp()){replies.add(new Response(404,new byte[0]));try{http.manifest("");throw new AssertionError("404 must fail");}catch(IOException expected){checks++;}}
  try(UpdateHttp http=new UpdateHttp()){replies.add(new Response(200,new byte[65537]));try{http.manifest("");throw new AssertionError("unbounded metadata");}catch(IOException expected){checks++;}}
  try(UpdateHttp http=new UpdateHttp();Closeable alarm=http.deadline(30)){Response response=new Response(200,new byte[0]);response.hang=true;replies.add(response);long start=System.nanoTime();try{http.manifest("");throw new AssertionError("deadline must interrupt");}catch(IOException expected){require((System.nanoTime()-start)/1000000<1000,"whole-request deadline stops a stalled request");}}
  require(replies.isEmpty(),"all HTTP fixtures consumed");System.out.println("Update HTTPS: "+checks+" checks passed");
 }
}
