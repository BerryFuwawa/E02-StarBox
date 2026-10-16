package com.e02.rootconsole;

import android.accessibilityservice.*;
import android.content.*;
import android.view.accessibility.AccessibilityEvent;

/** A lifecycle-only system binding. No UI event subscription, window access or gestures. */
public class SystemKeepAliveService extends AccessibilityService {
    private static volatile SystemKeepAliveService instance;
    static boolean connected(){return instance!=null;}
    static void detach(){SystemKeepAliveService active=instance;if(active!=null)active.disableSelf();}
    @Override protected void onServiceConnected(){
        super.onServiceConnected();instance=this;SystemKeepAlive.changed();
        try{AccessibilityServiceInfo info=getServiceInfo();
            if(info!=null){info.eventTypes=0;info.flags=0;setServiceInfo(info);}
        }catch(RuntimeException failed){StartupTrace.record(getFilesDir(),"system-binding","denied");disableSelf();return;}
        StartupTrace.record(getFilesDir(),"system-binding","connected");
        if(!SystemKeepAlive.requested(this)||!RuntimeSettings.snapshot(this).keepAlive){disableSelf();return;}
        String current=bootIdentity(),previous=BootSession.previous(getFilesDir());
        boolean known=BootSession.valid(current)&&BootSession.valid(previous);
        SystemKeepAliveGate.Decision decision=SystemKeepAliveGate.decide(RuntimeSettings.snapshot(this),
            RiskNotice.accepted(this),RuntimeSettings.healthy(this),true,known,known&&!current.equals(previous));
        if(decision==SystemKeepAliveGate.Decision.SKIP){StartupTrace.record(getFilesDir(),"system-binding","skipped");return;}
        try{
            startForegroundService(new Intent(this,ConsoleService.class).setAction("restore")
                .putExtra("coldStart",decision==SystemKeepAliveGate.Decision.COLD_START));
            StartupTrace.record(getFilesDir(),"system-binding",decision==SystemKeepAliveGate.Decision.COLD_START?"cold-requested":"restore-requested");
        }catch(RuntimeException denied){StartupTrace.record(getFilesDir(),"system-binding","denied");}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    protected String bootIdentity(){return BootSession.current();}
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(Intent intent){instance=null;SystemKeepAlive.changed();StartupTrace.record(getFilesDir(),"system-binding","disconnected");return super.onUnbind(intent);}
    @Override public void onDestroy(){instance=null;SystemKeepAlive.changed();super.onDestroy();}
}
