package com.e02.rootconsole;

import android.content.*;
import java.io.*;
import java.nio.file.*;
import java.lang.reflect.Field;

public final class TestSystemKeepAliveService {
    private static int checks;
    private static final String BOOT="2b615682-ae3c-4179-ab03-cdc62af8c861",OLD="4d0cc252-28ba-4060-89b4-c8ac717e473f";
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static final class App extends SystemKeepAliveService implements SharedPreferences {
        final File directory;boolean accepted=true,requested=true,denied;int starts;Intent last;
        App(File directory){this.directory=directory;}
        @Override public File getFilesDir(){return directory;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return this;}
        @Override public int getInt(String key,int fallback){return key.equals("acceptedVersion")&&accepted?1:fallback;}
        @Override public boolean getBoolean(String key,boolean fallback){return key.equals("systemKeepAliveRequested")?requested:fallback;}
        @Override public Editor edit(){throw new UnsupportedOperationException("no preference writes expected during binding");}
        @Override public ComponentName startForegroundService(Intent intent){if(denied)throw new IllegalStateException("fixture rejected start");last=intent;starts++;return new ComponentName();}
        @Override protected String bootIdentity(){return BOOT;}
    }
    static void state(File directory,boolean boot,boolean exited)throws Exception {
        check(new LifecycleFileStore(directory).commit(new RuntimePolicy.Snapshot(true,true,boot,false,exited,false,1)));
        Field policy=RuntimeSettings.class.getDeclaredField("policy");policy.setAccessible(true);policy.set(null,null);
    }
    public static void main(String[] args)throws Exception {
        Path path=Files.createTempDirectory("e02-system-binding-");App app=new App(path.toFile());
        try{
            state(app.directory,true,false);check(BootSession.remember(app.directory,BOOT));
            byte[] original=Files.readAllBytes(path.resolve("lifecycle-state"));
            app.onServiceConnected();check(app.starts==1);check(app.last.target==ConsoleService.class);check(!app.last.getBooleanExtra("coldStart",true));
            check(app.info.eventTypes==0&&app.info.flags==0);check(java.util.Arrays.equals(original,Files.readAllBytes(path.resolve("lifecycle-state"))));check(!RuntimeSettings.snapshot(app).rootAllowed);
            state(app.directory,true,true);app.onServiceConnected();check(app.starts==1); // Same boot: exit remains in force.
            check(BootSession.remember(app.directory,OLD));app.onServiceConnected();check(app.starts==2&&app.last.getBooleanExtra("coldStart",false));
            check(RuntimeSettings.snapshot(app).exited); // Only the real ConsoleService applies recovery.
            state(app.directory,false,false);app.onServiceConnected();check(app.starts==2); // Boot checkbox off.
            state(app.directory,true,false);app.accepted=false;app.onServiceConnected();check(app.starts==2);app.accepted=true;
            app.requested=false;int disabled=app.disableCalls;app.onServiceConnected();check(app.disableCalls==disabled+1&&app.starts==2);app.requested=true;
            app.denied=true;app.onServiceConnected();check(app.starts==2);check(new String(Files.readAllBytes(path.resolve("startup-events")),"UTF-8").contains("system-binding denied"));app.denied=false;
            Files.delete(path.resolve("service-boot-id"));app.onServiceConnected();check(app.starts==2); // Missing baseline fails closed.
            Files.write(path.resolve("lifecycle-state"),"broken".getBytes("UTF-8"));Field policy=RuntimeSettings.class.getDeclaredField("policy");policy.setAccessible(true);policy.set(null,null);app.onServiceConnected();check(app.starts==2&&!RuntimeSettings.healthy(app));
            disabled=app.disableCalls;SystemKeepAliveService.detach();check(app.disableCalls==disabled+1);
            app.onAccessibilityEvent(new android.view.accessibility.AccessibilityEvent());app.onInterrupt();check(app.starts==2);
            app.onDestroy();SystemKeepAliveService.detach();check(app.disableCalls==disabled+1);
            System.out.println("PASS "+checks+" production system-binding adapter checks. Framework binding is simulated; no vehicle startup tested.");
        }finally{try(java.util.stream.Stream<Path> files=Files.list(path)){for(Path file:(Iterable<Path>)files::iterator)Files.delete(file);}Files.delete(path);}
    }
}
