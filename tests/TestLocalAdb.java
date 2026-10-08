package com.e02.rootconsole;
public class TestLocalAdb {
 public static void main(String[] args){String local=AdbControl.localOnly(),off=AdbControl.wireless(false,"","","");
 if(!local.contains("-i lo -s 127.0.0.1 -d 127.0.0.1 -j ACCEPT")||!local.contains("$next -j DROP")||local.contains("iptables -A $next -i wlan")||local.contains("iptables -A $next -i ap"))throw new AssertionError("Local fallback must retain localhost only");
 if(off.contains("-i lo")||off.contains("setprop"))throw new AssertionError("Manual off semantics changed");
 if(!AdbControl.localStatus().contains("iptables -C \"$active\"")||!AdbControl.localStatus().contains("LOCAL_ADB=true"))throw new AssertionError("Must inspect active chain");
 String ensure=AdbControl.ensureLocal();if(ensure.contains("setusbmode")||!ensure.startsWith("if ! iptables -C INPUT")||!ensure.contains("exit 0\nfi"))throw new AssertionError("Preparation must preserve peer and USB");
 if(!AdbControl.status("192.0.2.10","192.0.2.20").contains("grep LISTEN")||!ensure.contains("grep LISTEN"))throw new AssertionError("Residual sockets cannot count as listening");
 System.out.println("FRP local fallback, active rule detection and manual off PASS");}
}
