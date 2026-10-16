package android.os;
public class ServiceManager {
 public static volatile IBinder display,power,cachedDisplay,cachedPower;
 public static volatile int rawCalls;
 public static IBinder getService(String name){return name.equals("display")?(cachedDisplay!=null?cachedDisplay:display):(cachedPower!=null?cachedPower:power);}
 private static IServiceManager getIServiceManager(){return name->{rawCalls++;return name.equals("display")?display:power;};}
}
