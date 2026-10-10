package android.content.pm;
import android.content.ComponentName;
public abstract class PackageManager {
 public static final int COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1,COMPONENT_ENABLED_STATE_DISABLED=2,DONT_KILL_APP=1;
 public abstract int getComponentEnabledSetting(ComponentName component);
 public abstract void setComponentEnabledSetting(ComponentName component,int state,int flags);
}
