package com.e02.rootconsole;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;

/** Prepares saved channels on a worker; never opens permission dialogs automatically. */
final class RemoteStartController {
    static final RemoteStartGate gate=new RemoteStartGate();
    static boolean preparing(){return gate.preparing();}
    static void onApplicationOpen(Context context){
        if(!context.getSharedPreferences("remote",0).getBoolean("autoOnOpen",false)||!gate.claimAutomatic())return;
        start(context,true,null);
    }
    static void start(Context context,boolean automatic,Runnable completed){
        Context app=context.getApplicationContext();
        if(RemoteService.running)return;
        long ticket=gate.begin();if(ticket<0)return;
        new Thread(()->{
            SharedPreferences prefs=app.getSharedPreferences("remote",0);
            try{
                if(prefs.getString("config","").trim().isEmpty())throw new IOException("请先填写并保存服务器配置");
                RemoteService.status="正在准备远程连接…";
                RemoteStartFlow.run(new RemoteStartFlow.Driver(){
                    public boolean current(){return gate.current(ticket)&&RuntimeSettings.runningAllowed(app)&&(!automatic||prefs.getBoolean("autoOnOpen",false));}
                    public void checkRoot()throws Exception {
                        long until=android.os.SystemClock.uptimeMillis()+10000;
                        while(current()&&RootAccess.busy()&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(100);
                        if(!current())return;
                        if(RootAccess.busy())throw new IOException("正在处理授权，请稍后点击启动远程连接");
                        RootAccess.check(app,false);
                    }
                    public boolean rootAvailable(){return RootAccess.available();}
                    public boolean adbAvailable(){return LocalAdb.available();}
                    public void enableAdb()throws Exception {
                        CommandRunner runner=RootAccess.runner(app);CommandRunner.Result result;
                        try{synchronized(AdbControl.LOCK){result=runner.execute(AdbControl.ensureLocal(),true,10);}}finally{runner.close();}
                        if(result.exit!=0||result.timedOut||!result.error.isEmpty())throw new IOException("无线 ADB 准备失败："+UserMessages.explain(result.error.isEmpty()?result.stderr:result.error));
                    }
                    public boolean webRunning(){return ConsoleService.server!=null;}
                    public void startWeb(boolean root)throws Exception{ConsoleService.startSession(app,ConsoleService.lanHost(app),root);}
                    public void saveConfig()throws Exception{
                        String config=RemoteConfig.client(prefs.getString("host",""),RemoteConfig.port(prefs.getString("port","7000")),prefs.getString("token",""),prefs.getString("secret",""),"127.0.0.1",ConsoleService.webPort(),prefs.getBoolean("console",true),prefs.getBoolean("adb",false),prefs.getString("user",""));
                        if(!prefs.edit().putString("config",config).putString("format","toml").commit())throw new IOException("无法保存远程配置");
                    }
                    public void launch(){RemoteService.startPrepared(app,ticket);}
                },prefs.getInt("mode",0)==0,prefs.getBoolean("console",true),prefs.getBoolean("adb",false));
            }catch(Exception error){if(gate.current(ticket)&&RuntimeSettings.runningAllowed(app))RemoteService.status="远程启动失败："+UserMessages.explain(error);}
            finally{
                if(automatic&&gate.current(ticket)&&!prefs.getBoolean("autoOnOpen",false)&&!RemoteService.running)RemoteService.status="已取消自动启动";
                gate.finish(ticket);if(completed!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(completed);
            }
        },"starbox-remote-prepare").start();
    }
}
