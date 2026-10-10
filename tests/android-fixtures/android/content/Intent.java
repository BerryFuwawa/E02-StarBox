package android.content;
import java.util.*;
/** Test-only intent. Never packaged or included in official Android API compilation. */
public final class Intent {
    public static final String ACTION_BOOT_COMPLETED="android.intent.action.BOOT_COMPLETED";
    public static final String ACTION_MY_PACKAGE_REPLACED="android.intent.action.MY_PACKAGE_REPLACED";
    private String action;public final Class<?> target;private final Map<String,Boolean> extras=new HashMap<>();
    public Intent(Context context,Class<?> target){this.target=target;}
    public Intent(String action){this.action=action;target=null;}
    public Intent setAction(String action){this.action=action;return this;}
    public String getAction(){return action;}
    public Intent putExtra(String key,boolean value){extras.put(key,value);return this;}
    public boolean getBooleanExtra(String key,boolean fallback){return extras.containsKey(key)?extras.get(key):fallback;}
}
