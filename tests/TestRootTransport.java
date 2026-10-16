package com.e02.rootconsole;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;

/** Actual server with an in-memory runner. No vehicle or real command execution. */
public final class TestRootTransport {
 static final String KEY="0123456789abcdef0123456789abcdef",BODY="private-command-do-not-record",OUTPUT="private-result-do-not-record";
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("transport check "+checks);}
 static class Runner extends CommandRunner{
  volatile boolean timeout,fail,block;volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
  Runner(){super("fixture","fixture");}
  public Result execute(String command,boolean root,int seconds){calls.incrementAndGet();entered.countDown();if(block)try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}if(fail)throw new IllegalStateException("private-error-do-not-record");Result r=new Result();r.stdout=OUTPUT;r.exit=0;r.timedOut=timeout;return r;}
  public void close(){release.countDown();}
 }
 static final class Service implements AutoCloseable{
  final int port;final RootBridgeServer server;final Thread thread;final List<String> events=new CopyOnWriteArrayList<>();final AtomicBoolean allowed=new AtomicBoolean(true);final AtomicReference<Throwable> error=new AtomicReference<>();
  Service(File home,Runner runner)throws Exception{
   try(ServerSocket reserve=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=reserve.getLocalPort();}
   String generation=RootPermit.grant(home).nonce;
   server=new RootBridgeServer(KEY,0,12345,generation,runner,allowed::get,null,null,()->"ready",(id,stage,outcome,ms)->{events.add(stage+":"+outcome);RootTransportLog.record(home,id,stage,outcome,ms);});
   thread=new Thread(()->{try{server.serve(port);}catch(Throwable e){error.set(e);}});thread.start();
   long end=System.nanoTime()+2000000000L;boolean ready=false;while(System.nanoTime()<end){try{BridgeRunner.identityAt(KEY,generation,port);ready=true;break;}catch(IOException e){Thread.sleep(5);}}check(ready);
  }
  Socket request(String key,String op)throws IOException{Socket s=new Socket("127.0.0.1",port);s.setSoTimeout(2500);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(key);out.writeUTF(op);out.flush();return s;}
  void waitFor(String event,int from)throws Exception{long end=System.nanoTime()+3000000000L;while(System.nanoTime()<end){if(events.subList(Math.min(from,events.size()),events.size()).contains(event)){checks++;return;}Thread.sleep(5);}throw new AssertionError("Missing event "+event+": "+events);}
  public void close()throws Exception{server.close();thread.join(3000);check(!thread.isAlive()&&error.get()==null);}
 }
 static void runRequest(Socket s)throws IOException{DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeInt(2);BridgeRunner.writeText(out,BODY);out.flush();}
 static CommandRunner.Result result(Socket s)throws IOException{DataInputStream in=new DataInputStream(s.getInputStream());CommandRunner.Result r=new CommandRunner.Result();r.exit=in.readInt();r.timedOut=in.readBoolean();r.truncated=in.readBoolean();r.millis=in.readLong();r.stdout=BridgeRunner.readText(in);r.stderr=BridgeRunner.readText(in);r.error=BridgeRunner.readText(in);return r;}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("e02-transport-test-");File home=dir.toFile();
  try{
   Runner runner=new Runner();try(Service service=new Service(home,runner)){
    try(Socket s=service.request(KEY,"wake-status")){check("ready".equals(new DataInputStream(s.getInputStream()).readUTF()));}
    check(service.events.isEmpty()); // Frequent successful identity/status probes do not write disk logs.
    int from=service.events.size();try(Socket s=service.request(KEY,"run")){runRequest(s);check(result(s).stdout.equals(OUTPUT));}
    service.waitFor("REPLY:COMPLETE",from);check(service.events.contains("AUTH:COMPLETE")&&service.events.contains("REQUEST:COMPLETE")&&service.events.contains("EXECUTE:STARTED"));check(runner.calls.get()==1);
    from=service.events.size();try(Socket s=service.request("wrong-private-key","run")){check(s.getInputStream().read()==-1);}service.waitFor("AUTH:DENIED",from);check(runner.calls.get()==1);
    from=service.events.size();try(Socket s=service.request(KEY,"run")){new DataOutputStream(s.getOutputStream()).writeInt(0);check(s.getInputStream().read()==-1);}service.waitFor("REQUEST:INVALID",from);check(runner.calls.get()==1);
    from=service.events.size();try(Socket s=service.request(KEY,"run")){s.shutdownOutput();check(s.getInputStream().read()==-1);}service.waitFor("REQUEST:INCOMPLETE",from);check(runner.calls.get()==1);
    from=service.events.size();try(Socket s=new Socket("127.0.0.1",service.port)){s.setSoTimeout(2500);s.getOutputStream().write(0);s.getOutputStream().flush();check(s.getInputStream().read()==-1);}service.waitFor("AUTH:TIMEOUT",from);check(runner.calls.get()==1);
    runner.timeout=true;from=service.events.size();try(Socket s=service.request(KEY,"run")){runRequest(s);check(result(s).timedOut);}service.waitFor("EXECUTE:TIMEOUT",from);runner.timeout=false;
    runner.fail=true;from=service.events.size();try(Socket s=service.request(KEY,"run")){runRequest(s);check(s.getInputStream().read()==-1);}service.waitFor("EXECUTE:FAILED",from);runner.fail=false;check(runner.calls.get()==3); // No automatic replay.
    runner.block=true;runner.entered=new CountDownLatch(1);runner.release=new CountDownLatch(1);from=service.events.size();
    Socket gone=service.request(KEY,"run");runRequest(gone);check(runner.entered.await(2,TimeUnit.SECONDS));gone.setSoLinger(true,0);gone.close();runner.release.countDown();service.waitFor("REPLY:IO_FAILED",from);check(runner.calls.get()==4);
   }
   String raw=new String(Files.readAllBytes(dir.resolve(RootTransportLog.FILE)),StandardCharsets.US_ASCII);
   for(String privateText:new String[]{KEY,BODY,OUTPUT,"wrong-private-key","private-error-do-not-record"})check(!raw.contains(privateText));
   String shown=RootTransportLog.read(home,System.currentTimeMillis());check(shown.contains("等待超时")&&shown.contains("数据未接收完整")&&!shown.contains("IO_FAILED"));
   retention(home);
   System.out.println("PASS "+checks+" production Root transport/log checks: phase, partial/slow packets, execution timeout, dropped reply, no replay, privacy, seven-day/size bounds, quiet status probes.");
  }finally{try(java.util.stream.Stream<Path> entries=Files.list(dir)){for(Path p:(Iterable<Path>)entries::iterator)Files.delete(p);}Files.delete(dir);}
 }
 static void retention(File home)throws Exception{
  long now=System.currentTimeMillis();Path file=new File(home,RootTransportLog.FILE).toPath();
  String valid=now+" 1 AUTH COMPLETE 3\n";
  Files.write(file,((now-RootTransportLog.RETENTION-1)+" 2 AUTH COMPLETE 3\n"+(now+1)+" 3 AUTH COMPLETE 3\n"+"private-command-should-disappear\n"+valid).getBytes(StandardCharsets.US_ASCII));
  RootTransportLog.record(home,4,RootTransportLog.Stage.REPLY,RootTransportLog.Outcome.COMPLETE,4,now);
  String read=new String(Files.readAllBytes(file),StandardCharsets.US_ASCII);check(read.contains(" 1 AUTH ")&&read.contains(" 4 REPLY ")&&!read.contains(" 2 AUTH ")&&!read.contains(" 3 AUTH ")&&!read.contains("private-command"));
  StringBuilder oversized=new StringBuilder();while(oversized.length()<RootTransportLog.LIMIT+500)oversized.append(valid);Files.write(file,oversized.toString().getBytes(StandardCharsets.US_ASCII));RootTransportLog.record(home,5,RootTransportLog.Stage.REPLY,RootTransportLog.Outcome.COMPLETE,5,now);check(Files.size(file)<=RootTransportLog.LIMIT);check(new String(Files.readAllBytes(file),StandardCharsets.US_ASCII).endsWith(" 5 REPLY COMPLETE 5\n"));
  RootTransportLog.record(home,-1,RootTransportLog.Stage.AUTH,RootTransportLog.Outcome.FAILED,0,now);check(!RootTransportLog.read(home,now).contains("#-1"));
   try(java.util.stream.Stream<Path> entries=Files.list(home.toPath())){check(entries.noneMatch(p->p.getFileName().toString().startsWith("root-transport-")&&p.getFileName().toString().endsWith(".tmp")));}
 }
}
