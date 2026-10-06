package com.e02.rootconsole;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

public class TestConsole {
 static int passed;static String token="0427";static ConsoleServer server;
 static void check(boolean okay,String name){if(!okay)throw new AssertionError(name);System.out.println("PASS "+name);passed++;}
 static String read(InputStream in)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}
 static void remote(String host,String origin,int expected)throws Exception{
  try(Socket s=new Socket("127.0.0.1",server.port())){s.setSoTimeout(2000);String q="GET /api/status HTTP/1.1\r\nHost: "+host+"\r\nX-E02-Token: "+token+"\r\nOrigin: "+origin+"\r\nConnection: close\r\n\r\n";s.getOutputStream().write(q.getBytes(StandardCharsets.UTF_8));check(read(s.getInputStream()).startsWith("HTTP/1.1 "+expected+" "),"remote Host / Origin "+expected);}
 }
 static String request(String path,String code,String mode,String body,int expected,String origin)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)new URL("http://127.0.0.1:"+server.port()+path).openConnection();connection.setConnectTimeout(2000);connection.setReadTimeout(38000);
  if(code!=null)connection.setRequestProperty("X-E02-Token",code);if(mode!=null)connection.setRequestProperty("X-E02-Mode",mode);if(origin!=null)connection.setRequestProperty("Origin",origin);
  if(body!=null){connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","text/plain; charset=utf-8");connection.setDoOutput(true);try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
  int status=connection.getResponseCode();check(status==expected,path+" HTTP "+expected);String result=read(status>=400?connection.getErrorStream():connection.getInputStream());connection.disconnect();return result;
 }
 public static void main(String[] args)throws Exception{
  String shell=args[0];CommandRunner runner=new CommandRunner(shell,shell);
  server=new ConsoleServer("127.0.0.1",0,token,Files.readAllBytes(Paths.get(args[1])),false,runner);server.start();
  try{
   check(request("/",null,null,null,200,null).contains("E02"),"frontend served");
   request("/api/status",null,null,null,401,null);request("/api/status","wrong",null,null,401,null);
   check(request("/api/status",token,null,null,200,null).contains("\"rootAllowed\":false"),"root permission status");
   server.setRemoteOrigin("https://console.example.test");
   remote("console.example.test","https://console.example.test",200);
   remote("evil.example.test","https://console.example.test",403);
   remote("console.example.test","http://console.example.test",403);
   server.setRemoteOrigin(null);remote("console.example.test","https://console.example.test",403);
   request("/api/run",token,"root","id",403,null);
   request("/api/run",token,"unknown","id",400,null);
   request("/api/run",token,"normal","id",403,"http://evil.invalid");
   String result=request("/api/run",token,"normal","[Console]::OutputEncoding=[Text.UTF8Encoding]::new(); Write-Output '回传测试'; [Console]::Error.WriteLine('error-marker'); exit 7",200,null);
   check(result.contains("回传测试")&&result.contains("error-marker")&&result.contains("\"exitCode\":7"),"UTF-8 stdout stderr and exit code");
   result=request("/api/run",token,"normal","Write-Output 'line-one'\nWrite-Output 'line-two'",200,null);
   check(result.contains("line-one")&&result.contains("line-two"),"multiline command");
   request("/api/run",token,"normal",new String(new char[33000]).replace('\0','a'),413,null);
   CommandRunner.Result timeout=runner.execute("Start-Sleep -Seconds 4",false,1);check(timeout.timedOut&&timeout.millis<4000,"bounded timeout");
   CommandRunner.Result large=runner.execute("[Console]::Out.Write(('x' * 160000)); [Console]::Error.Write(('e' * 160000))",false,10);check(large.truncated&&large.stdout.length()==CommandRunner.LIMIT&&large.stderr.length()==CommandRunner.LIMIT,"bounded concurrent stdout stderr");
   ExecutorService worker=Executors.newSingleThreadExecutor();Future<CommandRunner.Result> slow=worker.submit(()->runner.execute("Start-Sleep -Seconds 2",false,8));Thread.sleep(300);CommandRunner.Result overlap=runner.execute("Write-Output 'no'",false,2);check(!overlap.error.isEmpty(),"concurrent command rejected");slow.get();worker.shutdown();
   for(int i=0;i<5;i++)request("/api/status","9999",null,null,i==4?429:401,null);
   request("/api/status",token,null,null,429,null);
  }finally{server.close();}
  check(!runner.execute("Write-Output 'no'",false,2).error.isEmpty(),"closed session rejects commands");
  System.out.println("All "+passed+" checks passed (desktop shell; no vehicle root assertion).");
 }
}
