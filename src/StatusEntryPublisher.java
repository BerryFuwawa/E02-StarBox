package com.e02.rootconsole;

/** Tracks successful own notification publication, not physical icon visibility. */
final class StatusEntryPublisher {
 enum Presence { PRESENT, ABSENT, UNKNOWN }
 enum Result { UNCHANGED, PUBLISHED, CANCELLED, FAILED }
 interface Driver {
  boolean current(boolean enabled);
  void publish(boolean enabled)throws Exception;
 }
 private int committed=-1;
 Result refresh(boolean enabled,boolean force,Presence actual,Driver driver){
  if(!driver.current(enabled))return Result.CANCELLED;
  boolean needed=enabled?(force||committed!=1||actual==Presence.ABSENT):(committed==1||actual==Presence.PRESENT);
  if(!needed)return Result.UNCHANGED;
  try{driver.publish(enabled);}catch(Exception failed){committed=-1;return Result.FAILED;}
  if(!driver.current(enabled)){committed=-1;return Result.CANCELLED;}
  committed=enabled?1:0;return Result.PUBLISHED;
 }
}
