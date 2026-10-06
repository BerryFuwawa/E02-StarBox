package com.e02.rootconsole;
public class TestLocalAdb {
 public static void main(String[] args){String local=AdbControl.localOnly(),off=AdbControl.wireless(false,"","","");
 if(!local.contains("-i lo -s 127.0.0.1 -d 127.0.0.1 -j ACCEPT")||!local.contains("$next -j DROP")||local.contains("iptables -A $next -i wlan")||local.contains("iptables -A $next -i ap"))throw new AssertionError("Local fallback must retain localhost only");
 if(off.contains("-i lo")||off.contains("setprop"))throw new AssertionError("Manual off semantics changed");
 if(!AdbControl.localStatus().contains("iptables -C \"$active\"")||!AdbControl.localStatus().contains("LOCAL_ADB=true"))throw new AssertionError("Must inspect active chain");
 System.out.println("FRP local fallback, active rule detection and manual off PASS");}
}
