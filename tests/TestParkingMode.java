package com.e02.rootconsole;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;

public final class TestParkingMode {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("starbox-parking-");File home=directory.toFile();
        try {
            check(ParkingMode.read(home).phase==ParkingMode.Phase.NORMAL);
            Files.write(directory.resolve("wake-recovery"),"E02-WAKE-RECOVERY-1:1\n".getBytes("US-ASCII"));
            check(!WakeRecoveryOptions.enabled(home)); // old hidden setting cannot opt into new power mode
            ParkingMode.State before=new ParkingMode.State(ParkingMode.Phase.PREPARING,0,0,538640659);
            check(ParkingMode.write(home,before));check(ParkingMode.read(home).phase==ParkingMode.Phase.PREPARING);
            check(ParkingMode.restoreNeeded(before,538640659));check(!ParkingMode.restoreNeeded(before,0));
            check(ParkingMode.changedByOther(before,538640642));check(!ParkingMode.changedByOther(before,538640659));check(!ParkingMode.changedByOther(before,0));
            // Process death between applying power and saving ONLINE retains the original setting.
            ParkingMode.State afterDeath=ParkingMode.read(home);check(afterDeath.originalValue==0&&afterDeath.appliedValue==538640659);
            check(ParkingMode.write(home,new ParkingMode.State(ParkingMode.Phase.ONLINE,0,0,538640659)));
            check(ParkingMode.selected(home));
            check(ParkingMode.write(home,new ParkingMode.State(ParkingMode.Phase.RESTORING,0,0,538640659)));
            check(ParkingMode.read(home).phase==ParkingMode.Phase.RESTORING);
            check(ParkingMode.write(home,ParkingMode.State.normal())&&!ParkingMode.selected(home));
            ParkingMode.State alreadyAlways=new ParkingMode.State(ParkingMode.Phase.ONLINE,19,538640659,538640659);
            check(!ParkingMode.restoreNeeded(alreadyAlways,538640659));
            Files.write(directory.resolve("parking-mode-v1"),new byte[181]);
            try{ParkingMode.read(home);throw new AssertionError("corrupt mode accepted");}catch(IOException expected){checks++;}
            String generation="0123456789abcdef0123456789abcdef",request="abcdef0123456789abcdef0123456789";
            check(!WakeReceipt.matches(home,generation,request));check(WakeReceipt.acknowledge(home,generation,request));check(WakeReceipt.matches(home,generation,request));
            check(!WakeReceipt.matches(home,request,generation));check(!WakeReceipt.matches(home,generation,generation));
            check(!WakeReceipt.acknowledge(home,generation,"invalid"));check(WakeReceipt.matches(home,generation,request));
            check(WakeReceipt.acknowledge(home,generation,generation));check(!WakeReceipt.matches(home,generation,request));
            check(WakeReceipt.matches(home,generation,generation));
            Files.write(directory.resolve("wake-receipt"),new byte[100]);check(!WakeReceipt.matches(home,generation,generation));
            System.out.println("PASS "+checks+" parking journal and wake acknowledgement checks: original settings retained across interrupted transactions, conflicts, legacy isolation, stale request/generation rejection. No vehicle power or event claim.");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(directory)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
    }
}
