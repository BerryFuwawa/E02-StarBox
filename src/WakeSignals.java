package com.e02.rootconsole;

/** Observes notifications only; never sends power broadcasts or wakes the device. */
final class WakeSignals {
    // CarPowerCustomService sends this protected event with REGISTERED_ONLY.
    // Keep it in the live service filter, not as a manifest cold-start trigger.
    static final String DISPLAY_ON="ecarx.intent.action.DISPLAY_ON";
    static String[] registeredActions() {
        return new String[]{"android.intent.action.SCREEN_ON","com.ecarx.intent.action.WAKEUP","com.ecarx.intent.action.STR_RESUME",DISPLAY_ON};
    }
    static boolean observed(String action) {
        if(action==null)return false;
        for(String expected:registeredActions())if(expected.equals(action))return true;
        return false;
    }
}
