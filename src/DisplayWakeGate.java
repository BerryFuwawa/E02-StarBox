package com.e02.rootconsole;

/** Only an actual default-display transition to ON may begin a recovery round. */
final class DisplayWakeGate {
 private boolean known,closed;private int previous;
 synchronized void prime(int state){if(!closed){known=state>0;previous=state;}}
 synchronized boolean isOn(){return !closed&&known&&previous==2;}
 synchronized boolean event(int display,int event,int state){
  if(closed||display!=0||event<1||event>3)return false;
  if(event==3||state<=0){known=false;previous=0;return false;}
  boolean wake=known&&previous!=2&&state==2;
  known=true;previous=state;return wake;
 }
 synchronized void close(){closed=true;known=false;}
}
