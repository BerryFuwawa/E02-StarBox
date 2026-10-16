package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Transaction journal: never forget a pending factory-setting restoration. */
final class ParkingMode {
    enum Phase { NORMAL, PREPARING, ONLINE, RESTORING }
    static final class State {
        final Phase phase;final int originalRaw,originalValue,appliedValue;
        State(Phase phase,int raw,int original,int applied){this.phase=phase;originalRaw=raw;originalValue=original;appliedValue=applied;}
        static State normal(){return new State(Phase.NORMAL,0,0,0);}
    }
    static State read(File directory)throws IOException {
        File file=new File(directory,"parking-mode-v1");if(!file.exists())return State.normal();
        if(file.length()>180)throw new IOException("Invalid parking mode");
        String[] parts=new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII).trim().split(":",-1);
        try{if(parts.length!=5||!"E02-PARKING-1".equals(parts[0]))throw new IllegalArgumentException();return new State(Phase.valueOf(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]),Integer.parseInt(parts[4]));}
        catch(IllegalArgumentException invalid){throw new IOException("Invalid parking mode",invalid);}
    }
    static boolean write(File directory,State state) {
        Path temporary=null;
        try {
            temporary=Files.createTempFile(directory.toPath(),"parking-mode-",".tmp");
            try(FileOutputStream stream=new FileOutputStream(temporary.toFile())) {
                stream.write(("E02-PARKING-1:"+state.phase+":"+state.originalRaw+":"+state.originalValue+":"+state.appliedValue+"\n").getBytes(StandardCharsets.US_ASCII));stream.getFD().sync();
            }
            Files.move(temporary,new File(directory,"parking-mode-v1").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return true;
        }catch(IOException|RuntimeException error){return false;}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
    static boolean selected(File directory)throws IOException{return read(directory).phase!=Phase.NORMAL;}
    static boolean restoreNeeded(State saved,int currentValue){return saved.phase!=Phase.NORMAL&&saved.originalValue!=saved.appliedValue&&currentValue==saved.appliedValue;}
    static boolean changedByOther(State saved,int currentValue){return saved.phase!=Phase.NORMAL&&currentValue!=saved.appliedValue&&currentValue!=saved.originalValue;}
}
