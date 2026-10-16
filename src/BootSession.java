package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Records only the kernel boot identity, without secrets, settings or shell commands. */
final class BootSession {
    static boolean valid(String id){return id!=null&&id.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");}
    static String current(){try{return read(new File("/proc/sys/kernel/random/boot_id"));}catch(IOException|RuntimeException failed){return "";}}
    static String previous(File directory){try{return read(new File(directory,"service-boot-id"));}catch(IOException|RuntimeException failed){return "";}}
    private static String read(File file)throws IOException {
        if(!file.isFile())return "";
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(file)){int value;while((value=in.read())!=-1){if(out.size()>=40)return "";out.write(value);}}
        String id=new String(out.toByteArray(),StandardCharsets.US_ASCII).trim();return valid(id)?id:"";
    }
    static synchronized boolean remember(File directory,String id){
        if(!valid(id))return false;if(id.equals(previous(directory)))return true;Path temp=null;
        try{temp=Files.createTempFile(directory.toPath(),"boot-session-",".tmp");
            try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(id.getBytes(StandardCharsets.US_ASCII));out.getFD().sync();}
            Files.move(temp,new File(directory,"service-boot-id").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return true;
        }catch(IOException|RuntimeException failed){return false;}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
}
