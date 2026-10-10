package com.e02.rootconsole;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
import java.io.File;
import java.lang.reflect.*;

/** Android 9 Binder adapter in our existing UID 0 process. Passive, own service only. */
final class RootWakeObserver implements AutoCloseable {
    static final String ACTION="ecarx.intent.action.DISPLAY_ON";
    static final String RESTORE="com.e02.rootconsole.RECOVER_AFTER_WAKE";
    private static final String DESCRIPTOR="android.content.IIntentReceiver";
    private final File directory;private final RootPermit.Ticket permit;
    private final WakeRestoreController controller=new WakeRestoreController();
    private final OwnedRootCommandRunner runner=new OwnedRootCommandRunner();
    private Object manager,receiver;private Class<?> managerType,receiverType;
    private boolean registered;private volatile boolean closed;private volatile Thread worker;private volatile String status="unavailable";
    RootWakeObserver(File directory,RootPermit.Ticket permit){this.directory=directory;this.permit=permit;}
    private Object call(String name,Class<?>[] types,Object... args)throws Exception {
        Method method=managerType.getMethod(name,types);method.setAccessible(true);return method.invoke(manager,args);
    }
    void register() {
        if(!WakeRecoveryOptions.available()){status="disabled";return;}
        if(permit==null||android.os.Process.myUid()!=0||!RootPermit.matches(permit))return;
        try {
            Class<?> activityManager=Class.forName("android.app.ActivityManager");
            Method getService=activityManager.getDeclaredMethod("getService");getService.setAccessible(true);manager=getService.invoke(null);
            managerType=Class.forName("android.app.IActivityManager");receiverType=Class.forName(DESCRIPTOR);
            Method asInterface=Class.forName(DESCRIPTOR+"$Stub").getDeclaredMethod("asInterface",IBinder.class);asInterface.setAccessible(true);
            receiver=asInterface.invoke(null,new Receiver());
            call("registerReceiver",new Class<?>[]{Class.forName("android.app.IApplicationThread"),String.class,receiverType,IntentFilter.class,String.class,int.class,int.class},null,null,receiver,new IntentFilter(ACTION),null,0,0);
            registered=true;status="registered";System.out.println("Starbox wake observer registered");
        }catch(Exception error){System.out.println("Starbox wake observer unavailable: "+error.getClass().getSimpleName());}
    }
    private boolean allowed(){
        if(!WakeRecoveryOptions.available()||closed||!RootPermit.matches(permit))return false;
        try{return WakeRecoveryOptions.mayRestore(new LifecycleFileStore(directory).load(),WakeRecoveryOptions.read(directory));}
        catch(Exception invalid){return false;}
    }
    String status(){return closed?"stopped":status;}
    private void observed(){
        if(!allowed())return;
        long ticket=controller.begin();if(ticket<0)return;
        Thread next=new Thread(()->controller.run(ticket,new WakeRestoreController.Driver(){
            public boolean allowed(){return RootWakeObserver.this.allowed();}
            public void delay(int seconds)throws InterruptedException{Thread.sleep(seconds*1000L);}
            public boolean request(){
                if(!allowed())return false;
                // Fixed own component and a validated generation, no user shell text.
                CommandRunner.Result result=runner.execute("/system/bin/am start-foreground-service --user 0 -n com.e02.rootconsole/.ConsoleService -a "+RESTORE+" --es wakeGeneration "+permit.nonce,true,8);
                boolean accepted=result.exit==0&&!result.timedOut&&result.error.isEmpty()&&!result.stdout.contains("Error")&&!result.stderr.contains("Error");
                System.out.println(accepted?"Starbox wake recovery requested":"Starbox wake recovery request failed");return accepted;
            }
        }),"starbox-wake-restore");next.setDaemon(true);worker=next;next.start();
    }
    private final class Receiver extends Binder {
        Receiver(){attachInterface(null,DESCRIPTOR);}
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
            if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(DESCRIPTOR);return true;}
            if(code!=FIRST_CALL_TRANSACTION)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(DESCRIPTOR);
            Intent intent=data.readInt()!=0?Intent.CREATOR.createFromParcel(data):null;
            int resultCode=data.readInt();String resultData=data.readString();Bundle extras=data.readInt()!=0?Bundle.CREATOR.createFromParcel(data):null;
            boolean ordered=data.readInt()!=0;data.readInt();data.readInt();
            try{if(intent!=null&&ACTION.equals(intent.getAction()))observed();}
            finally{if(ordered)try{call("finishReceiver",new Class<?>[]{IBinder.class,int.class,String.class,Bundle.class,boolean.class,int.class},this,resultCode,resultData,extras,false,intent==null?0:intent.getFlags());}catch(Exception ignored){}}
            if(reply!=null)reply.writeNoException();return true;
        }
    }
    @Override public void close(){
        closed=true;controller.close();runner.close();Thread active=worker;if(active!=null)active.interrupt();
        if(registered){registered=false;try{call("unregisterReceiver",new Class<?>[]{receiverType},receiver);}catch(Exception ignored){}}
    }
}
