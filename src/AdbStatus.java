package com.e02.rootconsole;
import java.util.*;
/** Missing, failed or unfamiliar readings stay unknown rather than disabled. */
public final class AdbStatus {
 public final int listen,lan,allowed,wired;
 private static int value(Map<String,String> m,String key){String s=m.get(key);return "true".equals(s)?1:"false".equals(s)?0:-1;}
 public AdbStatus(String output){Map<String,String> fields=new HashMap<>();for(String line:output.split("\n")){int n=line.indexOf('=');if(n>0)fields.put(line.substring(0,n),line.substring(n+1).trim());}listen=value(fields,"LISTEN");lan=value(fields,"WIFI_ENABLED");allowed=value(fields,"WIRELESS");wired=value(fields,"WIRED");}
 public String wirelessLabel(){return listen<0?"无线ADB - 状态未知":listen==0?"无线ADB - 已关闭（端口未启动）":"无线ADB - 已开启 - "+(allowed==1?"已放行":allowed==0?"未放行":"放行状态未知");}
}
