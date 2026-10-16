package com.e02.rootconsole;

/** Actual recovery order, shared by Android and deterministic cancellation tests. */
final class RecoveryFlow {
    enum Result { READY, BUSY, CANCELLED, ROOT_WAIT, WEB_WAIT, ADB_WAIT }
    interface Driver {
        boolean current();
        boolean rootBusy();
        void checkRoot() throws Exception;
        boolean rootNeeded();
        boolean rootAvailable();
        boolean restoreWeb() throws Exception;
        boolean restoreRemote() throws Exception;
        void maintainAdb();
    }
    static Result restore(Driver driver)throws Exception {
        if(!driver.current())return Result.CANCELLED;
        if(driver.rootBusy())return Result.BUSY;
        driver.checkRoot();
        if(!driver.current())return Result.CANCELLED;
        boolean webReady=driver.restoreWeb();
        if(!driver.current())return Result.CANCELLED;
        // Each channel checks its own prerequisites. A Root webpage waiting for
        // authorization must not prevent an independent ADB-only FRP tunnel.
        boolean remoteReady=driver.restoreRemote();
        if(!driver.current())return Result.CANCELLED;
        driver.maintainAdb();
        if(!driver.current())return Result.CANCELLED;
        if(!webReady)return Result.WEB_WAIT;
        if(!remoteReady)return Result.ADB_WAIT;
        return driver.rootNeeded()&&!driver.rootAvailable()?Result.ROOT_WAIT:Result.READY;
    }
}
