package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** App writes an acknowledgement only after validating the current wake request. */
final class WakeReceipt {
    static boolean acknowledge(File directory,String generation,String request) {
        if(!RootPermit.nonce(generation)||!RootPermit.nonce(request))return false;
        Path temporary=null;
        try {
            temporary=Files.createTempFile(directory.toPath(),"wake-receipt-",".tmp");
            try(FileOutputStream stream=new FileOutputStream(temporary.toFile())) {
                stream.write((generation+":"+request+"\n").getBytes(StandardCharsets.US_ASCII));stream.getFD().sync();
            }
            Files.move(temporary,new File(directory,"wake-receipt").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return true;
        }catch(IOException|RuntimeException error){return false;}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
    static boolean matches(File directory,String generation,String request) {
        if(!RootPermit.nonce(generation)||!RootPermit.nonce(request))return false;
        File file=new File(directory,"wake-receipt");
        if(!file.isFile()||file.length()!=66)return false;
        try{return (generation+":"+request+"\n").equals(new String(Files.readAllBytes(file.toPath()),StandardCharsets.US_ASCII));}
        catch(IOException|RuntimeException unavailable){return false;}
    }
}
