package com.e02.rootconsole;

import android.os.*;
import java.lang.reflect.*;

/** Read-only Android 9 display callback; independent of ActivityManager app records. */
final class RootDisplayObserver implements AutoCloseable {
 interface Listener{void screenOn();void screenOff();void unavailable();default void reconcile(){}default void endpointLost(){}}
 private static final String DESCRIPTOR="android.hardware.display.IDisplayManagerCallback";
 private final DisplayWakeGate gate=new DisplayWakeGate();private final Listener listener;
 private volatile boolean closed,failed,dead;private Object manager,callback;private Class<?> managerType;private IBinder service;
 private final BoundedRead<Integer> reads=new BoundedRead<>();
 private final BoundedRead<IBinder> lookup=new BoundedRead<>();
 private final BoundedRead<Void> registration=new BoundedRead<>();
 private final IBinder.DeathRecipient death;
 RootDisplayObserver(Listener listener){this.listener=listener;death=()->{boolean notify=!closed&&!dead;dead=true;unavailable();if(notify)listener.endpointLost();};}
 private synchronized void unavailable(){if(!closed&&!failed){failed=true;gate.prime(0);listener.unavailable();}}
 private int state(long timeout)throws Exception{
  int result=reads.read(()->{Object info=managerType.getMethod("getDisplayInfo",int.class).invoke(manager,0);
   return info==null?0:info.getClass().getField("state").getInt(info);},Math.min(500,timeout));
  if(result<=0)throw new IllegalStateException("Default display state unknown");
  if(!closed&&!dead)failed=false;return result;
 }
 void register()throws Exception{
  register(lookup.read(()->ReadOnlyServiceLookup.get("display"),500));
 }
 void register(IBinder candidate)throws Exception{
  if(closed||candidate==null||!candidate.isBinderAlive())throw new IllegalStateException("Display service unavailable");
  service=candidate;
  managerType=Class.forName("android.hardware.display.IDisplayManager");
  manager=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,service);
  try{gate.prime(state(500));}catch(Exception unreadable){failed=true;gate.prime(0);}
  callback=Class.forName(DESCRIPTOR+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,new Receiver());
  service.linkToDeath(death,0);
  try{registration.read(()->{managerType.getMethod("registerCallback",Class.forName(DESCRIPTOR)).invoke(manager,callback);return null;},1500);}
  catch(Exception failed){close();throw failed;}
 }
 IBinder endpoint(){return service;}
 boolean endpointDead(){IBinder endpoint=service;return endpoint!=null&&!endpoint.isBinderAlive();}
 boolean available(){return !closed&&!dead&&!failed&&service!=null&&service.isBinderAlive();}
 boolean isOn(){return isOn(500);}
 boolean isOn(long timeout){if(closed||dead)return false;try{return state(timeout)==2&&!closed&&!dead;}catch(Exception error){unavailable();return false;}}
 private synchronized void changed(int event)throws Exception{
  if(closed||dead)return;boolean wasOn=gate.isOn();
  if(gate.event(0,event,event==3?0:state(500)))listener.screenOn();
  else if(wasOn&&!gate.isOn())listener.screenOff();
  else if(event!=3&&gate.isOn())listener.reconcile();
 }
 private final class Receiver extends Binder {
  Receiver(){attachInterface(null,DESCRIPTOR);}
  @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
   if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(DESCRIPTOR);return true;}
   if(code!=FIRST_CALL_TRANSACTION)return super.onTransact(code,data,reply,flags);
   if(closed||dead||Binder.getCallingUid()!=1000)return true;
   data.enforceInterface(DESCRIPTOR);int display=data.readInt(),event=data.readInt();
   if(display==0&&event>=1&&event<=3)try{changed(event);}
   catch(Exception error){unavailable();}
   if(reply!=null)reply.writeNoException();return true;
  }
 }
 @Override public void close(){closed=true;lookup.close();reads.close();registration.close();gate.close();if(service!=null)try{service.unlinkToDeath(death,0);}catch(RuntimeException ignored){}
  // Android 9 has no unregisterCallback. The daemon exits after close; Binder death removes its callback.
 }
}
