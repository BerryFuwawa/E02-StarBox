package com.e02.rootconsole;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Coalesced recovery of services explicitly left running; never wakes the vehicle. */
final class RuntimeRecovery {
    static volatile String message="";
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static long generation;
    private static boolean running,pending,scheduled;
    private static int retries;
    private static Runnable retry;
    static synchronized boolean working(){return running||scheduled;}
    static synchronized void cancel() {
        generation++;pending=false;scheduled=false;retries=0;
        if(retry!=null)main.removeCallbacks(retry);retry=null;
    }
    static synchronized void request(Context context) {
        Context app=context.getApplicationContext();
        RuntimePolicy.Snapshot state=RuntimeSettings.snapshot(app);
        if(!RecoveryPolicy.restoreChannels(state,RiskNotice.accepted(app),RuntimeSettings.healthy(app)))return;
        if(running){pending=true;return;}
        if(scheduled)return;
        long ticket=generation;running=true;
        new Thread(()->run(app,ticket),"starbox-service-recovery").start();
    }
    private static synchronized boolean current(Context app,long ticket) {
        return ticket==generation&&RuntimeSettings.runningAllowed(app)&&RecoveryPolicy.restoreChannels(RuntimeSettings.snapshot(app),true,RuntimeSettings.healthy(app));
    }
    private static void run(Context app,long ticket) {
        boolean complete=false;
        try {
            RecoveryFlow.Result result=RecoveryFlow.restore(new RecoveryFlow.Driver(){
                public boolean current(){return RuntimeRecovery.current(app,ticket);}
                public boolean rootBusy(){return RootAccess.busy();}
                public void checkRoot(){RootAccess.check(app,false);}
                public boolean rootNeeded(){try{return RuntimeSettings.automaticRootAllowed(app)&&!RootPermit.userEnded(app.getFilesDir());}catch(java.io.IOException invalid){return false;}}
                public boolean rootAvailable(){return RootAccess.available();}
                public boolean restoreWeb()throws Exception{return ConsoleService.restoreSession(app);}
                public boolean restoreRemote()throws Exception{return RemoteService.restore(app);}
                public void maintainAdb(){AdbMaintenance.refresh(app);}
            });
            complete=result==RecoveryFlow.Result.READY;
            if(current(app,ticket))switch(result){
                case READY:message="已请求恢复，实际连接状态请查看连接与远程页面";break;
                case BUSY:message="正在处理授权，稍后恢复连接";break;
                case ROOT_WAIT:message="等待独立 Root 授权恢复";break;
                case WEB_WAIT:message="网页回传等待 Root 授权恢复";break;
                case ADB_WAIT:message="远程连接等待本机 ADB 或 Root 授权恢复";break;
                default:break;
            }
        } catch(Exception error) {if(current(app,ticket))message="连接恢复未完成："+UserMessages.explain(error);}
        finally {
            synchronized(RuntimeRecovery.class) {
                running=false;
                if(!current(app,ticket)){if(pending&&RuntimeSettings.runningAllowed(app)){pending=false;request(app);}return;}
                if(complete)retries=0;
                if(pending){pending=false;request(app);return;}
                if(!complete&&retries<RecoveryPolicy.RETRY_SECONDS.length) {
                    int delay=RecoveryPolicy.RETRY_SECONDS[retries++];scheduled=true;
                    retry=()->{synchronized(RuntimeRecovery.class){if(ticket!=generation)return;scheduled=false;retry=null;request(app);}};
                    main.postDelayed(retry,delay*1000L);
                }
            }
        }
    }
}
