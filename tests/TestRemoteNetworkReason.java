package com.e02.rootconsole;
public final class TestRemoteNetworkReason {
 public static void main(String[] args){
  String[] old={"net1|private_interface_A|[private_resolver_A]","net1|private_interface_A|[private_resolver_A]","net1|private_interface_A|[private_resolver_A]","none",null};
  String[] next={"net2|private_interface_A|[private_resolver_A]","net1|private_interface_B|[private_resolver_A]","net1|private_interface_A|[private_resolver_B]","net1|private_interface_A|[private_resolver_A]",null};
  String[] expected={"默认网络已切换","网络接口已变化","网络解析设置已变化","默认连接信息已变化","默认连接信息已变化"};
  for(int i=0;i<old.length;i++){
   String result=RemoteNetworkReason.changed(old[i],next[i]);
   if(!result.startsWith(expected[i])||result.contains("private_")||result.contains("net1")||result.contains("net2"))throw new AssertionError("private-free reason "+i);
  }
  if(!RemoteNetworkReason.changed(old[0],old[0]).equals("默认连接信息未变化"))throw new AssertionError("unchanged key");
  System.out.println("PASS actual fixed network reason: route/interface/resolver/unknown/unchanged, no private network-key values in status.");
 }
}
