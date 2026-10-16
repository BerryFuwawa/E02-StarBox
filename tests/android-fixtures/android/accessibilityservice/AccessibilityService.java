package android.accessibilityservice;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.view.accessibility.AccessibilityEvent;
import java.io.File;
/** Framework binding is a fixture, production lifecycle adapter executes unchanged. */
public abstract class AccessibilityService extends Context {
 public int disableCalls;public AccessibilityServiceInfo info=new AccessibilityServiceInfo();
 protected void onServiceConnected(){}
 public AccessibilityServiceInfo getServiceInfo(){return info;}
 public void setServiceInfo(AccessibilityServiceInfo value){info=value;}
 public void disableSelf(){disableCalls++;}
 public boolean onUnbind(Intent intent){return false;}
 public void onDestroy(){}
 public abstract void onAccessibilityEvent(AccessibilityEvent event);
 public abstract void onInterrupt();
 public File getFilesDir(){throw new UnsupportedOperationException("test directory required");}
 public ApplicationInfo getApplicationInfo(){return new ApplicationInfo();}
 public SharedPreferences getSharedPreferences(String name,int mode){throw new UnsupportedOperationException("test preferences required");}
}
