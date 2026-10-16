package com.e02.rootconsole;

import java.util.concurrent.*;

/** A stalled Binder read keeps one daemon thread, never an expanding queue of readers. */
final class BoundedRead<T> implements AutoCloseable {
 private static final class Read<V>{final FutureTask<V> task;boolean expired;Read(Callable<V> call){task=new FutureTask<>(call);}}
 private Read<T> pending;private boolean closed;
 T read(Callable<T> operation,long timeoutMillis)throws Exception {
  final Read<T> read;
  synchronized(this){
   if(closed||timeoutMillis<=0)throw new TimeoutException("Read unavailable");
   if(pending!=null&&pending.task.isDone())pending=null;
   if(pending!=null&&pending.expired)throw new TimeoutException("Previous read still pending");
   if(pending==null){
    pending=new Read<>(operation);
    Thread thread=new Thread(pending.task,"starbox-state-read");thread.setDaemon(true);thread.start();
   }
   read=pending;
  }
  boolean completed=false;
  try{T value=read.task.get(timeoutMillis,TimeUnit.MILLISECONDS);completed=true;synchronized(this){if(closed)throw new CancellationException("Read closed");if(read.expired)throw new TimeoutException("Overdue read discarded");}return value;}
  catch(TimeoutException error){synchronized(this){read.expired=true;}throw error;}
  catch(InterruptedException error){synchronized(this){read.expired=true;}Thread.currentThread().interrupt();throw error;}
  catch(ExecutionException error){completed=true;throw error;}
  finally{synchronized(this){if(completed&&pending==read)pending=null;}}
 }
 @Override public synchronized void close(){closed=true;/* Binder calls cannot reliably be interrupted. */}
}
