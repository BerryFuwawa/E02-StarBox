package android.provider;
import android.content.ContentResolver;
public final class Settings {
 public static final class Secure {
  public static final String ENABLED_ACCESSIBILITY_SERVICES="enabled_accessibility_services",ACCESSIBILITY_ENABLED="accessibility_enabled";
  public static String getString(ContentResolver resolver,String name){return null;}
  public static int getInt(ContentResolver resolver,String name,int fallback){return fallback;}
 }
 public static final class Global {
  public static final String ADB_ENABLED="adb_enabled";
  public static int getInt(ContentResolver resolver,String name,int fallback){return fallback;}
  public static boolean putInt(ContentResolver resolver,String name,int value){throw new UnsupportedOperationException("Explicit test driver required for settings writes");}
 }
}
