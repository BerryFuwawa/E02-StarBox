package com.e02.rootconsole;
import java.util.*;
/** Missing, failed or unfamiliar readings stay unknown rather than disabled. */
public final class AdbStatus {
 public final int listen,lan,allowed,wired;
 private static int value(Map<String,String> m,String key){String s=m.get(key);return "true".equals(s)?1:"false".equals(s)?0:-1;}
 public AdbStatus(String output){Map<String,String> fields=new HashMap<>();for(String line:output.split("\n")){int n=line.indexOf('=');if(n>0)fields.put(line.substring(0,n),line.substring(n+1).trim());}listen=value(fields,"LISTEN");lan=value(fields,"WIFI_ENABLED");allowed=value(fields,"WIRELESS");wired=value(fields,"WIRED");}
 /** Localhost can stay available to FRP while LAN access is closed. */
 public int wirelessState(){return listen==0||lan==0?0:listen==1&&lan==1?1:-1;}
 public boolean wirelessWarning(){return wirelessState()<0||wirelessState()==1&&allowed!=1;}
 public String wirelessHint(){return wirelessState()<0?"无法确认无线 ADB 状态，请重新检测":wirelessState()==1&&allowed==0?"电脑 IP 尚未放行，请确认地址并应用":wirelessState()==1&&allowed<0?"电脑 IP 放行状态未知，请确认地址并重新检测":"";}
 public String wirelessLabel(){int state=wirelessState();return state<0?"无线ADB - 状态未知":state==0?"无线ADB - 已关闭":"无线ADB - 已开启 - "+(allowed==1?"已允许上述IP使用":allowed==0?"未允许上述IP使用":"放行状态未知");}
}
