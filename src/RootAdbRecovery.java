package com.e02.rootconsole;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Separate explicit opt-in; old Root consent never silently grants ADB preparation. */
final class RootAdbRecovery {
    static final String PERMISSION="android.permission.WRITE_SECURE_SETTINGS";
    private static final String OPTION="autoPrepareLocalAdb";
    private static int rootRequests;
    private static String lastObservation="";
    static boolean selected(Context context){return context.getSharedPreferences("connection",0).getBoolean(OPTION,false);}
    static synchronized void reset(){rootRequests=0;lastObservation="";}
    static void configure(Context context,boolean selected)throws IOException {
        if(selected&&context.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED) {
            if(!RootAccess.available())throw new IOException("请先完成一次 Root 授权，再启用自动恢复");
            CommandRunner runner=RootAccess.runner(context);CommandRunner.Result result;
            try{result=runner.execute("pm grant com.e02.rootconsole android.permission.WRITE_SECURE_SETTINGS",true,5);}finally{runner.close();}
            if(result.exit!=0||result.timedOut||!result.error.isEmpty()||context.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED)
                throw new IOException("系统未允许自动准备 ADB，原有 Root 授权仍可使用");
        }
        if(!context.getSharedPreferences("connection",0).edit().putBoolean(OPTION,selected).commit())throw new IOException("自动恢复设置未保存，请重试");
        AppLog.record(context.getFilesDir(),"授权恢复",selected?"已允许自动准备本机 ADB":"已关闭自动准备本机 ADB");
    }
    static ReadOnlyAdbProbe.Result prepare(Context context,BooleanSupplier current) {
        final Context app=context.getApplicationContext();
        return AdbRecoveryFlow.prepare(new AdbRecoveryFlow.Driver(){
            private String host="127.0.0.1";
            public boolean current(){return current.getAsBoolean();}
            public boolean optedIn(){return selected(app);}
            public boolean rootAllowed(){try{return RuntimeSettings.automaticRootAllowed(app)&&!RootPermit.userEnded(app.getFilesDir());}catch(IOException invalid){return false;}}
            public boolean permission(){return app.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED;}
            public int adbEnabled(){return Settings.Global.getInt(app.getContentResolver(),Settings.Global.ADB_ENABLED,-1);}
            private boolean allowed(){return current()&&optedIn()&&rootAllowed();}
            public boolean enableAdb(){
                synchronized(RootAccess.class){
                    if(!allowed()||!permission())return false;
                    try{boolean saved=Settings.Global.putInt(app.getContentResolver(),Settings.Global.ADB_ENABLED,1);if(saved)AppLog.record(app.getFilesDir(),"授权恢复","已请求准备本机 ADB");return saved;}
                    catch(RuntimeException denied){AppLog.record(app.getFilesDir(),"授权恢复","系统未允许准备本机 ADB");return false;}
                }
            }
            public ReadOnlyAdbProbe.Result probe(){
                ReadOnlyAdbProbe.Result observed=ReadOnlyAdbProbe.probe(5555);host=observed.host;
                if(current())synchronized(RootAdbRecovery.class){
                    String value=observed.status+"|"+host+"|"+optedIn()+"|"+permission()+"|"+rootAllowed();
                    if(!value.equals(lastObservation)){
                        lastObservation=value;
                        String text=observed.status==ReadOnlyAdbProbe.Status.ROOT_READY?("::1".equals(host)?"本机 IPv6 连接可用，已校验 Root 身份":"本机 ADB 已校验 Root 身份"):RootBootstrap.explain(observed.status);
                        if(!optedIn())text+="；未启用自动准备 ADB";
                        else if(!permission())text+="；自动准备 ADB 的系统权限未授予";
                        if(!rootAllowed())text+="；当前未允许自动恢复授权";
                        AppLog.record(app.getFilesDir(),"授权恢复",text);
                    }
                }
                return observed;
            }
            public boolean rootSupported(){
                synchronized(RootAdbRecovery.class){if(rootRequests>=3)return false;}
                CommandRunner runner=new CommandRunner("/system/bin/sh","/system/bin/sh");
                try{CommandRunner.Result result=runner.execute("getprop ro.debuggable",false,2);return result.exit==0&&!result.timedOut&&"1".equals(result.stdout.trim());}finally{runner.close();}
            }
            public void requestRoot(){
                synchronized(RootAccess.class){
                    if(!allowed())return;
                    synchronized(RootAdbRecovery.class){if(rootRequests>=3)return;rootRequests++;}
                    LocalAdbRootRequest.Result result=LocalAdbRootRequest.request(5555,host,this::allowed);
                    AppLog.record(app.getFilesDir(),"授权恢复",result==LocalAdbRootRequest.Result.REQUESTED?"已请求本机 ADB 恢复 Root，等待身份校验":"本机 ADB 尚未提供 Root，保留其他授权方式");
                }
            }
            public void awaitAdb(){
                long deadline=System.nanoTime()+8000000000L;
                while(allowed()&&System.nanoTime()<deadline&&!RootBootstrap.listening(5555,host)) {
                    try{Thread.sleep(200);}catch(InterruptedException cancelled){Thread.currentThread().interrupt();return;}
                }
            }
        });
    }
}
