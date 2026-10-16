package com.e02.rootconsole;

import android.content.*;
import android.provider.Settings;

/** User-controlled Android binding. Never writes the accessibility permission list. */
final class SystemKeepAlive {
    private static java.lang.ref.WeakReference<Runnable> listener=new java.lang.ref.WeakReference<Runnable>(null);
    static void watch(Runnable callback){listener=new java.lang.ref.WeakReference<Runnable>(callback);}
    static void unwatch(Runnable callback){if(listener.get()==callback)listener.clear();}
    static void changed(){Runnable callback=listener.get();if(callback!=null)callback.run();}
    private static final String CHOICE="systemKeepAliveRequested";
    private static ComponentName component(Context c){return new ComponentName(c.getPackageName(),SystemKeepAliveService.class.getName());}
    static boolean requested(Context c){return c.getSharedPreferences("connection",0).getBoolean(CHOICE,false);}
    static boolean automatic(Context c){try{return AuxiliaryRepairChoice.enabled(c.getFilesDir());}catch(java.io.IOException invalid){return false;}}
    static String state(Context c){return requested(c)?enabled(c)?automatic(c)?SystemKeepAliveService.connected()?RootAccess.wakeReady()?"已启用":"待检测恢复服务":"等待系统绑定":"待启用自动恢复":"授权待恢复":"未启用";}
    static boolean enabled(Context c){
        try{if(Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false;
            String list=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(list!=null)for(String value:list.split(":"))if(component(c).equals(ComponentName.unflattenFromString(value)))return true;
        }catch(RuntimeException ignored){}return false;
    }
    static boolean prepare(Context c){
        if(!RiskNotice.accepted(c)||!RuntimeSettings.healthy(c)||!RuntimeSettings.snapshot(c).keepAlive)return false;
        if(!BootSession.remember(c.getFilesDir(),BootSession.current()))return false;
        if(!c.getSharedPreferences("connection",0).edit().putBoolean(CHOICE,true).commit())return false;
        return AuxiliaryRepairChoice.write(c.getFilesDir(),true);
    }
    static boolean disable(Context c){
        if(!AuxiliaryRepairChoice.write(c.getFilesDir(),false))return false;
        if(!c.getSharedPreferences("connection",0).edit().putBoolean(CHOICE,false).commit())return false;
        SystemKeepAliveService.detach();return true;
    }
    static void syncOptions(Context c){if(!RuntimeSettings.snapshot(c).keepAlive)disable(c);}
}
