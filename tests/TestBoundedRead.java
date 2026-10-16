package com.e02.rootconsole;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
public final class TestBoundedRead {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("bounded read "+checks);}
 public static void main(String[] args)throws Exception{
  BoundedRead<Integer> r=new BoundedRead<>();check(r.read(()->7,1000)==7);
  try{r.read(()->{throw new IllegalStateException();},1000);throw new AssertionError();}catch(ExecutionException expected){checks++;}
  check(r.read(()->8,1000)==8);
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),ended=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
  long began=System.nanoTime();
  try{r.read(()->{calls.incrementAndGet();entered.countDown();release.await();ended.countDown();return 1;},60);throw new AssertionError();}catch(TimeoutException expected){checks++;}
  check(entered.await(1,TimeUnit.SECONDS));check((System.nanoTime()-began)/1000000<1000);
  for(int i=0;i<100;i++)try{r.read(()->{calls.incrementAndGet();return 2;},60);throw new AssertionError();}catch(TimeoutException expected){checks++;}
  check(calls.get()==1);
  release.countDown();check(ended.await(1,TimeUnit.SECONDS));Thread.sleep(20);
  check(r.read(()->9,1000)==9); // Discard the overdue value 1, request fresh state.
  r.close();try{r.read(()->10,100);throw new AssertionError();}catch(TimeoutException expected){checks++;}
  r=new BoundedRead<>();final BoundedRead<Integer> active=r;CountDownLatch blocking=new CountDownLatch(1),ready=new CountDownLatch(1);
  AtomicReference<Throwable> failure=new AtomicReference<>();
  Thread waiter=new Thread(()->{try{active.read(()->{ready.countDown();blocking.await();return 1;},1000);failure.set(new AssertionError("interruption missing"));}catch(InterruptedException expected){if(!Thread.currentThread().isInterrupted())failure.set(new AssertionError("interrupt flag lost"));}catch(Exception e){failure.set(e);}});
  waiter.start();check(ready.await(1,TimeUnit.SECONDS));waiter.interrupt();waiter.join(1000);check(!waiter.isAlive()&&failure.get()==null);
  try{r.read(()->2,100);throw new AssertionError();}catch(TimeoutException expected){checks++;}
  r.close();blocking.countDown();
  System.out.println("PASS "+checks+" bounded-read checks (timeout, fresh result, exception, cancellation, single blocked worker).");
 }
}
