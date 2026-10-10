package com.e02.rootconsole;

/** Shared manual/automatic preparation; no Android, shell, scheduling or power APIs. */
final class RemoteStartFlow {
    interface Driver {
        boolean current(); void checkRoot()throws Exception; boolean rootAvailable();
        boolean adbAvailable(); void enableAdb()throws Exception;
        boolean webRunning(); void startWeb(boolean root)throws Exception;
        void saveConfig()throws Exception; void launch()throws Exception;
    }
    static boolean run(Driver d,boolean own,boolean web,boolean adb)throws Exception {
        if(!d.current())return false;
        if(own){
            if(!web&&!adb)throw new IllegalArgumentException("请至少选择一个远程服务");
            d.checkRoot();if(!d.current())return false;
            if(adb&&!d.adbAvailable()){
                if(!d.rootAvailable())throw new IllegalStateException("请提权后再开启远程 ADB");
                d.enableAdb();if(!d.current())return false;
                if(!d.adbAvailable())throw new IllegalStateException("ADB 尚未就绪，请重新检测");
            }
            if(!d.current())return false;
            if(web&&!d.webRunning()){d.startWeb(d.rootAvailable());if(!d.current())return false;}
            d.saveConfig();if(!d.current())return false;
        }
        if(!d.current())return false;
        d.launch();return true;
    }
}
