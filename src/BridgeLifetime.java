package com.e02.rootconsole;
import java.util.concurrent.TimeUnit;
/** 0 means no scheduled expiration; stop/reboot still ends the bridge. */
public final class BridgeLifetime {
 public static final long MAX_SECONDS=24*3600+59*60;
 private final long seconds,started;
 public BridgeLifetime(long seconds,long started){if(seconds<0||seconds>MAX_SECONDS)throw new IllegalArgumentException("Bridge duration outside 0..89940 seconds");this.seconds=seconds;this.started=started;}
 public boolean expired(long now){return seconds!=0&&now-started>=TimeUnit.SECONDS.toNanos(seconds);}
 public int commandSeconds(int requested,long now){if(seconds==0)return requested;long remaining=TimeUnit.SECONDS.toNanos(seconds)-(now-started);if(remaining<=0)return 0;return (int)Math.min(requested,Math.max(1,TimeUnit.NANOSECONDS.toSeconds(remaining)));}
 public String description(){return seconds==0?"no scheduled expiration (stop/reboot ends bridge)":"expires in "+seconds+" seconds";}
}
