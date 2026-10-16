package com.e02.rootconsole;

/** One bounded recovery sequence per natural event. No alarms or wake locks. */
final class WakeRestoreController {
    interface Driver {
        boolean allowed();
        boolean request()throws Exception;
        void delay(int seconds)throws InterruptedException;
        default long nowMillis(){return System.nanoTime()/1000000L;}
    }
    private boolean closed,running;private long generation;
    synchronized long begin(){if(closed||running)return -1;running=true;return ++generation;}
    synchronized void close(){closed=true;generation++;}
    synchronized void cancel(){generation++;running=false;}
    synchronized boolean current(long ticket){return !closed&&running&&ticket==generation;}
    boolean run(long ticket,Driver driver) {
        final long began=driver.nowMillis();
        try {
            for(int seconds:new int[]{0,2,5,15}) {
                if(driver.nowMillis()-began>=120000||!current(ticket)||!driver.allowed())return false;
                if(seconds>0)driver.delay(seconds);
                if(driver.nowMillis()-began>=120000||!current(ticket)||!driver.allowed())return false;
                try{if(driver.request())return driver.nowMillis()-began<120000&&current(ticket)&&driver.allowed();}
                catch(Exception unavailable){if(unavailable instanceof InterruptedException){Thread.currentThread().interrupt();return false;}}
            }
            return false;
        }catch(InterruptedException cancelled){Thread.currentThread().interrupt();return false;}
        finally{synchronized(this){if(ticket==generation)running=false;}}
    }
}
