package com.e02.rootconsole;
import android.os.*;import android.hardware.display.*;import android.view.*;import java.io.*;
public class TestWakeObserverAdapter{
 public static class Display extends Binder implements IDisplayManager{int state=2;IDisplayManagerCallback callback;public DisplayInfo getDisplayInfo(int id){return new DisplayInfo(state);}public void registerCallback(IDisplayManagerCallback c){callback=c;}void changed()throws Exception{callback.onDisplayEvent(0,2);}}
 public static class Power extends Binder implements IPowerManager{public boolean isInteractive(){return true;}}
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("wake adapter "+checks);}
 static RootWakeObserver start(Display d){FixtureState.reset();ServiceManager.display=d;ServiceManager.power=new Power();RootWakeObserver o=new RootWakeObserver(new File("fixture"),new RootPermit.Ticket());o.register();return o;}
 static void waitFor(String event)throws Exception{long end=System.nanoTime()+2000000000L;while(!FixtureState.events.contains(event)&&System.nanoTime()<end)Thread.sleep(5);check(FixtureState.events.contains(event));Thread.sleep(20);}
 static long count(String c){return FixtureState.commands.stream().filter(s->s.contains(c)).count();}
 public static void main(String[] args)throws Exception{
  Display d=new Display();RootWakeObserver o=start(d);check(o.status().equals("registered-v5:fixture"));d.changed();waitFor("service:already-running");check(count("start-foreground-service")==0&&count(" put secure ")==0);long probes=count("dumpsys");d.changed();Thread.sleep(30);check(count("dumpsys")==probes);o.close();
  d=new Display();o=start(d);FixtureState.live=0;d.changed();waitFor("service:receipt-confirmed");check(count("start-foreground-service")==1&&count(" put secure ")==0);o.close();
  d=new Display();o=start(d);FixtureState.list="other.app/.Service";d.changed();waitFor("service:receipt-confirmed");check(count(" put secure ")==1&&FixtureState.list.equals("other.app/.Service:"+AuxiliaryRepair.COMPONENT));o.close();
  d=new Display();o=start(d);FixtureState.exit=true;d.changed();Thread.sleep(50);check(FixtureState.commands.isEmpty());o.close();
  d=new Display();o=start(d);FixtureState.permit=false;d.changed();Thread.sleep(50);check(FixtureState.commands.isEmpty());o.close();
  d=new Display();o=start(d);FixtureState.live=0;FixtureState.denyAfterRead=true;d.changed();Thread.sleep(80);check(count("start-foreground-service")==0&&count(" put secure ")==0);o.close();
  d=new Display();o=start(d);FixtureState.live=-1;d.changed();waitFor("service:liveness-unknown");check(count("start-foreground-service")==0&&count(" put secure ")==0);o.close();
  d=new Display();o=start(d);d.state=1;d.changed();d.state=2;d.changed();waitFor("service:receipt-confirmed");check(count("start-foreground-service")==1);o.close();
  d=new Display();o=start(d);o.close();d.changed();Thread.sleep(30);check(FixtureState.commands.isEmpty());
  System.out.println("PASS "+checks+" production RootWakeObserver integration checks: delayed-event reconciliation, healthy no restart, own-only repair, revoke/exit guards, unknown no write, real transition.");
 }
}
