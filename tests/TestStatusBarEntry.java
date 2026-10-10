package com.e02.rootconsole;
import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual publisher/settings adapter; package-manager and Android resource objects are fixtures. */
public final class TestStatusBarEntry {
 static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static final class App extends Context implements SharedPreferences {
  final File dir;final Map<String,Boolean> prefs=new HashMap<>();boolean failCommit,denied;int mode=0,changes;
  final PackageManager packages=new PackageManager(){public int getComponentEnabledSetting(ComponentName c){own(c);return mode;}public void setComponentEnabledSetting(ComponentName c,int value,int flags){own(c);check(flags==DONT_KILL_APP,"does not kill application or SystemUI");if(denied)throw new SecurityException("fixture denial");mode=value;changes++;}};
  App(File dir){this.dir=dir;}void own(ComponentName c){check(c.packageName.equals("com.e02.rootconsole")&&c.className.equals("com.e02.rootconsole.StarboxStatusBarPlugin"),"only own optional component");}
  public File getFilesDir(){return dir;}public ApplicationInfo getApplicationInfo(){return new ApplicationInfo();}public PackageManager getPackageManager(){return packages;}
  public SharedPreferences getSharedPreferences(String name,int mode){return this;}public int getInt(String key,int fallback){return key.equals("acceptedVersion")?1:fallback;}public boolean getBoolean(String key,boolean fallback){return prefs.getOrDefault(key,fallback);}
  public Editor edit(){return new Editor(){String key;boolean value;public Editor putBoolean(String key,boolean value){this.key=key;this.value=value;return this;}public void apply(){commit();}public boolean commit(){prefs.put(key,value);return !failCommit;}};}
 }
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory("starbox-status-entry-");App app=new App(dir.toFile());
  try{
   check(RuntimeSettings.open(app),"real lifecycle opened");check(StatusBarEntry.sync(app)&&app.changes==0,"disabled by default without package write");
   check(StatusBarEntry.select(app,true)&&StatusBarEntry.selected(app)&&!StatusBarEntry.wanted(app)&&app.changes==0,"child choice saved while master off");
   check(RuntimeSettings.configure(app,true,false,false)&&StatusBarEntry.sync(app)&&app.mode==1,"master enables selected entry");
   int changes=app.changes;check(StatusBarEntry.sync(app)&&app.changes==changes,"refresh does not repeatedly change package settings");
   check(RuntimeSettings.configure(app,false,true,true)&&StatusBarEntry.sync(app)&&app.mode==2&&StatusBarEntry.selected(app),"master off hides entry and keeps child choice");
   check(RuntimeSettings.snapshot(app).autoStart&&RuntimeSettings.snapshot(app).parkedRemote,"siblings unchanged");
   check(RuntimeSettings.configure(app,true,true,true)&&StatusBarEntry.sync(app)&&app.mode==1,"master on restores choice");
   app.denied=true;check(!StatusBarEntry.select(app,false)&&StatusBarEntry.selected(app)&&!StatusBarEntry.error.isEmpty(),"rejected component change rolls selection back");app.denied=false;
   app.failCommit=true;check(!StatusBarEntry.select(app,false)&&StatusBarEntry.selected(app),"failed disk commit rolls in-memory preference back");app.failCommit=false;
   check(StatusBarEntry.select(app,false)&&app.mode==2,"child off disables own entry");
   Bundle shown=StatusBarEntry.extras(app,true),hidden=StatusBarEntry.extras(app,false);
   check(Boolean.TRUE.equals(shown.values.get("flag_status_icon_notification"))&&Integer.valueOf(0x453032).equals(shown.values.get("flag_status_icon_id")),"matches actual host notification contract");
   check(Boolean.FALSE.equals(shown.values.get("flag_status_icon_hide"))&&Boolean.TRUE.equals(hidden.values.get("flag_status_icon_hide")),"hide metadata explicit");
   check(shown.values.size()==7&&!shown.values.containsKey("token")&&!shown.values.containsKey("root"),"no pairing or root secret in host metadata");
   check(StatusBarEntry.select(app,true)&&app.mode==1,"entry reselected");check(StatusBarEntry.disableForExit(app)&&app.mode==2&&StatusBarEntry.selected(app),"full exit removes host entry without resetting selection");
   for(boolean running:new boolean[]{false,true})for(boolean master:new boolean[]{false,true})for(boolean selected:new boolean[]{false,true})for(boolean permission:new boolean[]{false,true}){
    boolean bar=KeepAlivePresentation.statusBar(running,master,selected),overlay=KeepAlivePresentation.overlay(running,master,selected,permission);
    if(!running||!master||!selected)check(!bar&&!overlay,"inactive gate suppresses both children");else check(bar&&overlay==permission,"overlay permission only affects overlay");
   }
   System.out.println("PASS "+checks+" actual status-entry adapter checks: independent saved child, master gate, own component only, write/permission failures and host metadata. SystemUI and Android drawing are not exercised.");
  }finally{try(java.util.stream.Stream<Path> files=Files.list(dir)){for(Path p:(Iterable<Path>)files::iterator)Files.delete(p);}Files.delete(dir);}
 }
}
