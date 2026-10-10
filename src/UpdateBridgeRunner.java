package com.e02.rootconsole;

import java.io.*;
import java.net.*;

/** Only three fixed update operations can use the longer managed task deadline. */
final class UpdateBridgeRunner extends BridgeRunner implements AutoCloseable {
    private final String secret,operation,legacyCommand;private final boolean legacyAllowed;
    private volatile Socket updateSocket;private volatile boolean ended;
    UpdateBridgeRunner(String secret,String operation,String apk,boolean legacyAllowed)throws IOException {
        super(secret,false);ManagedUpdate.timeout(operation);this.secret=secret;this.operation=operation;this.legacyAllowed=legacyAllowed;
        // Compatibility with the installed older service, which has no command-family cleanup.
        this.legacyCommand=ManagedUpdate.command(apk,operation).replace(" exec /system/bin/app_process "," /system/bin/app_process ")+" > '/data/user/0/com.e02.rootconsole/files/update-task/worker.log' 2>&1 < /dev/null &";
    }
    private String features()throws IOException {
        try(Socket socket=new Socket()){updateSocket=socket;socket.connect(new InetSocketAddress("127.0.0.1",8876),700);socket.setSoTimeout(900);DataOutputStream out=new DataOutputStream(socket.getOutputStream());out.writeUTF(secret);out.writeUTF("features");out.flush();return new DataInputStream(socket.getInputStream()).readUTF();}finally{updateSocket=null;}
    }
    @Override public Result execute(String ignored,boolean root,int ignoredSeconds){
        Result result=new Result();if(ended||!root){result.error="更新授权连接已结束";return result;}
        String protocol;
        try{protocol=features();}
        catch(IOException old){
            if(!legacyAllowed){result.error="更新连接不可用，请重新检测 Root";return result;}
            // A new legacy-mode server must not accidentally take the old detached path.
            try{BridgeRunner.identity(secret,null);result.error="当前授权不支持更新，请重新启用独立授权";return result;}catch(IOException absent){}
            if(ended){result.error="更新授权连接已结束";return result;}return super.execute(legacyCommand,true,10);
        }
        if(!"UPDATE1".equals(protocol)){result.error="当前授权不支持更新，请重新启用独立授权";return result;}
        try(Socket socket=new Socket()){
            updateSocket=socket;if(ended)throw new IOException("更新已取消");socket.connect(new InetSocketAddress("127.0.0.1",8876),700);socket.setSoTimeout((ManagedUpdate.timeout(operation)+5)*1000);
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());out.writeUTF(secret);out.writeUTF("update");out.writeUTF(operation);out.flush();DataInputStream in=new DataInputStream(socket.getInputStream());
            result.exit=in.readInt();result.timedOut=in.readBoolean();result.truncated=in.readBoolean();result.millis=in.readLong();result.stdout=readText(in);result.stderr=readText(in);result.error=readText(in);
        }catch(IOException failed){result.error="更新任务连接已中断，请查看实际更新状态";}finally{updateSocket=null;}return result;
    }
    @Override public void close(){ended=true;Socket socket=updateSocket;if(socket!=null)try{socket.close();}catch(IOException ignored){}super.close();}
}
