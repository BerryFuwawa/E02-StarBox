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
        if(!driver.restoreWeb())return Result.WEB_WAIT;
        if(!driver.current())return Result.CANCELLED;
        if(!driver.restoreRemote())return Result.ADB_WAIT;
        if(!driver.current())return Result.CANCELLED;
        driver.maintainAdb();
        if(!driver.current())return Result.CANCELLED;
        return driver.rootNeeded()&&!driver.rootAvailable()?Result.ROOT_WAIT:Result.READY;
    }
}
