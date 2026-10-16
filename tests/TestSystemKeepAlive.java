package com.e02.rootconsole;

import java.nio.file.*;

public final class TestSystemKeepAlive {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args)throws Exception {
        for(int bits=0;bits<256;bits++){
            boolean keep=(bits&1)!=0,boot=(bits&2)!=0,exited=(bits&4)!=0,consent=(bits&8)!=0,
                healthy=(bits&16)!=0,request=(bits&32)!=0,known=(bits&64)!=0,fresh=(bits&128)!=0;
            RuntimePolicy.Snapshot state=new RuntimePolicy.Snapshot(consent,keep,boot,false,exited,false,0);
            SystemKeepAliveGate.Decision result=SystemKeepAliveGate.decide(state,consent,healthy,request,known,fresh);
            boolean shouldStart=keep&&consent&&healthy&&request&&known&&(fresh?boot:!exited);
            check((result!=SystemKeepAliveGate.Decision.SKIP)==shouldStart);
            if(shouldStart)check((result==SystemKeepAliveGate.Decision.COLD_START)==fresh);
            check(!state.rootAllowed); // Binding never grants or changes authorization.
            check(SystemKeepAliveGate.decide(state,false,healthy,request,known,fresh)==SystemKeepAliveGate.Decision.SKIP);
        }
        Path dir=Files.createTempDirectory("e02-boot-session-");
        String first="2b615682-ae3c-4179-ab03-cdc62af8c861",second="4d0cc252-28ba-4060-89b4-c8ac717e473f";
        try{
            check(BootSession.previous(dir.toFile()).isEmpty());
            check(BootSession.remember(dir.toFile(),first));check(first.equals(BootSession.previous(dir.toFile())));
            check(BootSession.remember(dir.toFile(),first));
            check(!BootSession.remember(dir.toFile(),"not-a-boot"));check(first.equals(BootSession.previous(dir.toFile())));
            check(BootSession.remember(dir.toFile(),second));check(second.equals(BootSession.previous(dir.toFile())));
            Files.write(dir.resolve("service-boot-id"),new byte[100]);check(BootSession.previous(dir.toFile()).isEmpty());
            check(!BootSession.remember(dir.resolve("missing").toFile(),first));
            try(java.util.stream.Stream<Path> files=Files.list(dir)){check(files.noneMatch(p->p.toString().endsWith(".tmp")));}
        }finally{try(java.util.stream.Stream<Path> files=Files.list(dir)){for(Path p:(Iterable<Path>)files::iterator)Files.delete(p);}Files.delete(dir);}
        System.out.println("PASS "+checks+" system-binding policy and persisted boot identity checks. No real OS binding or vehicle startup tested.");
    }
}
