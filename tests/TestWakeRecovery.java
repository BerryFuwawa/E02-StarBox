package com.e02.rootconsole;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Production policy and actual private option storage; Android delivery is not simulated. */
public final class TestWakeRecovery {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("wake check "+checks);}
    private static final class Driver implements WakeRestoreController.Driver {
        boolean allowed=true,fail,stopAfterDelay;int requests,successAt=1;final List<Integer> delays=new ArrayList<>();
        public boolean allowed(){return allowed;}
        public boolean request()throws Exception{requests++;if(fail)throw new IOException("fixture failure");return requests>=successAt;}
        public void delay(int seconds){delays.add(seconds);if(stopAfterDelay)allowed=false;}
    }
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("starbox-wake-");
        try{
            File home=directory.toFile();LifecycleFileStore store=new LifecycleFileStore(home);
            check(!WakeRecoveryOptions.read(home));
            RuntimePolicy.Snapshot old=new RuntimePolicy.Snapshot(true,false,false,true,false,true,5);
            check(store.commit(old)&&!WakeRecoveryOptions.read(home)); // dormant legacy opt-in is not reused
            byte[] legacy=Files.readAllBytes(store.file().toPath());
            check(WakeRecoveryOptions.write(home,true)&&WakeRecoveryOptions.read(home));
            check(!WakeRecoveryOptions.available()&&!WakeRecoveryOptions.enabled(home)); // hidden legacy opt-in remains inert in stable release
            check(Arrays.equals(legacy,Files.readAllBytes(store.file().toPath()))); // keeps old package compatibility
            check(WakeRecoveryOptions.write(home,false)&&!WakeRecoveryOptions.read(home));
            Files.write(directory.resolve("wake-recovery"),new byte[65]);
            try{WakeRecoveryOptions.read(home);throw new AssertionError("corrupt opt-in accepted");}catch(IOException expected){checks++;}
            check(WakeRecoveryOptions.write(home,true)&&WakeRecoveryOptions.read(home));
            for(int bits=0;bits<64;bits++){
                boolean accepted=(bits&1)!=0,exited=(bits&2)!=0,root=(bits&4)!=0,boot=(bits&8)!=0,wake=(bits&16)!=0,keep=(bits&32)!=0;
                RuntimePolicy.Snapshot state=new RuntimePolicy.Snapshot(accepted,keep,boot,true,exited,root,8);
                check(WakeRecoveryOptions.mayRestore(state,wake)==(accepted&&!exited&&root&&(boot||wake)));
                check(RecoveryPolicy.start(state,true,true,RecoveryPolicy.Event.WAKE,wake)==(accepted&&!exited&&(boot||wake)));
                check(RecoveryPolicy.background(state,wake)==(keep||boot||wake));
                check(RecoveryPolicy.restoreChannels(state,true,true,wake)==(accepted&&!exited&&(boot||keep||wake)));
                check(ExitPolicy.blocked(keep,boot,true,wake)==(keep||wake));
                if(wake)check(ExitPolicy.message(keep,boot,true,wake).contains("熄火后远程连接保活"));
                check(!RecoveryPolicy.start(state,false,true,RecoveryPolicy.Event.WAKE,wake));
                check(!RecoveryPolicy.start(state,true,false,RecoveryPolicy.Event.WAKE,wake));
            }
            RuntimePolicy policy=new RuntimePolicy(store);check(policy.openByUser(true).has(RuntimePolicy.Effect.CHECK_IDENTITY));
            check(policy.bootOrWake(true).has(RuntimePolicy.Effect.CHECK_IDENTITY));
            check(policy.restoreExistingService(true).has(RuntimePolicy.Effect.CHECK_IDENTITY));
            check(policy.exitByUser(true).has(RuntimePolicy.Effect.EXIT_BLOCKED)&&!policy.snapshot().exited);
            check(policy.exitByUser(false).has(RuntimePolicy.Effect.STOP_ALL_OWNED));
            check(policy.bootOrWake(true).has(RuntimePolicy.Effect.NONE)); // explicit exit is never cleared by a wake
            check(policy.restoreExistingService(true).has(RuntimePolicy.Effect.NONE));
            check(policy.coldStart().has(RuntimePolicy.Effect.NONE)); // no autoStart means no boot reopening
            check(policy.openByUser(true).has(RuntimePolicy.Effect.CHECK_IDENTITY));
            check(policy.configure(false,true,true).has(RuntimePolicy.Effect.CANCEL_RETRIES));
            check(policy.exitByUser(false).has(RuntimePolicy.Effect.STOP_ALL_OWNED));
            check(policy.coldStart().has(RuntimePolicy.Effect.CHECK_IDENTITY)&&!policy.snapshot().exited);
            Driver good=new Driver();WakeRestoreController controller=new WakeRestoreController();long ticket=controller.begin();
            check(ticket>0&&controller.begin()==-1);check(controller.run(ticket,good)&&good.requests==1&&good.delays.isEmpty());
            Driver retries=new Driver();retries.successAt=4;ticket=controller.begin();check(controller.run(ticket,retries));check(retries.requests==4&&retries.delays.equals(Arrays.asList(2,5,15)));
            Driver failures=new Driver();failures.fail=true;ticket=controller.begin();check(!controller.run(ticket,failures)&&failures.requests==4);check(controller.begin()>ticket);
            controller=new WakeRestoreController();Driver cancelled=new Driver();cancelled.successAt=4;cancelled.stopAfterDelay=true;ticket=controller.begin();check(!controller.run(ticket,cancelled)&&cancelled.requests==1);
            controller=new WakeRestoreController();Driver denied=new Driver();denied.allowed=false;ticket=controller.begin();check(!controller.run(ticket,denied)&&denied.requests==0);
            controller=new WakeRestoreController();ticket=controller.begin();controller.close();Driver closed=new Driver();check(!controller.run(ticket,closed)&&closed.requests==0&&controller.begin()==-1);
            System.out.println("PASS "+checks+" wake recovery checks: legacy opt-in isolation, actual atomic storage, corrupt state, 64 permission combinations, explicit exit, real boot distinction, bounded retries, duplicate events and cancellation. No vehicle event delivery or sleep claim.");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(directory)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
    }
}
