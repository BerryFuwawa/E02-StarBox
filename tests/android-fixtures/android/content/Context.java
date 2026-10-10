package android.content;
import java.io.File;
import android.content.pm.ApplicationInfo;
/** Desktop fixture only. Never included in the production API28 compile or APK. */
public abstract class Context {
    public ComponentName startForegroundService(Intent intent) { throw new UnsupportedOperationException("fixture must record service request"); }
    public Context getApplicationContext(){return this;}
    public String getPackageName(){return "com.e02.rootconsole";}
    public android.content.pm.PackageManager getPackageManager(){throw new UnsupportedOperationException();}
    public android.content.res.Resources getResources(){return new android.content.res.Resources();}
    public abstract File getFilesDir();
    public abstract SharedPreferences getSharedPreferences(String name,int mode);
    public abstract ApplicationInfo getApplicationInfo();
}
