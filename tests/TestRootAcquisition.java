package com.e02.rootconsole;

import java.io.*;
import java.nio.file.*;

/** Executes production acquisition decisions with a fixture driver, never real privilege acquisition. */
public final class TestRootAcquisition {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Fixture implements RootAcquisition.Driver {
        final File directory;
        boolean current=true,automatic,legacy,existing,wrongIdentity,wrongLaunch,failedLaunch,failedMigration;
        boolean cancelAtGrant,cancelAtLaunch,newerAtLaunch,failPermit,newerAtIdentity,throwMigration;
        ReadOnlyAdbProbe.Status backend=ReadOnlyAdbProbe.Status.ROOT_READY;
        int uid=0,grants,launches,cancels,migrations,probes,legacyReads;
        RootPermit.Ticket newer;
        Fixture(File directory){this.directory=directory;}
        public boolean current(){return current;}
        public boolean automaticAllowed(){try{return automatic&&!RootPermit.userEnded(directory);}catch(IOException e){return false;}}
        public RootPermit.Ticket permit()throws IOException{if(failPermit)throw new IOException("fixture storage error");return RootPermit.active(directory);}
        public boolean permissionFileExists(){return RootPermit.file(directory).exists();}
        public RootIdentity identity(RootPermit.Ticket lease)throws IOException{if(!existing)throw new IOException("fixture absent");if(newerAtIdentity)newer=RootPermit.grant(directory);return new RootIdentity(wrongIdentity?2000:0,111,lease.nonce);}
        public boolean legacyAuthorized(){legacyReads++;return legacy;}
        public ReadOnlyAdbProbe.Result backend(){probes++;return new ReadOnlyAdbProbe.Result(backend,uid,"fixture");}
        public RootPermit.Ticket grant()throws IOException{grants++;RootPermit.Ticket lease=RootPermit.grant(directory);if(cancelAtGrant)current=false;return lease;}
        public boolean migrateLegacy(RootPermit.Ticket lease){migrations++;if(throwMigration)throw new IllegalStateException("fixture failure");return !failedMigration;}
        public RootBootstrap.Result launch(RootPermit.Ticket lease){launches++;if(cancelAtLaunch)current=false;if(newerAtLaunch)try{newer=RootPermit.grant(directory);current=false;}catch(IOException e){throw new IllegalStateException(e);}return new RootBootstrap.Result(!failedLaunch,"fixture startup",failedLaunch?null:new RootIdentity(wrongLaunch?2000:0,222,lease.nonce));}
        public void cancel(RootPermit.Ticket lease){cancels++;try{RootPermit.revokeIfCurrent(lease);}catch(IOException e){throw new IllegalStateException(e);}}
    }
    static Fixture fresh(File directory)throws IOException{Files.deleteIfExists(RootPermit.file(directory).toPath());return new Fixture(directory);}
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("starbox-acquire-");
        try {
            Fixture f=fresh(directory.toFile());RootAcquisition.Result result=RootAcquisition.check(f,false);
            check(result.mode==RootAcquisition.Mode.CONSENT_REQUIRED&&!result.available(),"backend UID0 alone is not available authorization");
            check(f.probes==1&&f.grants==0&&f.launches==0&&f.migrations==0,"first inspection has no write/elevation");
            for(ReadOnlyAdbProbe.Status status:ReadOnlyAdbProbe.Status.values())if(status!=ReadOnlyAdbProbe.Status.ROOT_READY){
                f=fresh(directory.toFile());f.automatic=true;f.backend=status;f.uid=status==ReadOnlyAdbProbe.Status.SHELL_ONLY?2000:-1;
                result=RootAcquisition.check(f,false);
                check(!result.available()&&result.mode==RootAcquisition.Mode.UNAVAILABLE,"non-root backend: "+status);
                check(f.launches==0&&f.grants==0&&f.migrations==0,"no first Root request for "+status);
            }
            f=fresh(directory.toFile());f.automatic=true;f.uid=2000;
            check(!RootAcquisition.check(f,false).available()&&f.launches==0,"inconsistent backend fixture cannot launch");
            f=fresh(directory.toFile());f.existing=true;RootPermit.Ticket active=RootPermit.grant(directory.toFile());
            result=RootAcquisition.check(f,false);
            check(result.mode==RootAcquisition.Mode.OWNED&&result.identity.pid==111,"reuse only actual owned identity");
            check(f.probes==0&&f.launches==0&&f.grants==0,"owned reuse needs no backend/root restart");
            f=fresh(directory.toFile());f.existing=true;f.newerAtIdentity=true;RootPermit.grant(directory.toFile());
            check(RootAcquisition.check(f,false).mode==RootAcquisition.Mode.CANCELLED,"owned identity from a replaced lease cannot publish");
            check(RootPermit.matches(f.newer),"identity race never revokes a newer approval");
            f=fresh(directory.toFile());f.existing=true;f.wrongIdentity=true;RootPermit.grant(directory.toFile());
            result=RootAcquisition.check(f,false);
            check(!result.available()&&result.identity==null,"non-root service identity not accepted");
            f=fresh(directory.toFile());f.legacy=true;result=RootAcquisition.check(f,false);
            check(result.mode==RootAcquisition.Mode.LEGACY&&result.identity==null,"legacy compatible status not an owned HMAC identity");
            check(f.migrations==0&&f.launches==0&&f.grants==0&&f.probes==0,"silent detection does not migrate old service");
            f=fresh(directory.toFile());f.legacy=true;f.automatic=true;
            result=RootAcquisition.check(f,true);
            check(result.mode==RootAcquisition.Mode.OWNED&&result.identity.pid==222,"explicit approved migration starts owned service");
            check(f.grants==1&&f.migrations==1&&f.launches==1&&f.cancels==0,"single lease and launch");
            f=fresh(directory.toFile());f.legacy=true;
            check(RootAcquisition.check(f,true).mode==RootAcquisition.Mode.CONSENT_REQUIRED&&f.migrations==0,"migration also requires stored consent");
            f=fresh(directory.toFile());f.automatic=true;f.legacy=true;RootPermit.revokeByUser(directory.toFile());
            result=RootAcquisition.check(f,false);
            check(result.mode==RootAcquisition.Mode.CONSENT_REQUIRED&&!result.available(),"user end not bypassed by old consent or legacy socket");
            check(f.legacyReads==0&&f.grants==0&&f.launches==0&&RootPermit.userEnded(directory.toFile()),"explicit end marker survives inspection");
            f=fresh(directory.toFile());f.automatic=true;f.current=false;
            check(RootAcquisition.check(f,false).mode==RootAcquisition.Mode.CANCELLED&&f.probes==0&&f.grants==0,"closed runtime gate has no effects");
            f=fresh(directory.toFile());f.automatic=true;f.cancelAtGrant=true;
            check(RootAcquisition.check(f,false).mode==RootAcquisition.Mode.CANCELLED&&f.launches==0&&f.cancels==1,"revocation after grant stops before launch");
            check(RootPermit.active(directory.toFile())==null,"cancelled granted lease is revoked");
            f=fresh(directory.toFile());f.automatic=true;f.cancelAtLaunch=true;
            check(RootAcquisition.check(f,false).mode==RootAcquisition.Mode.CANCELLED&&f.cancels==1,"late launch result cannot publish authorization");
            check(RootPermit.active(directory.toFile())==null,"late launch loses its permission");
            f=fresh(directory.toFile());f.automatic=true;f.newerAtLaunch=true;
            check(RootAcquisition.check(f,false).mode==RootAcquisition.Mode.CANCELLED&&f.cancels==1,"late cleanup targets old generation");
            check(RootPermit.matches(f.newer),"new user approval survives stale cleanup");
            f=fresh(directory.toFile());f.automatic=true;f.failedLaunch=true;
            check(!RootAcquisition.check(f,false).available()&&f.cancels==1,"failed startup is cancelled");
            check(RootPermit.active(directory.toFile())==null,"failed startup leaves no active lease");
            f=fresh(directory.toFile());f.automatic=true;f.wrongLaunch=true;
            check(!RootAcquisition.check(f,false).available()&&f.cancels==1,"wrong launch identity not accepted");
            f=fresh(directory.toFile());f.automatic=true;f.legacy=true;f.failedMigration=true;
            check(!RootAcquisition.check(f,true).available()&&f.launches==0&&f.cancels==1,"migration failure does not overlap services");
            f=fresh(directory.toFile());f.automatic=true;f.legacy=true;f.throwMigration=true;
            check(!RootAcquisition.check(f,true).available()&&f.cancels==1&&f.launches==0,"migration exception cancels the allocated lease");
            check(RootPermit.active(directory.toFile())==null,"failed migration leaves no launch permission");
            f=fresh(directory.toFile());f.automatic=true;f.failPermit=true;
            check(!RootAcquisition.check(f,false).available()&&f.grants==0&&f.launches==0,"unreadable permission fails closed");
            System.out.println("PASS "+checks+" acquisition checks: first consent, backend vs service, reuse, legacy migration, explicit end, cancellation, stale generation, failure cleanup; driver UID/PID are fixtures");
        } finally {
            try(java.util.stream.Stream<Path> files=Files.list(directory)){for(Path path:(Iterable<Path>)files::iterator)Files.delete(path);}
            Files.delete(directory);
        }
    }
}
