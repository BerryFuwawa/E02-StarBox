package com.e02.rootconsole;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import static com.e02.rootconsole.ReadOnlyAdbProbe.*;

/** Fixed local-ADB bootstrap for this package. Never enables ADB or requests an adbd Root restart. */
final class RootBootstrap {
    static final class Result {
        final boolean ready;
        final String message;
        final RootIdentity identity;
        Result(boolean ready,String message,RootIdentity identity) { this.ready=ready;this.message=message;this.identity=identity; }
    }
    static Result acquire(File apk,File token,RootPermit.Ticket ticket,String secret) {
        if(!RootPermit.matches(ticket))return new Result(false,"Root 授权已结束",null);
        boolean launchIssued=false;
        try {
            File directory=token.getCanonicalFile().getParentFile();
            if(!directory.equals(ticket.file.getCanonicalFile().getParentFile())||!"bridge-token".equals(token.getName())||!apk.isFile()||!token.isFile())throw new IOException("Unexpected app files");
            if(!RootPermit.nonce(secret))throw new IOException("Invalid owned service key");
            byte[] key=new byte[32];try(DataInputStream in=new DataInputStream(new FileInputStream(token))){in.readFully(key);if(in.read()!=-1||!java.security.MessageDigest.isEqual(key,secret.getBytes(StandardCharsets.US_ASCII)))throw new IOException("Owned service key changed");}
            try(RandomAccessFile lockFile=new RandomAccessFile(new File(directory,"root-launch.lock"),"rw");FileChannel channel=lockFile.getChannel()) {
                FileLock lock;
                try { lock=channel.tryLock(); }catch(OverlappingFileLockException busy){return new Result(false,"正在获取 Root，请稍后",null);}
                if(lock==null)return new Result(false,"正在获取 Root，请稍后",null);
                try {
                    if(!RootPermit.matches(ticket))return new Result(false,"Root 授权已结束",null);
                    try { RootIdentity current=BridgeRunner.identity(secret,ticket.nonce);if(RootPermit.matches(ticket))return new Result(true,"Root 已就绪",current); }
                    catch(IOException missing) { /* Only a verified owned service is reused. */ }
                    // A prior generation may be closing; never launch over an unknown listener.
                    for(int retry=0;retry<4&&listening(8876);retry++) {
                        if(!RootPermit.matches(ticket))return new Result(false,"Root 授权已结束",null);
                        try {RootIdentity current=BridgeRunner.identity(secret,ticket.nonce);if(RootPermit.matches(ticket))return new Result(true,"Root 已就绪",current);return new Result(false,"Root 授权已结束",null);}catch(IOException pending){}
                        Thread.sleep(150);
                    }
                    if(listening(8876))return new Result(false,"已有授权服务正在运行，请先结束提权后重试",null);
                    ReadOnlyAdbProbe.Result backend=probe(5555);
                    if(backend.status!=Status.ROOT_READY||backend.uid!=0)return new Result(false,explain(backend.status),null);
                    if(!RootPermit.matches(ticket))return new Result(false,"Root 授权已结束",null);
                    String command=script(apk.getCanonicalPath(),token.getCanonicalPath(),ticket.nonce);
                    launchIssued=true;String reply=execute(command,5555);
                    if(!"E02_STARTED".equals(reply.trim()))throw new IOException("Owned service launch not acknowledged");
                    long deadline=System.nanoTime()+5000000000L;
                    do {
                        if(!RootPermit.matches(ticket)){BridgeRunner.stopManaged(secret,ticket.nonce);return new Result(false,"Root 授权已结束",null);}
                        try {
                            RootIdentity identity=BridgeRunner.identity(secret,ticket.nonce);
                            if(RootPermit.matches(ticket))return new Result(true,"Root 已就绪",identity);
                            BridgeRunner.stopManaged(secret,ticket.nonce);return new Result(false,"Root 授权已结束",null);
                        } catch(IOException pending) { Thread.sleep(150); }
                    } while(System.nanoTime()<deadline);
                    boolean cleaned=cancel(ticket,secret);
                    return new Result(false,cleaned?"Root 服务未能启动，请查看诊断结果":"Root 服务清理未完成，请查看诊断结果",null);
                } finally { lock.release(); }
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt();boolean cleaned=!launchIssued||cancel(ticket,secret);return new Result(false,cleaned?"Root 启动已取消":"Root 服务清理未完成，请查看诊断结果",null); }
        catch(Exception e) { boolean cleaned=!launchIssued||cancel(ticket,secret);return new Result(false,cleaned?"Root 服务未能启动，请查看诊断结果":"Root 服务清理未完成，请查看诊断结果",null); }
    }
    /** Revoke only this launch. Stopping one socket alone cannot exclude a late-starting child. */
    private static boolean cancel(RootPermit.Ticket ticket,String secret) {
        boolean invalidated;
        try { RootPermit.revokeIfCurrent(ticket);invalidated=!RootPermit.matches(ticket); }
        catch(IOException e) { invalidated=false; }
        BridgeRunner.stopManaged(secret,ticket.nonce);return invalidated;
    }
    static String explain(Status status) {
        switch(status) {
            case SHELL_ONLY:return "本机 ADB 已开启，但尚未提供 Root 权限";
            case AUTH_REQUIRED:return "本机 ADB 需要连接授权";
            case SERVICE_DENIED:return "本机 ADB 无法读取权限状态";
            case OTHER_UID:return "本机 ADB 未提供 Root 权限";
            default:return "本机 ADB 不可用，请先开启或使用其他授权方式";
        }
    }
    static boolean listening(int port) {
        try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),350);return true;}
        catch(IOException unavailable){return false;}
    }
    static String script(String apk,String token,String generation)throws IOException {
        if(apk==null||!apk.startsWith("/data/app/")||!apk.endsWith("/base.apk")||invalid(apk)||token==null||!token.matches("/data/(?:user/[0-9]+|user_de/[0-9]+|data)/com\\.e02\\.rootconsole/files/bridge-token")||!RootPermit.nonce(generation))throw new IOException("Unexpected app paths");
        String log=token.substring(0,token.lastIndexOf('/'))+"/bridge.log";
        return "[ \"$(id -u)\" = 0 ] || exit 1; CLASSPATH="+quote(apk)+" /system/bin/setsid /system/bin/nohup /system/bin/app_process /system/bin com.e02.rootconsole.RootBridge "+quote(token)+" --managed "+quote(generation)+" > "+quote(log)+" 2>&1 < /dev/null & /system/bin/sleep 1; echo E02_STARTED";
    }
    private static boolean invalid(String value) { return value.indexOf('\0')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0||value.contains("/../")||value.length()>1024; }
    private static String quote(String value) { return "'"+value.replace("'","'\\''")+"'"; }
    private static String execute(String command,int port)throws IOException {
        long deadline=System.nanoTime()+8000000000L;
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1",port),1500);
            OutputStream out=socket.getOutputStream();send(out,CNXN,VERSION,MAX_PAYLOAD,text("host::\0"));Packet hello=receive(socket,deadline);
            if(hello.command!=CNXN||hello.arg0<VERSION||hello.arg1<1||!new String(hello.payload,StandardCharsets.UTF_8).startsWith("device::"))throw new IOException("ADB unavailable or authorization required");
            byte[] service=text("shell:"+command+"\0");if(service.length>Math.min(hello.arg1,MAX_PAYLOAD))throw new IOException("ADB launch request too long");
            send(out,OPEN,LOCAL,0,service);int remote=0;ByteArrayOutputStream result=new ByteArrayOutputStream();
            for(int count=0;count<32;count++) {
                Packet packet=receive(socket,deadline);
                if(packet.command==OKAY&&remote==0&&packet.arg0>0&&packet.arg1==LOCAL&&packet.payload.length==0){remote=packet.arg0;continue;}
                if(remote==0||packet.arg0!=remote||packet.arg1!=LOCAL)throw new IOException("Unexpected ADB stream");
                if(packet.command==WRTE){if(result.size()+packet.payload.length>512)throw new IOException("Unexpected launch output length");result.write(packet.payload);send(out,OKAY,LOCAL,remote,new byte[0]);continue;}
                if(packet.command==CLSE&&packet.payload.length==0){send(out,CLSE,LOCAL,remote,new byte[0]);return new String(result.toByteArray(),StandardCharsets.UTF_8);}
                throw new IOException("Unexpected ADB frame");
            }
            throw new IOException("Too many ADB frames");
        }
    }
}
