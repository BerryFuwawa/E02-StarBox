package com.e02.rootconsole;
import java.io.IOException;
import java.util.*;

/** Own component only. Never enables the global switch or restores another application's old choice. */
final class AuxiliaryRepair {
 static final String COMPONENT="com.e02.rootconsole/com.e02.rootconsole.SystemKeepAliveService";
 static final String SHORT="com.e02.rootconsole/.SystemKeepAliveService";
 enum Result { PRESENT, REPAIRED, DENIED, MASTER_OFF, INVALID, CONFLICT, FAILED }
 interface Driver {boolean allowed();String readList()throws IOException;String readMaster()throws IOException;void writeList(String list)throws IOException;}
 static boolean contains(String list){return Arrays.asList(list.split(":",-1)).contains(COMPONENT)||Arrays.asList(list.split(":",-1)).contains(SHORT);}
 static String clean(String value)throws IOException {
  if(value==null)throw new IOException("Missing settings response");String text=value.trim();if(text.equals("null"))return "";
  if(text.length()>16384||(!text.isEmpty()&&!text.matches("[A-Za-z0-9_.$/:-]+")))throw new IOException("Invalid component list");
  if(!text.isEmpty())for(String part:text.split(":",-1))if(!part.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+"))throw new IOException("Invalid component entry");
  return text;
 }
 static Result restore(Driver driver){
  try{
   for(int attempt=0;attempt<3;attempt++){
    if(!driver.allowed())return Result.DENIED;
    if(!"1".equals(driver.readMaster().trim()))return Result.MASTER_OFF;
    String before=clean(driver.readList());if(contains(before))return Result.PRESENT;
    if(!driver.allowed())return Result.DENIED;
    String latest=clean(driver.readList());if(!latest.equals(before))continue;
    if(!driver.allowed())return Result.DENIED;
    driver.writeList(latest+(latest.isEmpty()?"":":")+COMPONENT);
    if(!driver.allowed())return Result.DENIED;
    String after=clean(driver.readList());
    if(contains(after)&&Arrays.asList(after.split(":")).containsAll(Arrays.asList(latest.isEmpty()?new String[0]:latest.split(":"))))return Result.REPAIRED;
   }
   return Result.CONFLICT;
  }catch(IOException|RuntimeException invalid){return Result.FAILED;}
 }
}
