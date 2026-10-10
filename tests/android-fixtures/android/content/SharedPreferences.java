package android.content;
/** Minimal desktop fixture interface for the real RootAccess adapter. */
public interface SharedPreferences {
    int getInt(String key,int fallback);
    default boolean getBoolean(String key,boolean fallback){return fallback;}
    Editor edit();
    interface Editor {Editor putBoolean(String key,boolean value);void apply();default boolean commit(){return false;}}
}
