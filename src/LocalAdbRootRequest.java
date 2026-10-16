package com.e02.rootconsole;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import static com.e02.rootconsole.ReadOnlyAdbProbe.*;

/** Fixed native ADB root request on loopback. A reply is never proof of UID 0. */
final class LocalAdbRootRequest {
    enum Result { REQUESTED, DENIED, AUTH_REQUIRED, CANCELLED, UNCONFIRMED }
    static Result request(int port,BooleanSupplier allowed) {
        return request(port,"127.0.0.1",allowed);
    }
    static Result request(int port,String host,BooleanSupplier allowed) {
        if(port<1||port>65535||!loopback(host))throw new IllegalArgumentException("Expected local ADB address");
        if(!allowed.getAsBoolean())return Result.CANCELLED;
        long deadline=System.nanoTime()+5000000000L;
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress(host,port),1000);
            OutputStream out=socket.getOutputStream();send(out,CNXN,VERSION,MAX_PAYLOAD,text("host::\0"));
            Packet hello=receive(socket,deadline);
            if(hello.command==AUTH)return Result.AUTH_REQUIRED;
            if(hello.command!=CNXN||hello.arg0<VERSION||hello.arg1<1||!new String(hello.payload,StandardCharsets.UTF_8).startsWith("device::"))return Result.DENIED;
            if(text("root:\0").length>Math.min(hello.arg1,MAX_PAYLOAD))return Result.DENIED;
            if(!allowed.getAsBoolean())return Result.CANCELLED;
            send(out,OPEN,LOCAL,0,text("root:\0"));
            int remote=0;ByteArrayOutputStream reply=new ByteArrayOutputStream();
            for(int count=0;count<32;count++) {
                if(!allowed.getAsBoolean())return Result.CANCELLED;
                Packet packet=receive(socket,deadline);
                if(packet.command==CLSE&&remote==0&&packet.arg1==LOCAL)return Result.DENIED;
                if(packet.command==OKAY&&remote==0&&packet.arg0>0&&packet.arg1==LOCAL&&packet.payload.length==0){remote=packet.arg0;continue;}
                if(remote==0||packet.arg0!=remote||packet.arg1!=LOCAL)return Result.DENIED;
                if(packet.command==WRTE) {
                    if(reply.size()+packet.payload.length>1024)return Result.DENIED;
                    reply.write(packet.payload);send(out,OKAY,LOCAL,remote,new byte[0]);continue;
                }
                if(packet.command==CLSE&&packet.payload.length==0) {
                    send(out,CLSE,LOCAL,remote,new byte[0]);
                    String message=new String(reply.toByteArray(),StandardCharsets.US_ASCII);
                    return message.contains("already running as root")||message.contains("restarting adbd as root")?Result.REQUESTED:Result.DENIED;
                }
                return Result.DENIED;
            }
        } catch(IOException unavailable) { return Result.UNCONFIRMED; }
        return Result.UNCONFIRMED;
    }
}
