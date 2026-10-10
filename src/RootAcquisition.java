package com.e02.rootconsole;

import java.io.IOException;

/** Permission-aware acquisition. Drivers perform only this application's fixed operations. */
final class RootAcquisition {
    enum Mode { OWNED, LEGACY, CONSENT_REQUIRED, UNAVAILABLE, CANCELLED }
    static final class Result {
        final Mode mode;final String message;final RootIdentity identity;
        Result(Mode mode,String message,RootIdentity identity) { this.mode=mode;this.message=message;this.identity=identity; }
        boolean available() { return mode==Mode.OWNED||mode==Mode.LEGACY; }
    }
    interface Driver {
        boolean current();
        boolean automaticAllowed();
        RootPermit.Ticket permit() throws IOException;
        boolean permissionFileExists();
        RootIdentity identity(RootPermit.Ticket ticket) throws IOException;
        boolean legacyAuthorized();
        ReadOnlyAdbProbe.Result backend();
        RootPermit.Ticket grant() throws IOException;
        boolean migrateLegacy(RootPermit.Ticket ticket);
        RootBootstrap.Result launch(RootPermit.Ticket ticket);
        void cancel(RootPermit.Ticket ticket);
    }
    static Result check(Driver driver,boolean explicitMigration) {
        RootPermit.Ticket ticket=null;boolean started=false,allocated=false;
        try {
            if(!driver.current())return cancelled();
            ticket=driver.permit();
            if(ticket!=null) {
                try {
                    RootIdentity identity=driver.identity(ticket);
                    if(!driver.current()||!RootPermit.matches(ticket))return cancelled();
                    if(identity.uid==0&&identity.pid>0&&ticket.nonce.equals(identity.generation))
                        return new Result(Mode.OWNED,"星匣独立 Root 授权可用",identity);
                } catch(IOException missing) { /* A backend UID is not an owned service identity. */ }
            }
            if(!driver.current())return cancelled();
            // A revoked/failed lease must not silently reuse an old service.
            boolean legacy=(!driver.permissionFileExists()||explicitMigration)&&driver.legacyAuthorized();
            if(!driver.current())return cancelled();
            if(legacy&&!explicitMigration)return new Result(Mode.LEGACY,"当前使用旧版授权，启用独立授权可切换到星匣自己的服务",null);
            ReadOnlyAdbProbe.Result backend=driver.backend();
            if(!driver.current())return cancelled();
            if(backend.status!=ReadOnlyAdbProbe.Status.ROOT_READY||backend.uid!=0)return new Result(Mode.UNAVAILABLE,RootBootstrap.explain(backend.status),null);
            if(!driver.automaticAllowed())return new Result(Mode.CONSENT_REQUIRED,"本机 Root ADB 可用，请确认启用星匣独立授权",null);
            if(ticket==null){ticket=driver.grant();allocated=ticket!=null;}
            if(ticket==null||!driver.current()) { if(ticket!=null)driver.cancel(ticket);return cancelled(); }
            if(legacy&&!driver.migrateLegacy(ticket)){driver.cancel(ticket);return new Result(Mode.UNAVAILABLE,"旧版授权未能停止，请稍后重试",null);}
            if(!driver.current()){driver.cancel(ticket);return cancelled();}
            started=true;
            RootBootstrap.Result launch=driver.launch(ticket);
            if(!driver.current()||!RootPermit.matches(ticket)) { driver.cancel(ticket);return cancelled(); }
            if(!launch.ready||launch.identity==null){driver.cancel(ticket);return new Result(Mode.UNAVAILABLE,launch.message,null);}
            RootIdentity identity=launch.identity;
            if(identity.uid!=0||identity.pid<=0||!ticket.nonce.equals(identity.generation)) {
                driver.cancel(ticket);return new Result(Mode.UNAVAILABLE,"星匣授权服务身份校验失败",null);
            }
            return new Result(Mode.OWNED,"星匣独立 Root 授权可用",identity);
        } catch(IOException|RuntimeException e) {
            if((started||allocated)&&ticket!=null)driver.cancel(ticket);
            return new Result(Mode.UNAVAILABLE,"无法准备星匣授权，请检查存储或重新授权",null);
        }
    }
    private static Result cancelled() { return new Result(Mode.CANCELLED,"Root 授权已结束",null); }
}
