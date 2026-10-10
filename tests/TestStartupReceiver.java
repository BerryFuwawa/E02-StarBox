package com.e02.rootconsole;

import android.content.*;
import android.content.pm.ApplicationInfo;
import java.io.*;
import java.nio.file.*;
import java.lang.reflect.Field;

/** Executes the production receiver and real settings store; delivery/FGS APIs are fixtures. */
public final class TestStartupReceiver {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static final class App extends Context implements SharedPreferences {
        final File directory;Intent last;int starts;boolean denied,accepted=true;
        App(File directory){this.directory=directory;}
        public File getFilesDir(){return directory;}public ApplicationInfo getApplicationInfo(){return new ApplicationInfo();}
        public SharedPreferences getSharedPreferences(String name,int mode){return this;}
        public int getInt(String key,int fallback){return key.equals("acceptedVersion")&&accepted?1:fallback;}
        public Editor edit(){throw new UnsupportedOperationException();}
        public ComponentName startForegroundService(Intent intent){if(denied)throw new IllegalStateException("fixture restriction");starts++;last=intent;return new ComponentName();}
    }
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("starbox-receiver-");App app=new App(directory.toFile());StartupReceiver receiver=new StartupReceiver();
        try {
            check(RuntimeSettings.open(app));
            for(int bits=0;bits<8;bits++){
                boolean keep=(bits&1)!=0,boot=(bits&2)!=0,park=(bits&4)!=0;
                check(RuntimeSettings.configure(app,keep,boot,park));
                byte[] saved=Files.readAllBytes(directory.resolve("lifecycle-state"));
                String[] actions={Intent.ACTION_BOOT_COMPLETED,"com.ecarx.intent.action.SYSTEM_READY","com.ecarx.intent.action.POWER_ON","com.ecarx.intent.action.WAKEUP","com.ecarx.intent.action.STR_RESUME","com.geely.intent.action.WAKEUP",Intent.ACTION_MY_PACKAGE_REPLACED};
                for(int index=0;index<actions.length;index++){
                    int before=app.starts;receiver.onReceive(app,new Intent(actions[index]));boolean allowed=index==0?boot:index==6&&(keep||boot);
                    check(app.starts==before+(allowed?1:0));
                    if(allowed){check(app.last.target==ConsoleService.class&&app.last.getAction().equals("restore"));check(app.last.getBooleanExtra("coldStart",false)==(index==0));}
                    check(java.util.Arrays.equals(saved,Files.readAllBytes(directory.resolve("lifecycle-state"))));
                }
                int before=app.starts;receiver.onReceive(app,new Intent("android.intent.action.REBOOT"));receiver.onReceive(app,new Intent("arbitrary-event"));receiver.onReceive(app,null);check(app.starts==before);
                app.accepted=false;receiver.onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));check(app.starts==before);app.accepted=true;
            }
            check(RuntimeSettings.configure(app,true,true,true));app.denied=true;receiver.onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));check(RuntimeRecovery.message.equals("系统暂未允许恢复服务，请打开星匣"));app.denied=false;
            check(RuntimeSettings.configure(app,false,false,false));check(RuntimeSettings.requestExit(app).has(RuntimePolicy.Effect.STOP_ALL_OWNED));int before=app.starts;receiver.onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));receiver.onReceive(app,new Intent("com.ecarx.intent.action.STR_RESUME"));check(app.starts==before);
            // Explicit exit leaves boot checked; only an actual BOOT_COMPLETED can reopen it.
            check(RuntimeSettings.open(app));check(RuntimeSettings.configure(app,false,true,true));
            check(RuntimeSettings.requestExit(app).has(RuntimePolicy.Effect.STOP_ALL_OWNED));
            before=app.starts;
            receiver.onReceive(app,new Intent("com.ecarx.intent.action.POWER_ON"));
            receiver.onReceive(app,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));check(app.starts==before);
            receiver.onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));check(app.starts==before+1);
            check(RuntimeSettings.restore(app,true));check(!RuntimeSettings.snapshot(app).exited&&RuntimeSettings.snapshot(app).autoStart);
            before=app.starts;
            // Recreate the Android adapter after corrupt storage. The actual receiver must fail closed.
            Field policy=RuntimeSettings.class.getDeclaredField("policy");policy.setAccessible(true);policy.set(null,null);
            Files.write(directory.resolve("lifecycle-state"),"bad-state".getBytes("US-ASCII"));receiver.onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));check(app.starts==before&&!RuntimeSettings.healthy(app));
            System.out.println("PASS "+checks+" production receiver checks: real persisted eight-option combinations, exact event filter, private explicit service target, consent/exit/corrupt-storage gates and rejected service start. No real Android broadcast delivery, vehicle power or services.");
        } finally {try(java.util.stream.Stream<Path> files=Files.list(directory)){for(Path path:(Iterable<Path>)files::iterator)Files.delete(path);}Files.delete(directory);}
    }
}
