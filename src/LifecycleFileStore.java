package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** One app-owned snapshot: the three options and exit gate are committed together. */
final class LifecycleFileStore implements RuntimePolicy.Store {
    private final File directory;
    LifecycleFileStore(File directory) { this.directory=directory; }
    File file() { return new File(directory,"lifecycle-state"); }
    @Override public synchronized RuntimePolicy.Snapshot load() throws IOException {
        if(!file().exists())return new RuntimePolicy.Snapshot(false,false,false,false,true,false,0);
        byte[] bytes=new byte[160];int size=0;
        try(InputStream input=new FileInputStream(file())) {
            int count;
            while(size<bytes.length&&(count=input.read(bytes,size,bytes.length-size))>0)size+=count;
            if(size==bytes.length||input.read()!=-1)throw new IOException("Invalid lifecycle state");
        }
        try { return RuntimePolicy.Snapshot.decode(new String(bytes,0,size,StandardCharsets.US_ASCII)); }
        catch(IllegalArgumentException e) { throw new IOException("Invalid lifecycle state",e); }
    }
    @Override public synchronized boolean commit(RuntimePolicy.Snapshot next) {
        Path temporary=null;
        try {
            if(!directory.isDirectory())return false;
            temporary=Files.createTempFile(directory.toPath(),"lifecycle-",".tmp");
            try(FileOutputStream output=new FileOutputStream(temporary.toFile())) {
                output.write(next.encode().getBytes(StandardCharsets.US_ASCII));
                output.flush();output.getFD().sync();
            }
            Files.move(temporary,file().toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch(IOException|RuntimeException e) { return false; }
        finally { if(temporary!=null)try { Files.deleteIfExists(temporary); }catch(IOException ignored) {} }
    }
}
