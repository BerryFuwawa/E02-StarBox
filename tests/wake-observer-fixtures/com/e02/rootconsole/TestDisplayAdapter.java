package com.e02.rootconsole;
import android.os.*;import android.hardware.display.*;import android.view.DisplayInfo;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
public final class TestDisplayAdapter {
 public static final class Fake extends Binder implements IDisplayManager {
  volatile int state=2;volatile boolean failOnce;IDisplayManagerCallback callback;volatile CountDownLatch block,entered;AtomicInteger calls=new AtomicInteger();
  public DisplayInfo getDisplayInfo(int id)throws RemoteException{
   calls.incrementAndGet();if(failOnce){failOnce=false;throw new RemoteException("transient");}
   CountDownLatch latch=block;if(latch!=null){entered.countDown();try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
   return new DisplayInfo(state);
  }
  public void registerCallback(IDisplayManagerCallback c){callback=c;}
  void event(int id,int event)throws RemoteException{callback.onDisplayEvent(id,event);}
 }
 static final class Counts implements RootDisplayObserver.Listener{int on,off,unavailable,hints;public void screenOn(){on++;}public void screenOff(){off++;}public void unavailable(){unavailable++;}public void reconcile(){hints++;}}
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("adapter check "+checks);}
 static RootDisplayObserver start(Fake f,Counts c)throws Exception{ServiceManager.display=f;RootDisplayObserver o=new RootDisplayObserver(c);o.register();return o;}
 public static void main(String[] args)throws Exception{
  Fake f=new Fake();Counts c=new Counts();RootDisplayObserver o=start(f,c);
  check(o.available()&&o.isOn()&&c.on==0&&c.hints==0);
  f.state=1;f.event(0,2);check(c.off==1);f.state=2;f.event(0,2);check(c.on==1);
  f.event(1,2);check(c.on==1&&c.hints==0);
  Binder.caller=20000;f.event(0,2);check(c.hints==0);Binder.caller=1000;
  o.close();f.event(0,2);check(!o.available()&&c.hints==0);
  f=new Fake();c=new Counts();o=start(f,c);f.die();check(!o.available()&&!o.isOn()&&c.unavailable==1);o.close();

  f=new Fake();c=new Counts();o=start(f,c);f.state=1;f.state=2;f.event(0,2);f.event(0,2);
  check(c.on==0&&c.hints==2); // Ambiguous notifications are rechecks, never claimed as real transitions.
  o.close();
  f=new Fake();c=new Counts();o=start(f,c);f.failOnce=true;f.event(0,2);check(!o.available()&&c.unavailable==1);
  f.state=1;f.event(0,2);check(o.available());f.state=2;f.event(0,2);check(c.on==1);o.close();
  f=new Fake();c=new Counts();f.failOnce=true;o=start(f,c);check(!o.available());f.event(0,2);check(o.available()&&c.hints==1);o.close();

  f=new Fake();c=new Counts();o=start(f,c);f.block=new CountDownLatch(1);f.entered=new CountDownLatch(1);
  long at=System.nanoTime();check(!o.isOn());check((System.nanoTime()-at)/1000000<1500);check(f.entered.await(1,TimeUnit.SECONDS));
  int calls=f.calls.get();for(int i=0;i<100;i++)check(!o.isOn());check(f.calls.get()==calls); // One blocked reader only.
  f.block.countDown();Thread.sleep(50);f.block=null;check(o.isOn()&&o.available());o.close();
  System.out.println("PASS "+checks+" production display-adapter checks: delayed hints, transient retry, bounded stalled Binder, trusted source, close/death.");
 }
}
