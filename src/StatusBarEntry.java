package com.e02.rootconsole;

import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;

/** Only toggles our own optional component and publishes our own notification metadata. */
final class StatusBarEntry {
    static final String CHOICE="statusBarKeepAlive";
    static volatile String error="";
    // Host-only interface classes are intentionally absent from the app class loader.
    private static ComponentName component(Context c){return new ComponentName(c.getPackageName(),"com.e02.rootconsole.StarboxStatusBarPlugin");}
    static boolean wanted(Context c){return KeepAlivePresentation.statusBar(RuntimeSettings.runningAllowed(c),RuntimeSettings.snapshot(c).keepAlive,c.getSharedPreferences("connection",0).getBoolean(CHOICE,false));}
    static boolean selected(Context c){return c.getSharedPreferences("connection",0).getBoolean(CHOICE,false);}
    static synchronized boolean select(Context c,boolean on){
        boolean before=selected(c);
        if(!c.getSharedPreferences("connection",0).edit().putBoolean(CHOICE,on).commit()){c.getSharedPreferences("connection",0).edit().putBoolean(CHOICE,before).commit();error="状态栏设置未保存，请重试";return false;}
        if(sync(c))return true;
        if(!c.getSharedPreferences("connection",0).edit().putBoolean(CHOICE,before).commit())error="状态栏设置保存失败，请重新打开星匣检查";
        return false;
    }
    static synchronized boolean sync(Context c){
        try{
            PackageManager pm=c.getPackageManager();int value=pm.getComponentEnabledSetting(component(c));boolean enabled=value==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            boolean desired=wanted(c);if(desired!=enabled)pm.setComponentEnabledSetting(component(c),desired?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            error="";return true;
        }catch(RuntimeException rejected){error="系统暂未允许切换状态栏入口，请重试";return false;}
    }
    static synchronized boolean disableForExit(Context c){try{if(c.getPackageManager().getComponentEnabledSetting(component(c))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)c.getPackageManager().setComponentEnabledSetting(component(c),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);return true;}catch(RuntimeException rejected){error="状态栏入口未能关闭，请在设置中重试";return false;}}
    static Bundle extras(Context c,boolean visible){
        int id=c.getResources().getIdentifier("ic_status_starbox","drawable",c.getPackageName());Bundle values=new Bundle();if(id==0)return values;
        values.putBoolean("flag_status_icon_notification",true);values.putInt("flag_status_icon_id",StarboxStatusBarPlugin.ENTRY_ID);
        values.putString("flag_status_icon_describe","E02星匣");values.putInt("flag_status_icon_space_x",1);
        values.putParcelable("flag_status_icon_icon",Icon.createWithResource(c.getPackageName(),id));values.putParcelable("flag_status_icon_pressed_icon",Icon.createWithResource(c.getPackageName(),id));
        values.putBoolean("flag_status_icon_hide",!visible);return values;
    }
}
