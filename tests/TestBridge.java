package com.e02.rootconsole;
import java.io.*;
import java.net.*;

public class TestBridge {
 public static void main(String[] args)throws Exception {
  String secret="0123456789abcdef0123456789abcdef";
  try(ServerSocket server=new ServerSocket()){server.bind(new InetSocketAddress("127.0.0.1",38766));server.setSoTimeout(3000);
   Thread fake=new Thread(()->{try(Socket s=server.accept()){DataInputStream in=new DataInputStream(s.getInputStream());if(!secret.equals(in.readUTF())||!in.readUTF().equals("run")||in.readInt()!=2||!BridgeRunner.readText(in).equals("id"))throw new AssertionError("request");DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeInt(7);out.writeBoolean(false);out.writeBoolean(true);out.writeLong(12);BridgeRunner.writeText(out,"root-output\n中文");BridgeRunner.writeText(out,"stderr");BridgeRunner.writeText(out,"");out.flush();}catch(Exception e){throw new RuntimeException(e);}});fake.start();
   BridgeRunner runner=new BridgeRunner(secret,false);CommandRunner.Result result=runner.execute("id",true,2);if(result.exit!=7||!result.stdout.contains("中文")||!result.stderr.equals("stderr")||!result.truncated||result.millis!=12||!result.error.isEmpty())throw new AssertionError("result");fake.join();System.out.println("PASS bridge structured output");runner.close();if(runner.execute("id",true,2).error.isEmpty())throw new AssertionError("closed");System.out.println("PASS bridge closed session");
   Thread truncated=new Thread(()->{try(Socket s=server.accept()){DataInputStream in=new DataInputStream(s.getInputStream());in.readUTF();in.readUTF();in.readInt();BridgeRunner.readText(in);DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeInt(0);out.flush();}catch(Exception e){throw new RuntimeException(e);}});truncated.start();result=new BridgeRunner(secret,false).execute("id",true,2);if(result.error.isEmpty())throw new AssertionError("partial result accepted");truncated.join();System.out.println("PASS incomplete bridge response reported");
  }
  CommandRunner.Result absent=new BridgeRunner(secret,false).execute("id",true,2);if(absent.error.isEmpty())throw new AssertionError("absent");System.out.println("PASS missing bridge reported");
 }
}
