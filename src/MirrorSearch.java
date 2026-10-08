package com.e02.rootconsole;
/** Stop at the first usable mirror; a successful result is never persisted here. */
public final class MirrorSearch {
 public interface Probe {boolean cancelled();void testing(int index,String route);void verify(String route)throws Exception;}
 public static String first(String[] routes,Probe probe){for(int i=1;i<routes.length&&!probe.cancelled();i++){probe.testing(i,routes[i]);try{probe.verify(routes[i]);if(!probe.cancelled())return routes[i];}catch(Exception unavailable){}}return "";}
}
