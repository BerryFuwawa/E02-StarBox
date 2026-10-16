package com.e02.rootconsole;

import android.app.Activity;
import android.view.View;

/** Optional local research feature; absent from normal builds unless extra sources are supplied. */
final class DisplayTrialEntry {
    private static final String PANEL="com.e02.rootconsole.DisplayTrialPanel";
    static boolean supported(){try{Class.forName(PANEL,false,DisplayTrialEntry.class.getClassLoader());return true;}catch(ClassNotFoundException absent){return false;}}
    static View create(Activity activity){try{return (View)Class.forName(PANEL).getConstructor(Activity.class).newInstance(activity);}catch(Exception error){return Design.text(activity,"显示试验页面暂时不可用，请重新打开研究版后重试。",20);}}
    static boolean busy(){try{return (Boolean)Class.forName(PANEL).getMethod("busy").invoke(null);}catch(ClassNotFoundException absent){return false;}catch(Exception uncertain){return true;}}
    private DisplayTrialEntry(){}
}
