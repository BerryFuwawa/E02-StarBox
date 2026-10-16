package com.e02.rootconsole;
/** Fixed status text only. Network keys may contain private addresses and are never returned. */
final class RemoteNetworkReason {
 static String changed(String previous,String current){
  if(previous==null||current==null)return "默认连接信息已变化，正在重新建立远程通道";
  String[] before=previous.split("\\|",-1),after=current.split("\\|",-1);
  if(before.length!=3||after.length!=3)return "默认连接信息已变化，正在重新建立远程通道";
  if(!before[0].equals(after[0]))return "默认网络已切换，正在重新建立远程通道";
  if(!before[1].equals(after[1]))return "网络接口已变化，正在重新建立远程通道";
  if(!before[2].equals(after[2]))return "网络解析设置已变化，正在重新建立远程通道";
  return "默认连接信息未变化";
 }
 private RemoteNetworkReason(){}
}
