package com.e02.rootconsole;
public class TestRemoteUser {
 public static void main(String[] args){
  String legacy=RemoteConfig.client("example.com",7000,"token","1234567890123456","192.0.2.10",8875,true,true);
  String empty=RemoteConfig.client("example.com",7000,"token","1234567890123456","192.0.2.10",8875,true,true,"");
  if(!legacy.equals(empty)||empty.contains("user ="))throw new AssertionError("Empty username compatibility");
  String client=RemoteConfig.client("example.com",7000,"token","1234567890123456","192.0.2.10",8878,true,true," demo-user ");
  String visitor=RemoteConfig.visitor("example.com",7000,"token","1234567890123456",true,true,"demo-user");
  if(!client.contains("user = \"demo-user\"")||!visitor.contains("user = \"demo-user\""))throw new AssertionError("Matching namespaces");
  if(!client.contains("localPort = 8878")||!visitor.contains("bindPort = 8556"))throw new AssertionError("Port compatibility");
  String escaped=RemoteConfig.visitor("example.com",7000,"token","1234567890123456",true,true,"a\"b");
  if(!escaped.contains("user = \"a\\\"b\""))throw new AssertionError("TOML escaping");
  try{RemoteConfig.visitor("example.com",7000,"token","1234567890123456",true,true,"a\nb");throw new AssertionError("Multiline user accepted");}catch(IllegalArgumentException expected){}
  System.out.println("Remote username and legacy configuration PASS");
 }
}
