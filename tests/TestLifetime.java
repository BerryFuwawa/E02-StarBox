package com.e02.rootconsole;
import java.util.concurrent.TimeUnit;
public final class TestLifetime {
 private static void check(boolean ok){if(!ok)throw new AssertionError("Lifetime check failed");}
 public static void main(String[] args){
  long start=123456;
  BridgeLifetime timed=new BridgeLifetime(60,start);
  check(!timed.expired(start+TimeUnit.SECONDS.toNanos(59)));
  check(timed.expired(start+TimeUnit.SECONDS.toNanos(60)));
  check(timed.commandSeconds(30,start+TimeUnit.SECONDS.toNanos(55))==5);
  check(timed.commandSeconds(30,start+TimeUnit.SECONDS.toNanos(60))==0);
  BridgeLifetime permanent=new BridgeLifetime(0,start);
  check(!permanent.expired(start+TimeUnit.DAYS.toNanos(3650)));
  check(permanent.commandSeconds(30,start+TimeUnit.DAYS.toNanos(3650))==30);
  check(!new BridgeLifetime(BridgeLifetime.MAX_SECONDS,start).expired(start));
  for(long bad:new long[]{-1,BridgeLifetime.MAX_SECONDS+1,Long.MAX_VALUE}){boolean rejected=false;try{new BridgeLifetime(bad,start);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
  System.out.println("Lifetime: 10 checks PASS (expiry, timeout cap, permanent, bounds)");
 }
}
