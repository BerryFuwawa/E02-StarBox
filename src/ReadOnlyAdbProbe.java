package com.e02.rootconsole;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Only CNXN and fixed read-only identity. Does not authorize, restart adbd or change its configuration. */
public final class ReadOnlyAdbProbe {
    static final int CNXN=0x4e584e43, AUTH=0x48545541, OPEN=0x4e45504f;
    static final int OKAY=0x59414b4f, WRTE=0x45545257, CLSE=0x45534c43;
    static final int VERSION=0x01000000, MAX_PAYLOAD=4096, LOCAL=1;
    public enum Status { ROOT_READY, SHELL_ONLY, OTHER_UID, AUTH_REQUIRED, SERVICE_DENIED, UNAVAILABLE }
    public static final class Result {
        public final Status status;
        public final int uid;
        public final String detail;
        Result(Status status, int uid, String detail) { this.status=status;this.uid=uid;this.detail=detail; }
        public String toString() { return status+" uid="+uid+" "+detail; }
    }
    static final class Packet {
        final int command, arg0, arg1;
        final byte[] payload;
        Packet(int command,int arg0,int arg1,byte[] payload) { this.command=command;this.arg0=arg0;this.arg1=arg1;this.payload=payload; }
    }
    static int checksum(byte[] bytes) { int sum=0;for(byte b:bytes)sum+=b&255;return sum; }
    static void send(OutputStream stream,int command,int a,int b,byte[] payload)throws IOException {
        DataOutputStream out=new DataOutputStream(stream);
        for(int n:new int[]{command,a,b,payload.length,checksum(payload),command^0xffffffff})out.writeInt(Integer.reverseBytes(n));
        out.write(payload);out.flush();
    }
    static Packet read(InputStream stream)throws IOException {
        DataInputStream in=new DataInputStream(stream);
        int cmd=Integer.reverseBytes(in.readInt()),a=Integer.reverseBytes(in.readInt()),b=Integer.reverseBytes(in.readInt());
        int length=Integer.reverseBytes(in.readInt()),sum=Integer.reverseBytes(in.readInt()),magic=Integer.reverseBytes(in.readInt());
        if(magic!=(cmd^0xffffffff)||length<0||length>MAX_PAYLOAD)throw new IOException("Invalid ADB header");
        byte[] payload=new byte[length];in.readFully(payload);
        if(checksum(payload)!=sum)throw new IOException("Invalid ADB checksum");
        return new Packet(cmd,a,b,payload);
    }
    static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static Packet receive(Socket socket,long deadline)throws IOException {
        long remaining=(deadline-System.nanoTime())/1000000;
        if(remaining<=0)throw new SocketTimeoutException("Probe deadline reached");
        socket.setSoTimeout((int)Math.min(remaining,2000));
        return read(new DeadlineInput(socket,deadline));
    }
    /** A per-read deadline also bounds peers that keep sending partial headers or payloads. */
    private static final class DeadlineInput extends FilterInputStream {
        private final Socket socket;private final long deadline;
        DeadlineInput(Socket socket,long deadline)throws IOException { super(socket.getInputStream());this.socket=socket;this.deadline=deadline; }
        private void bound()throws IOException { long remaining=(deadline-System.nanoTime())/1000000;if(remaining<=0)throw new SocketTimeoutException("ADB deadline reached");socket.setSoTimeout((int)Math.min(remaining,2000)); }
        @Override public int read()throws IOException { bound();int value=in.read();bound();return value; }
        @Override public int read(byte[] bytes,int offset,int length)throws IOException { bound();int count=in.read(bytes,offset,length);bound();return count; }
    }
    public static Result probe(int port) {
        if(port<1||port>65535)throw new IllegalArgumentException("Invalid port");
        long deadline=System.nanoTime()+5000000000L;
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1",port),1500);
            OutputStream out=socket.getOutputStream();
            send(out,CNXN,VERSION,MAX_PAYLOAD,text("host::\0"));
            Packet hello=receive(socket,deadline);
            if(hello.command==AUTH)return new Result(Status.AUTH_REQUIRED,-1,"Authorization required; no key sent");
            if(hello.command!=CNXN||hello.arg0<VERSION||hello.arg1<1||!new String(hello.payload,StandardCharsets.UTF_8).startsWith("device::"))throw new IOException("Unexpected device handshake");
            byte[] service=text("shell:id -u\0");if(service.length>Math.min(hello.arg1,MAX_PAYLOAD))throw new IOException("ADB service request too long");
            send(out,OPEN,LOCAL,0,service);
            int remote=0;
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            for(int count=0;count<32;count++) {
                Packet p=receive(socket,deadline);
                if(p.command==CLSE&&remote==0&&p.arg1==LOCAL)return new Result(Status.SERVICE_DENIED,-1,"Read-only service unavailable");
                if(p.command==OKAY&&remote==0&&p.arg0>0&&p.arg1==LOCAL&&p.payload.length==0) { remote=p.arg0;continue; }
                if(remote==0||p.arg0!=remote||p.arg1!=LOCAL)throw new IOException("Mismatched ADB stream");
                if(p.command==WRTE) {
                    if(output.size()+p.payload.length>64)throw new IOException("Unexpected UID output length");
                    output.write(p.payload);send(out,OKAY,LOCAL,remote,new byte[0]);continue;
                }
                if(p.command==CLSE&&p.payload.length==0) {
                    send(out,CLSE,LOCAL,remote,new byte[0]);
                    String value=new String(output.toByteArray(),StandardCharsets.US_ASCII).trim();
                    if(!value.matches("[0-9]{1,8}"))throw new IOException("UID not verified");
                    int uid=Integer.parseInt(value);
                    return new Result(uid==0?Status.ROOT_READY:uid==2000?Status.SHELL_ONLY:Status.OTHER_UID,uid,"Read-only UID verified");
                }
                throw new IOException("Unexpected ADB command");
            }
            throw new IOException("Too many ADB frames");
        } catch(IOException e) { return new Result(Status.UNAVAILABLE,-1,e.getClass().getSimpleName()); }
    }
    public static void main(String[] args) { if(args.length!=1)throw new IllegalArgumentException("Expected local forwarded port");System.out.println(probe(Integer.parseInt(args[0]))); }
}
