package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;

/** App-owned launch permission. Revocation also invalidates already-dispatched launches. */
final class RootPermit {
    private static final String HEADER="E02-ROOT-1\n";
    private static final String STOPPED="STOPPED";
    private static final String USER_ENDED="STOPPED-USER";
    static final class Ticket {
        final File file;
        final String nonce;
        Ticket(File file,String nonce) { this.file=file;this.nonce=nonce; }
    }
    static File file(File directory) { return new File(directory,"root-permit"); }
    static boolean nonce(String value) { return value!=null&&value.matches("[0-9a-f]{32}"); }
    static synchronized Ticket active(File directory)throws IOException {
        String state=read(directory);
        if(state==null||STOPPED.equals(state)||USER_ENDED.equals(state))return null;
        if(!nonce(state))throw new IOException("Invalid launch permission");
        return new Ticket(file(directory),state);
    }
    static synchronized boolean userEnded(File directory)throws IOException { return USER_ENDED.equals(read(directory)); }
    private static String read(File directory)throws IOException {
        File file=file(directory);
        if(!file.exists())return null;
        byte[] bytes=new byte[128];int size=0;
        try(InputStream in=new FileInputStream(file)) {
            int n;while(size<bytes.length&&(n=in.read(bytes,size,bytes.length-size))>0)size+=n;
            if(size==bytes.length||in.read()!=-1)throw new IOException("Invalid launch permission");
        }
        String value=new String(bytes,0,size,StandardCharsets.US_ASCII);
        if(!value.startsWith(HEADER)||!value.endsWith("\n"))throw new IOException("Invalid launch permission");
        String state=value.substring(HEADER.length(),value.length()-1);
        if(!STOPPED.equals(state)&&!USER_ENDED.equals(state)&&!nonce(state))throw new IOException("Invalid launch permission");
        return state;
    }
    /** Call only after the user's Root request or a previously approved recovery policy. */
    static synchronized Ticket grant(File directory)throws IOException {
        byte[] random=new byte[16];new SecureRandom().nextBytes(random);
        StringBuilder value=new StringBuilder();for(byte b:random)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        write(directory,value.toString());return new Ticket(file(directory),value.toString());
    }
    static synchronized void revoke(File directory)throws IOException { write(directory,STOPPED); }
    static synchronized void revokeByUser(File directory)throws IOException { write(directory,USER_ENDED); }
    static synchronized boolean revokeIfCurrent(Ticket ticket)throws IOException {
        Ticket current=active(ticket.file.getParentFile());
        if(current==null||!current.nonce.equals(ticket.nonce))return false;
        write(ticket.file.getParentFile(),STOPPED);return true;
    }
    static boolean matches(Ticket ticket) {
        if(ticket==null)return false;
        try { Ticket current=active(ticket.file.getParentFile());return current!=null&&current.nonce.equals(ticket.nonce); }
        catch(IOException e) { return false; }
    }
    private static void write(File directory,String value)throws IOException {
        if(!directory.isDirectory())throw new IOException("App storage unavailable");
        Path temporary=Files.createTempFile(directory.toPath(),"root-permit-",".tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(temporary.toFile())) {
                out.write((HEADER+value+"\n").getBytes(StandardCharsets.US_ASCII));out.flush();out.getFD().sync();
            }
            // Do not silently fall back to a non-atomic replacement.
            Files.move(temporary,file(directory).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
