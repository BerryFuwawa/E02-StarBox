package com.e02.rootconsole;

import android.os.IBinder;
import java.lang.reflect.Method;

/** Read-only references; never edits ServiceManager's process cache or services. */
final class ReadOnlyServiceLookup {
    static IBinder get(String name)throws Exception {
        if(!"display".equals(name)&&!"power".equals(name))throw new IllegalArgumentException("Unsupported service");
        Class<?> type=Class.forName("android.os.ServiceManager");
        IBinder cached=(IBinder)type.getMethod("getService",String.class).invoke(null,name);
        if(cached!=null&&cached.isBinderAlive())return cached;
        // Android 9 getService/checkService may return the same dead cached Binder.
        // Read the authoritative service registry without altering that cache.
        Method accessor=type.getDeclaredMethod("getIServiceManager");accessor.setAccessible(true);
        Object registry=accessor.invoke(null);
        IBinder current=(IBinder)Class.forName("android.os.IServiceManager")
            .getMethod("checkService",String.class).invoke(registry,name);
        return current!=null&&current.isBinderAlive()?current:null;
    }
}
