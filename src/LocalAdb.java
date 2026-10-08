package com.e02.rootconsole;
import java.net.*;
import java.io.*;
/** Recognize adbd's CNXN/AUTH response, without granting authorization. */
public final class LocalAdb {
 public static boolean available(){return available(5555);}
 static boolean available(int port){try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),700);socket.setSoTimeout(1200);byte[] body=new byte[]{'h','o','s','t',':',':',0};int sum=0;for(byte b:body)sum+=b&255;DataOutputStream out=new DataOutputStream(socket.getOutputStream());for(int n:new int[]{0x4e584e43,0x01000000,4096,body.length,sum,0x4e584e43^0xffffffff})out.writeInt(Integer.reverseBytes(n));out.write(body);out.flush();DataInputStream in=new DataInputStream(socket.getInputStream());int cmd=Integer.reverseBytes(in.readInt());in.readInt();in.readInt();int length=Integer.reverseBytes(in.readInt());in.readInt();int magic=Integer.reverseBytes(in.readInt());if((cmd^0xffffffff)!=magic||length<0||length>4096)return false;byte[] response=new byte[length];in.readFully(response);return cmd==0x4e584e43||cmd==0x48545541;}catch(IOException e){return false;}}
}
