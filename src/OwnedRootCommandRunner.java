package com.e02.rootconsole;

import android.system.Os;
import android.system.ErrnoException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Android-only root runner. It never kills by package name, UID, or a global process pattern. */
final class OwnedRootCommandRunner extends CommandRunner {
    private static final String KEY="E02_STARBOX_COMMAND";
    private volatile OwnedProcessFamily family;
    private volatile boolean clean=true;
    OwnedRootCommandRunner(){super("/system/bin/sh","/system/bin/sh");}
    @Override protected Process start(String command,boolean root)throws IOException {
        if(!root)throw new IOException("该连接仅用于已授权的 Root 命令");
        if(!clean)throw new IOException("上次命令清理未完成，请先结束提权");
        final String marker=RootIdentity.challenge();
        String gate="printf '%s\\n' \"$$\"; IFS= read -r e02_allow || exit 125; [ \"$e02_allow\" = \"$"+KEY+"\" ] || exit 126; exec /system/bin/sh -c \"$1\"";
        ProcessBuilder builder=new ProcessBuilder("/system/bin/setsid","/system/bin/sh","-c",gate,"e02-command",command);
        builder.environment().put(KEY,marker);Process process=builder.start();
        FutureTask<Integer> handshake=new FutureTask<>(()->{
            StringBuilder line=new StringBuilder();int value;
            while((value=process.getInputStream().read())!=-1&&value!='\n'){if(value<'0'||value>'9'||line.length()>=10)throw new IOException("命令进程响应无效");line.append((char)value);}
            if(value!='\n'||line.length()==0)throw new IOException("命令进程未就绪");return Integer.parseInt(line.toString());
        });Thread reader=new Thread(handshake,"owned-command-ready");reader.setDaemon(true);reader.start();
        try{
            int pid=handshake.get(1500,TimeUnit.MILLISECONDS);ProcDriver driver=new ProcDriver(marker);
            OwnedProcessFamily next=new OwnedProcessFamily(driver,driver.read(pid),android.os.Process.myPid());family=next;
            if(isClosed())throw new IOException("命令连接已关闭");
            process.getOutputStream().write((marker+"\n").getBytes(StandardCharsets.US_ASCII));process.getOutputStream().flush();return process;
        }catch(Exception error){
            // No user command runs until the verified process receives the owner marker.
            try{process.getOutputStream().close();}catch(IOException ignored){}
            OwnedProcessFamily current=family;if(current!=null)try{if(!current.stop())clean=false;}catch(IOException ignored){clean=false;}
            super.terminate(process);handshake.cancel(true);throw new IOException("无法建立可清理的命令进程",error);
        }
    }
    @Override protected void terminate(Process process){
        OwnedProcessFamily current=family;
        if(current!=null)try{if(!current.stop())clean=false;}catch(IOException ignored){clean=false;}
        super.terminate(process);
    }
    @Override protected void finish(Process process,Result result){
        OwnedProcessFamily current=family;
        try{if(current!=null&&!current.stop())clean=false;if(!clean)result.error="命令已结束，但部分后台进程未能停止，请结束提权后检查";}
        catch(IOException error){clean=false;result.error="命令清理未完成，请结束提权后检查";}
        finally{if(process.isAlive())super.terminate(process);family=null;}
    }
    @Override boolean cleanupComplete(){return clean;}
    @Override public void close(){super.close();OwnedProcessFamily current=family;if(current!=null)try{if(!current.stop())clean=false;}catch(IOException failure){clean=false;}}
    private static final class ProcDriver implements OwnedProcessFamily.Driver {
        final byte[] marker;ProcDriver(String value){marker=(KEY+"="+value).getBytes(StandardCharsets.US_ASCII);}
        public OwnedProcessFamily.Member read(int pid)throws IOException {
            File directory=new File("/proc/"+pid);
            try{
                String stat=new String(bytes(new File(directory,"stat"),8192),StandardCharsets.US_ASCII);
                String status=new String(bytes(new File(directory,"status"),65536),StandardCharsets.US_ASCII);int uid=-1;
                for(String line:status.split("\n"))if(line.startsWith("Uid:")){uid=Integer.parseInt(line.substring(4).trim().split("\\s+")[0]);break;}
                byte[] env;try{env=bytes(new File(directory,"environ"),131072);}catch(IOException unreadable){env=new byte[0];}boolean marked=false;
                for(int begin=0;begin<env.length;){int end=begin;while(end<env.length&&env[end]!=0)end++;if(end-begin==marker.length){boolean match=true;for(int i=0;i<marker.length;i++)if(env[begin+i]!=marker[i]){match=false;break;}if(match){marked=true;break;}}begin=end+1;}
                return OwnedProcessFamily.parse(stat,uid,marked);
            }catch(FileNotFoundException gone){if(!directory.exists())return null;throw gone;}
            catch(NumberFormatException invalid){throw new IOException("无法读取命令进程身份",invalid);}
        }
        public List<OwnedProcessFamily.Member> scan()throws IOException {
            File[] paths=new File("/proc").listFiles();if(paths==null)throw new IOException("无法检查命令后台进程");List<OwnedProcessFamily.Member> found=new ArrayList<>();
            for(File path:paths)if(path.getName().matches("[1-9][0-9]*"))try{OwnedProcessFamily.Member m=read(Integer.parseInt(path.getName()));if(m!=null)found.add(m);}catch(IOException ignored){/* Unreadable foreign processes never authorize a signal. */}
            return found;
        }
        public void signal(int pid,int signal)throws IOException {try{Os.kill(pid,signal);}catch(ErrnoException error){if(error.errno!=3)throw new IOException("无法停止命令后台进程",error);}}
        public void pause()throws InterruptedException{Thread.sleep(120);}
        private static byte[] bytes(File file,int limit)throws IOException {
            try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("进程信息过长");out.write(buffer,0,count);}return out.toByteArray();
            }
        }
    }
}
