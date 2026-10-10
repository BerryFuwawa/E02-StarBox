package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Separate opt-in: old parkedRemote preferences never silently enable recovery. */
final class WakeRecoveryOptions {
    // Kept behind a release gate until natural sleep/wake has been verified.
    static boolean available(){return false;}
    static boolean enabled(File directory)throws IOException{return available()&&read(directory);}
    private static final String HEADER="E02-WAKE-RECOVERY-1:";
    static boolean read(File directory)throws IOException {
        File file=new File(directory,"wake-recovery");
        if(!file.exists())return false;
        byte[] bytes=new byte[64];int count;
        try(DataInputStream stream=new DataInputStream(new FileInputStream(file))){count=stream.read(bytes);if(count<0||stream.read()!=-1)throw new IOException("Invalid wake recovery option");}
        String value=new String(bytes,0,count,StandardCharsets.US_ASCII);
        if(value.equals(HEADER+"1\n"))return true;
        if(value.equals(HEADER+"0\n"))return false;
        throw new IOException("Invalid wake recovery option");
    }
    static boolean write(File directory,boolean enabled) {
        Path temporary=null;
        try {
            temporary=Files.createTempFile(directory.toPath(),"wake-option-",".tmp");
            try(FileOutputStream stream=new FileOutputStream(temporary.toFile())) {
                stream.write((HEADER+(enabled?"1":"0")+"\n").getBytes(StandardCharsets.US_ASCII));stream.flush();stream.getFD().sync();
            }
            Files.move(temporary,new File(directory,"wake-recovery").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            return true;
        }catch(IOException|RuntimeException error){return false;}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
    static boolean mayRestore(RuntimePolicy.Snapshot state,boolean optedIn) {
        return state.accepted&&!state.exited&&state.rootAllowed&&(state.autoStart||optedIn);
    }
}
