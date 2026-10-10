package com.e02.rootconsole;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Runs the production supervisor with real, disposable JVM child processes. */
public final class TestFrpcSupervisor {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static void await(BooleanSupplier ready)throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);
        while(!ready.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);
        check(ready.getAsBoolean());
    }
    private static class Driver implements FrpcSupervisor.Driver {
        final AtomicInteger prepares=new AtomicInteger(),verifiers=new AtomicInteger(),clients=new AtomicInteger(),ready=new AtomicInteger();
        final List<Process> processes=Collections.synchronizedList(new ArrayList<Process>());
        final List<Long> retries=Collections.synchronizedList(new ArrayList<Long>());
        final AtomicInteger maxAlive=new AtomicInteger();
        int verifyExit;boolean holdVerify;int[] exits={};boolean failFirstClient,failPrepare;
        CountDownLatch preparing,releasePrepare;
        public void prepare()throws IOException {
            int count=prepares.incrementAndGet();
            if(failPrepare)throw new IOException("fixture disk error");
            if(count==1&&preparing!=null){
                preparing.countDown();
                try{if(!releasePrepare.await(5,TimeUnit.SECONDS))throw new IOException("fixture prepare timeout");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException(error);}
            }
        }
        public Process launch(boolean verify)throws IOException {
            int count=verify?verifiers.incrementAndGet():clients.incrementAndGet();
            if(!verify&&failFirstClient&&count==1)throw new IOException("fixture transient spawn error");
            long millis=verify?(holdVerify?60000:0):(count<=exits.length?0:60000);
            int exit=verify?verifyExit:(count<=exits.length?exits[count-1]:0);
            String java=new File(System.getProperty("java.home"),"bin/java.exe").getAbsolutePath();
            Process process=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),TestFrpcSupervisor.class.getName(),"child",Long.toString(millis),Integer.toString(exit)).redirectErrorStream(true).start();
            synchronized(processes){processes.add(process);int alive=0;for(Process p:processes)if(p.isAlive())alive++;maxAlive.accumulateAndGet(alive,Math::max);}
            return process;
        }
        public void line(String line){if(line.equals("ready"))ready.incrementAndGet();}
        public void connecting(){}
        public void retry(int exit,long delay){retries.add(delay);}
        boolean noneAlive(){synchronized(processes){for(Process p:processes)if(p.isAlive())return false;return true;}}
    }
    private static final class Run implements AutoCloseable {
        final Driver driver;final FrpcSupervisor supervisor;final Thread thread;
        final AtomicReference<Throwable> error=new AtomicReference<>();
        Run(Driver driver,long[] delays,long verifyMillis){
            this.driver=driver;supervisor=new FrpcSupervisor(delays,verifyMillis,60000);
            thread=new Thread(()->{try{supervisor.run(driver);}catch(Throwable failure){error.set(failure);}},"test-frpc-owner");thread.start();
        }
        public void close()throws Exception {
            supervisor.stop();if(driver.releasePrepare!=null)driver.releasePrepare.countDown();
            thread.join(6000);
            // Ensure a failing assertion cannot leave even the test-owned children.
            synchronized(driver.processes){for(Process p:driver.processes)if(p.isAlive())p.destroyForcibly();}
            check(!thread.isAlive());check(driver.noneAlive());check(driver.maxAlive.get()<=1);
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("child")){
            System.out.println("ready");System.out.flush();Thread.sleep(Long.parseLong(args[1]));System.exit(Integer.parseInt(args[2]));return;
        }
        Driver d=new Driver();d.exits=new int[]{0,7,9};
        try(Run run=new Run(d,new long[]{40,80},4000)){
            Driver f=d;await(()->f.clients.get()==4&&f.ready.get()>=8);
            check(d.retries.equals(Arrays.asList(40L,80L,80L)));
            check(d.prepares.get()==4&&d.verifiers.get()==4);
            run.supervisor.stop();run.thread.join(3000);check(run.error.get()==null);
        }
        d=new Driver();d.exits=new int[]{1};
        try(Run run=new Run(d,new long[]{5000},4000)){
            Driver f=d;await(()->!f.retries.isEmpty());
            long since=System.nanoTime();run.supervisor.stop();run.thread.join(1500);
            check(!run.thread.isAlive());check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-since)<1500);
            check(d.clients.get()==1&&run.error.get()==null);
        }
        d=new Driver();d.exits=new int[]{1};
        try(Run run=new Run(d,new long[]{5000},4000)){
            Driver f=d;await(()->!f.retries.isEmpty());run.supervisor.networkChanged();
            long since=System.nanoTime();await(()->f.clients.get()==2&&f.ready.get()>=4);
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-since)<4500);
            check(d.prepares.get()==2&&d.verifiers.get()==2);
        }
        d=new Driver();
        try(Run run=new Run(d,new long[]{5000},4000)){
            Driver f=d;await(()->f.clients.get()==1&&f.ready.get()>=2);run.supervisor.networkChanged();
            await(()->f.clients.get()==2&&f.ready.get()>=4);
            check(d.retries.isEmpty());check(d.prepares.get()==2);
        }
        d=new Driver();d.verifyExit=23;
        try(Run run=new Run(d,new long[]{20},4000)){
            run.thread.join(5000);check(!run.thread.isAlive());check(run.error.get() instanceof IOException);
            check(d.clients.get()==0&&d.retries.isEmpty()&&d.verifiers.get()==1);
        }
        d=new Driver();d.holdVerify=true;
        try(Run run=new Run(d,new long[]{20},300)){
            run.thread.join(5000);check(!run.thread.isAlive());
            check(run.error.get() instanceof IOException&&run.error.get().getMessage().contains("超时"));
            check(d.clients.get()==0&&d.retries.isEmpty());
        }
        d=new Driver();d.holdVerify=true;
        try(Run run=new Run(d,new long[]{20},4000)){
            Driver f=d;await(()->f.ready.get()==1);run.supervisor.stop();run.thread.join(3000);
            check(run.error.get()==null&&d.clients.get()==0&&d.retries.isEmpty());
        }
        d=new Driver();d.preparing=new CountDownLatch(1);d.releasePrepare=new CountDownLatch(1);
        try(Run run=new Run(d,new long[]{20},4000)){
            check(d.preparing.await(3,TimeUnit.SECONDS));run.supervisor.stop();d.releasePrepare.countDown();
            run.thread.join(3000);check(run.error.get()==null&&d.verifiers.get()==0&&d.clients.get()==0);
        }
        d=new Driver();d.preparing=new CountDownLatch(1);d.releasePrepare=new CountDownLatch(1);
        try(Run run=new Run(d,new long[]{20},4000)){
            check(d.preparing.await(3,TimeUnit.SECONDS));run.supervisor.networkChanged();d.releasePrepare.countDown();
            Driver f=d;await(()->f.clients.get()==1&&f.ready.get()>=2);
            check(d.prepares.get()==2&&d.verifiers.get()==1);
        }
        d=new Driver();d.failFirstClient=true;
        try(Run run=new Run(d,new long[]{30},4000)){
            Driver f=d;await(()->f.clients.get()==2&&f.ready.get()>=3);
            check(d.retries.equals(Arrays.asList(30L))&&d.verifiers.get()==2);
        }
        d=new Driver();d.failPrepare=true;
        try(Run run=new Run(d,new long[]{20},4000)){
            run.thread.join(3000);check(run.error.get() instanceof IOException&&d.processes.isEmpty()&&d.retries.isEmpty());
        }
        System.out.println("PASS "+checks+" production FRP supervision checks with real owned JVM children: zero/nonzero exit retry, capped backoff, manual stop, network wakeup and replacement, verification failure/timeout, cancelled preparation, spawn failure and no overlapping/orphan children. Not a vehicle wake or FRP server test.");
    }
}
