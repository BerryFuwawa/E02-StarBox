package com.e02.rootconsole;
import java.net.*;
import java.util.*;
public final class NetworkState {
 public static final class Address {public final String ip,iface;public final short prefix;Address(String ip,String iface,short prefix){this.ip=ip;this.iface=iface;this.prefix=prefix;}public String label(){return iface+" · "+ip+" / "+prefix;}public boolean contains(String peer){return sameSubnet(ip,peer,prefix);}}
 public static boolean sameSubnet(String a,String b,int prefix){if(prefix<0||prefix>32)return false;try{long x=number(AdbControl.ipv4(a)),y=number(AdbControl.ipv4(b)),mask=prefix==0?0:(0xffffffffL<<(32-prefix))&0xffffffffL;return (x&mask)==(y&mask);}catch(Exception e){return false;}}
 private static long number(String ip){long n=0;for(String p:ip.split("\\."))n=(n<<8)|Integer.parseInt(p);return n;}
 public static List<Address> list(){List<Address> out=new ArrayList<>();try{for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces())){if(!n.isUp()||n.isLoopback()||n.getName().startsWith("tun")||n.getName().startsWith("rmnet")||n.getName().startsWith("ccmni")||n.getName().startsWith("eth0."))continue;for(InterfaceAddress a:n.getInterfaceAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress()&&!a.getAddress().isLinkLocalAddress())out.add(new Address(a.getAddress().getHostAddress(),n.getName(),a.getNetworkPrefixLength()));}Collections.sort(out,(a,b)->Boolean.compare(!a.iface.startsWith("wlan")&&!a.iface.startsWith("ap"),!b.iface.startsWith("wlan")&&!b.iface.startsWith("ap")));}catch(Exception ignored){}return out;}
 public static Address maintained(List<Address> all,String peer,String previous){String iface=previous.split("\\|",-1)[0];for(Address a:all)if(a.iface.equals(iface))return a;return previous.isEmpty()?choose(all,peer):null;}
 public static Address choose(List<Address> all,String peer){Address match=null;for(Address a:all)if(a.contains(peer)){if(match!=null)return null;match=a;}return match;}
}
