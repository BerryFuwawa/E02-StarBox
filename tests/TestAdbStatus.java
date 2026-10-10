package com.e02.rootconsole;
public class TestAdbStatus {
 private static void check(boolean value){if(!value)throw new AssertionError();}
 private static String field(int value){return value==1?"true":value==0?"false":"unknown";}
 public static void main(String[] args){
  int checks=0;
  for(int listen=-1;listen<=1;listen++)for(int lan=-1;lan<=1;lan++)for(int allowed=-1;allowed<=1;allowed++){
   AdbStatus status=new AdbStatus("LISTEN="+field(listen)+"\nWIFI_ENABLED="+field(lan)+"\nWIRELESS="+field(allowed)+"\nWIRED=true\n");
   int expected=listen==0||lan==0?0:listen==1&&lan==1?1:-1;
   check(status.wirelessState()==expected);check(status.wired==1);
   check(status.wirelessLabel().contains(expected<0?"状态未知":expected==0?"已关闭":"已开启"));checks+=3;check(status.wirelessWarning()==(expected<0||expected==1&&allowed!=1));check(status.wirelessHint().isEmpty()==!status.wirelessWarning());checks+=2;
   if(expected==1){check(status.wirelessLabel().contains(allowed==1?"已允许上述IP使用":allowed==0?"未允许上述IP使用":"放行状态未知"));checks++;}
  }
  AdbStatus localOnly=new AdbStatus("LISTEN=true\nWIFI_ENABLED=false\nWIRELESS=false\n");
  check(localOnly.listen==1&&localOnly.wirelessState()==0&&localOnly.wirelessLabel().equals("无线ADB - 已关闭"));
  check(new AdbStatus("").wirelessState()==-1);
  check(new AdbStatus("LISTEN=true\nWIFI_ENABLED=unexpected\n").wirelessState()==-1);
  System.out.println("ADB presentation: "+(checks+3)+" localhost/LAN/unknown/peer checks PASS");
 }
}
