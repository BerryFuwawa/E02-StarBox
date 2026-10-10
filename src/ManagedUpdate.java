package com.e02.rootconsole;

import java.io.IOException;

/** Fixed update entry point. It stays owned by RootService instead of escaping command cleanup. */
final class ManagedUpdate {
    static int timeout(String operation)throws IOException {
        if("download".equals(operation))return 650;if("install".equals(operation))return 210;if("recover".equals(operation))return 45;throw new IOException("更新操作无效");
    }
    static String command(String apk,String operation)throws IOException {
        timeout(operation);
        if(apk==null||!apk.startsWith("/data/app/")||!apk.endsWith("/base.apk")||apk.indexOf('\0')>=0||apk.indexOf('\n')>=0||apk.indexOf('\r')>=0||apk.contains("/../")||apk.contains("/./"))throw new IOException("更新程序位置无效");
        return "CLASSPATH="+quote(apk)+" exec /system/bin/app_process /system/bin com.e02.rootconsole.RootUpdateWorker "+operation+" "+quote("/data/user/0/com.e02.rootconsole/files/update-task/task.json");
    }
    private static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
}
