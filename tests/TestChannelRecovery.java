package com.e02.rootconsole;
public final class TestChannelRecovery {
 public static void main(String[] args){
  int checks=0;
  for(int flags=0;flags<16;flags++){
   boolean web=(flags&1)!=0,webReady=(flags&2)!=0,adb=(flags&4)!=0,adbReady=(flags&8)!=0;
   boolean result=ChannelRecovery.mayStart(web,webReady,adb,adbReady);
   if(web&&!webReady&&result)throw new AssertionError("Missing selected web must wait");
   if(web&&webReady&&!result)throw new AssertionError("Available selected web must remain reachable even without ADB");
   if(!web&&result!=(adb&&adbReady))throw new AssertionError("ADB-only readiness required");
   checks++;
  }
  if(ChannelRecovery.webRoot(true,false)||ChannelRecovery.webRoot(false,true)||ChannelRecovery.webRoot(false,false)||!ChannelRecovery.webRoot(true,true))throw new AssertionError("Root may never be assumed or expanded");
  System.out.println("PASS "+(checks+4)+" independent recovery-channel cases; mapping selection stays unchanged. No vehicle claim.");
 }
}
