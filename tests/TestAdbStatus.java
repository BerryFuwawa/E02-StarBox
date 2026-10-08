package com.e02.rootconsole;
public class TestAdbStatus {
 private static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){AdbStatus empty=new AdbStatus("");check(empty.listen==-1&&empty.wired==-1&&empty.wirelessLabel().contains("未知"));AdbStatus blocked=new AdbStatus("WIRED=true\nLISTEN=true\nWIFI_ENABLED=false\nWIRELESS=false\n");check(blocked.listen==1&&blocked.wired==1&&blocked.wirelessLabel().contains("已开启 - 未放行"));AdbStatus off=new AdbStatus("LISTEN=false\nWIRED=false\n");check(off.listen==0&&off.wirelessLabel().contains("端口未启动"));AdbStatus unknown=new AdbStatus("LISTEN=unknown\nWIFI_ENABLED=true\nWIRED=unexpected\n");check(unknown.listen==-1&&unknown.wired==-1);System.out.println("ADB: listening, blocked, off and unknown readings PASS");}
}
