package com.e02.rootconsole;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Owns one ordinary frpc process. Waiting never holds a wake lock or alarm. */
final class FrpcSupervisor {
    interface Driver {
        void prepare() throws IOException;
        Process launch(boolean verify) throws IOException;
        void line(String line);
        void connecting();
        void retry(int exit, long delayMillis);
    }
    private final Object lock=new Object();
    private final long[] delays;
    private final long verifyTimeoutMillis, stableMillis;
    private boolean stopped, started;
    private long revision;
    private Process child;

    FrpcSupervisor(){this(new long[]{2000,5000,10000,20000,30000,60000},20000,60000);}
    FrpcSupervisor(long[] delays,long verifyTimeoutMillis,long stableMillis){
        if(delays==null||delays.length==0||verifyTimeoutMillis<1||stableMillis<1)throw new IllegalArgumentException();
        this.delays=delays.clone();for(long delay:this.delays)if(delay<1)throw new IllegalArgumentException();
        this.verifyTimeoutMillis=verifyTimeoutMillis;this.stableMillis=stableMillis;
    }
    boolean stopped(){synchronized(lock){return stopped;}}
    void stop(){synchronized(lock){stopped=true;if(child!=null)child.destroy();lock.notifyAll();}}
    void networkChanged(){synchronized(lock){if(stopped)return;revision++;if(child!=null)child.destroy();lock.notifyAll();}}
    private boolean current(long ticket){synchronized(lock){return !stopped&&ticket==revision;}}
    private Process launch(Driver driver,boolean verify,long ticket)throws IOException {
        synchronized(lock){
            if(!current(ticket))return null;
            if(child!=null)throw new IOException("上次远程连接尚未停止");
            // Stop/network-change cannot slip between process creation and ownership.
            child=driver.launch(verify);return child;
        }
    }
    void run(Driver driver)throws IOException,InterruptedException {
        synchronized(lock){if(started)throw new IllegalStateException("远程连接已在运行");started=true;}
        int failures=0;
        while(true){
            long ticket;synchronized(lock){if(stopped)return;ticket=revision;}
            try {
                driver.prepare();
                Process verifier=launch(driver,true,ticket);if(verifier==null){failures=0;continue;}
                int checked=consume(verifier,true,driver,ticket);
                if(!current(ticket)){failures=0;continue;}
                if(checked!=0)throw new IOException("远程配置检查失败，详情见日志");
            } catch(IOException error){
                if(stopped())return;
                if(!current(ticket)){failures=0;continue;}
                // Bad configuration, unavailable executable or unwritable config is
                // actionable: do not spin indefinitely on a failed verification.
                throw error;
            }
            if(!current(ticket)){failures=0;continue;}
            driver.connecting();
            long since=System.nanoTime();int exit=-1;
            try {
                Process client=launch(driver,false,ticket);if(client==null){failures=0;continue;}
                exit=consume(client,false,driver,ticket);
            } catch(IOException runtimeFailure){
                if(current(ticket))driver.line("远程连接暂时中断，正在准备重试");
            }
            if(stopped())return;
            if(!current(ticket)){failures=0;continue;}
            if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-since)>=stableMillis)failures=0;
            long delay=delays[Math.min(failures,delays.length-1)];
            if(failures<delays.length-1)failures++;
            driver.retry(exit,delay);
            await(ticket,delay);
        }
    }
    private void await(long ticket,long delay)throws InterruptedException {
        long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(delay);
        synchronized(lock){
            while(current(ticket)){
                long left=until-System.nanoTime();if(left<=0)return;
                TimeUnit.NANOSECONDS.timedWait(lock,left);
            }
        }
    }
    private int consume(Process process,boolean verify,Driver driver,long ticket)throws IOException,InterruptedException {
        AtomicReference<IOException> readError=new AtomicReference<>();
        Thread reader=new Thread(()->{
            try(BufferedReader input=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){
                String line;while((line=input.readLine())!=null)if(current(ticket))driver.line(line);
            }catch(IOException error){if(current(ticket)){readError.set(error);process.destroy();}}
        },"starbox-frpc-output");
        reader.setDaemon(true);reader.start();
        try {
            if(verify&&!process.waitFor(verifyTimeoutMillis,TimeUnit.MILLISECONDS))throw new IOException("远程配置检查超时，请查看日志后重试");
            int exit=process.waitFor();
            reader.join(1000);
            IOException error=readError.get();if(error!=null&&current(ticket))throw error;
            return exit;
        }finally{
            // A replacement is forbidden until the previous owned process is gone.
            if(process.isAlive())process.destroy();
            if(!process.waitFor(1500,TimeUnit.MILLISECONDS)){
                process.destroyForcibly();
                if(!process.waitFor(1500,TimeUnit.MILLISECONDS)){
                    stop();throw new IOException("远程连接组件未能停止，请重新打开星匣");
                }
            }
            try{process.getInputStream().close();}catch(IOException ignored){}
            reader.join(500);
            synchronized(lock){if(child==process)child=null;}
        }
    }
}
