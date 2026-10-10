package com.e02.rootconsole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Real file replacement/reload tests, with the same policy used by the Android UI adapter. */
public final class TestLifecycleFileStore {
    private static int checks;
    private static void check(boolean value) { checks++;if(!value)throw new AssertionError("Check "+checks); }
    private static void write(File file,String value)throws IOException { Files.write(file.toPath(),value.getBytes(StandardCharsets.US_ASCII)); }
    public static void main(String[] arguments)throws Exception {
        Path directory=Files.createTempDirectory("starbox-exit-state-");
        try {
            LifecycleFileStore store=new LifecycleFileStore(directory.toFile());
            RuntimePolicy initial=new RuntimePolicy(store);
            check(initial.saveHealthy());check(!initial.snapshot().accepted&&initial.snapshot().exited);
            check(!initial.snapshot().keepAlive&&!initial.snapshot().autoStart&&!initial.snapshot().parkedRemote);
            check(initial.bootOrWake().has(RuntimePolicy.Effect.NONE));
            for(int mask=0;mask<8;mask++) {
                boolean keep=(mask&1)!=0,boot=(mask&2)!=0,park=(mask&4)!=0;
                RuntimePolicy current=new RuntimePolicy(store);
                check(!current.openByUser(true).has(RuntimePolicy.Effect.SAVE_ERROR));
                check(!current.configure(keep,boot,park).has(RuntimePolicy.Effect.SAVE_ERROR));
                RuntimePolicy restored=new RuntimePolicy(new LifecycleFileStore(directory.toFile()));
                RuntimePolicy.Snapshot actual=restored.snapshot();
                check(restored.saveHealthy()&&actual.keepAlive==keep&&actual.autoStart==boot&&actual.parkedRemote==park);
                String before=new String(Files.readAllBytes(store.file().toPath()),StandardCharsets.US_ASCII);
                long revision=actual.revision,ticket=restored.ticket();
                RuntimePolicy.Decision decision=restored.exitByUser();
                check(decision.has(keep?RuntimePolicy.Effect.EXIT_BLOCKED:RuntimePolicy.Effect.STOP_ALL_OWNED));
                if(keep) {
                    check(!decision.has(RuntimePolicy.Effect.STOP_ALL_OWNED)&&!decision.has(RuntimePolicy.Effect.STOP_OWNED_ROOT));
                    check(restored.ticket()==ticket&&restored.snapshot().revision==revision&&!restored.snapshot().exited);
                    check(before.equals(new String(Files.readAllBytes(store.file().toPath()),StandardCharsets.US_ASCII)));
                    String message=ExitPolicy.message(keep,boot,park);
                    check(message.contains("• 星匣后台保活")==keep);
                    check(!message.contains("• 开机自启"));
                    check(!message.contains("• 熄火后远程连接保活"));
                    check(!message.contains("继续退出"));
                }
                check(!restored.configure(false,false,false).has(RuntimePolicy.Effect.SAVE_ERROR));
                check(restored.exitByUser().has(RuntimePolicy.Effect.STOP_ALL_OWNED));
                RuntimePolicy stopped=new RuntimePolicy(new LifecycleFileStore(directory.toFile()));
                check(stopped.saveHealthy()&&stopped.snapshot().exited);
                check(stopped.bootOrWake().has(RuntimePolicy.Effect.NONE));
                check(stopped.unexpectedServiceStop().has(RuntimePolicy.Effect.NONE));
            }
            String saved=new String(Files.readAllBytes(store.file().toPath()),StandardCharsets.US_ASCII);
            check(!store.commit(null));
            check(saved.equals(new String(Files.readAllBytes(store.file().toPath()),StandardCharsets.US_ASCII)));
            LifecycleFileStore missing=new LifecycleFileStore(directory.resolve("missing").toFile());
            check(!missing.commit(store.load()));check(!directory.resolve("missing").toFile().exists());
            String[] invalid={"", "E02-LIFECYCLE-1:0:11111x", "E02-LIFECYCLE-2:0:111111", "E02-LIFECYCLE-1:9223372036854775807:111111", "E02-LIFECYCLE-1:0:111111\n",new String(new char[161]).replace('\0','x')};
            for(String value:invalid) {
                write(store.file(),value);
                try {store.load();throw new AssertionError("Corruption accepted");}catch(IOException expected){checks++;}
                RuntimePolicy corrupt=new RuntimePolicy(store);
                check(!corrupt.saveHealthy());check(corrupt.bootOrWake().has(RuntimePolicy.Effect.NONE));
            }
            try(java.util.stream.Stream<Path> files=Files.list(directory)) {
                check(files.noneMatch(path->path.getFileName().toString().endsWith(".tmp")));
            }
            System.out.println("PASS "+checks+" checks: real atomic store/reload, all eight option combinations, blocked click unchanged, dynamic enabled list, full-off exit gate, corrupt/failed storage");
        } finally {
            try(java.util.stream.Stream<Path> files=Files.walk(directory)) {
                for(Path path:(Iterable<Path>)files.sorted(java.util.Comparator.reverseOrder())::iterator)Files.delete(path);
            }
        }
    }
}
