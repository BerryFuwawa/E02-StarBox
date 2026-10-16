package com.e02.rootconsole;

/** Parse only the fixed own-service query; unknown output never authorizes a restart. */
final class RecoveryServiceState {
 static int parse(String text){
  if(text==null||!text.contains("ACTIVITY MANAGER SERVICES"))return -1;
  boolean own=false,found=false;
  for(String line:text.replace("\r","").split("\n")){
   if(line.contains("ServiceRecord{")){
    own=line.contains("com.e02.rootconsole/.ConsoleService}")||line.contains("com.e02.rootconsole/com.e02.rootconsole.ConsoleService}");
    if(own)found=true;
   }
   if(own&&line.trim().startsWith("app=")){
    if(line.contains("app=ProcessRecord{"))return 1;
    if(line.trim().equals("app=null"))return 0;
   }
  }
  return !found&&text.contains("(nothing)")?0:-1;
 }
}
