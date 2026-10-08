package com.e02.rootconsole;
import java.net.*;import java.io.*;
public class TestAdbHandshake {
 public static void main(String[] args)throws Exception{for(int code:new int[]{0x48545541,0x4e584e43,0x12345678})try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){Thread t=new Thread(()->{try(Socket s=server.accept()){s.setSoTimeout(2000);new DataInputStream(s.getInputStream()).readFully(new byte[31]);DataOutputStream out=new DataOutputStream(s.getOutputStream());for(int n:new int[]{code,0,0,0,0,code^0xffffffff})out.writeInt(Integer.reverseBytes(n));out.flush();}catch(IOException e){throw new RuntimeException(e);}});t.start();boolean good=LocalAdb.available(server.getLocalPort());if(good!=(code!=0x12345678))throw new AssertionError("Protocol recognition");t.join();}System.out.println("ADB handshake: daemon vs unrelated listener PASS");}
}
