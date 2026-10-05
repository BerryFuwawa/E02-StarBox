package com.e02.rootconsole;
/** Generates narrowly scoped commands; all variable shell values are validated IPv4 addresses. */
public final class AdbControl {
 public static String ipv4(String s){if(s==null||!s.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IllegalArgumentException("请输入电脑的 IPv4 地址");for(String part:s.split("\\.")){int n=Integer.parseInt(part);if(n>255||(part.length()>1&&part.startsWith("0")))throw new IllegalArgumentException("IPv4 地址无效");}if(s.startsWith("127.")||s.equals("0.0.0.0")||Integer.parseInt(s.split("\\.")[0])>=224)throw new IllegalArgumentException("请选择局域网电脑地址");return s;}
 private static String rule(String host,String pc){return "-i wlan0 -s "+ipv4(pc)+" -d "+ipv4(host)+" -p tcp --dport 5555 -m comment --comment e02-wifi-adb -j ACCEPT";}
 public static String wireless(boolean on,String host,String pc){String r=rule(host,pc);String old=r;String off="-i wlan0 -p tcp --dport 5555 -m comment --comment e02-adb-off -j DROP";
  String clean="while iptables -C INPUT "+old+" 2>/dev/null; do iptables -D INPUT "+old+" || exit 1; done\n";
  String managed="iptables -N E02_WIFI_ADB 2>/dev/null || iptables -L E02_WIFI_ADB >/dev/null || exit 1\niptables -F E02_WIFI_ADB || exit 1\n";
  String jump="-i wlan0 -p tcp --dport 5555 -j E02_WIFI_ADB";
  String removeJump="while iptables -C INPUT "+jump+" 2>/dev/null; do iptables -D INPUT "+jump+" || exit 1; done\n";
  String removeOff="while iptables -C INPUT "+off+" 2>/dev/null; do iptables -D INPUT "+off+" || exit 1; done\n";
  if(on)return managed+"iptables -A E02_WIFI_ADB "+r+" || exit 1\niptables -A E02_WIFI_ADB -j DROP || exit 1\n"+removeJump+"iptables -I INPUT 1 "+jump+" || exit 1\n"+clean+removeOff+"setprop service.adb.tcp.port 5555\nif ! netstat -lnt | grep -q ':5555 '; then stop adbd; start adbd; fi\nprintf '无线 ADB 已开放给电脑 "+pc+"，仅 wlan0/TCP5555\\n'\n";
  return "iptables -C INPUT "+off+" 2>/dev/null || iptables -I INPUT 1 "+off+" || exit 1\n"+removeJump+clean+"printf '无线 ADB 已关闭；有线 ADB 不受此规则影响\\n'\n";
 }
 public static String wired(boolean on){return "test -f /vendor/bin/change_usb_mode.sh || { echo '未找到 E02 厂家 USB 切换脚本'; exit 1; }\nsetprop persist.vendor.setusbmode "+on+"\ngetprop persist.vendor.setusbmode\n";}
 public static String status(String host,String pc){if(pc==null||pc.trim().isEmpty())return "printf 'WIRED='; getprop persist.vendor.setusbmode\nprintf 'WIRELESS=unknown\\n'\n";String r=rule(host,pc);return "printf 'WIRED='; getprop persist.vendor.setusbmode\nprintf 'WIRELESS='; if iptables -C INPUT -i wlan0 -p tcp --dport 5555 -m comment --comment e02-adb-off -j DROP 2>/dev/null; then echo false; elif iptables -C INPUT -i wlan0 -p tcp --dport 5555 -j E02_WIFI_ADB 2>/dev/null && iptables -C E02_WIFI_ADB "+r+" 2>/dev/null; then echo true; elif iptables -C INPUT "+r+" 2>/dev/null; then echo true; else echo false; fi\n";}
}
