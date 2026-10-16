package com.e02.rootconsole;

/** Consent and cancellation boundaries for restoring native local ADB. */
final class AdbRecoveryFlow {
    interface Driver {
        boolean current();
        boolean optedIn();
        boolean rootAllowed();
        boolean permission();
        int adbEnabled();
        boolean enableAdb();
        ReadOnlyAdbProbe.Result probe();
        boolean rootSupported();
        void requestRoot();
        void awaitAdb();
    }
    static ReadOnlyAdbProbe.Result prepare(Driver driver) {
        ReadOnlyAdbProbe.Result observed=driver.probe();
        if(!allowed(driver)||observed.status==ReadOnlyAdbProbe.Status.ROOT_READY)return observed;
        if(observed.status==ReadOnlyAdbProbe.Status.AUTH_REQUIRED||observed.status==ReadOnlyAdbProbe.Status.OTHER_UID)return observed;
        if(observed.status==ReadOnlyAdbProbe.Status.UNAVAILABLE&&driver.permission()&&driver.adbEnabled()==0) {
            if(!allowed(driver)||!driver.enableAdb())return observed;
            if(!allowed(driver))return observed;
            driver.awaitAdb();if(!allowed(driver))return observed;observed=driver.probe();
        }
        if(observed.status==ReadOnlyAdbProbe.Status.SHELL_ONLY&&allowed(driver)&&driver.rootSupported()) {
            if(!allowed(driver))return observed;
            driver.requestRoot();
            if(!allowed(driver))return observed;
            driver.awaitAdb();if(!allowed(driver))return observed;observed=driver.probe();
        }
        return observed;
    }
    private static boolean allowed(Driver driver){return driver.current()&&driver.optedIn()&&driver.rootAllowed();}
}
