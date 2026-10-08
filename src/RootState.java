package com.e02.rootconsole;
/** Rejects a detection result started before revocation or a newer detection. */
public final class RootState {
 private long generation;private boolean available,bridge=true;
 public synchronized long begin(){return ++generation;}
 public synchronized boolean publish(long ticket,boolean valid,boolean viaBridge){if(ticket!=generation)return false;available=valid;bridge=viaBridge;return true;}
 public synchronized void clear(){generation++;available=false;bridge=true;}
 public synchronized boolean available(){return available;}
 public synchronized boolean bridge(){return bridge;}
}
