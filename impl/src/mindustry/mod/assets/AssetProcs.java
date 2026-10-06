package mindustry.mod.assets;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.mock.*;
import arc.struct.*;
import arc.struct.Queue;
import arc.util.*;
import arc.util.TaskQueue;
import arc.util.serialization.*;
import arc.util.serialization.Jval.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;

import java.util.*;
import java.util.concurrent.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class AssetProcs{
    private static final TaskQueue posts = new TaskQueue();
    public static LoadedMod mod;

    public static void main(String[] args){
        ArcNativesLoader.load();
        loadLogger();

        app = new MockApplication(){
            @Override
            public void post(Runnable runnable){
                posts.post(runnable);
            }
        };
        graphics = new MockGraphics();
        audio = new MockAudio();
        input = new MockInput();
        files = new MockFiles(){
            @Override
            public boolean isExternalStorageAvailable(){
                return false;
            }

            @Override
            public boolean isLocalStorageAvailable(){
                return false;
            }
        };
        settings = new Settings();

        // Shutdown the current thread executor.
        executor.shutdown();
        try{
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }catch(InterruptedException e){
            throw new RuntimeException(e);
        }

        CompletableFuture<Throwable> error = new CompletableFuture<>();
        executor = mainExecutor = new ThreadPoolExecutor(OS.cores, OS.cores, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), run -> {
            var thread = new Thread(run);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> error.complete(e));
            return thread;
        }){
            @Override
            protected void afterExecute(Runnable runnable, Throwable failure){
                super.afterExecute(runnable, failure);
                if(failure == null && runnable instanceof Future<?> future && future.isDone()){
                    try{
                        future.get();
                    }catch(CancellationException ignored){
                    }catch(ExecutionException e){
                        failure = e.getCause();
                    }catch(InterruptedException e){
                        Thread.currentThread().interrupt();
                        failure = e;
                    }
                }

                if(failure != null) error.complete(failure);
            }
        };

        headless = true;
        asyncCore = new AsyncCore();
        state = new GameState();
        mods = new Mods();

        content = new ContentLoader();
        content.createBaseContent();

        var processors = parseArgs((main, meta) -> {
            mod = new LoadedMod(null, null, main, AssetProcs.class.getClassLoader(), meta);
            Reflect.<Seq<LoadedMod>>get(Mods.class, mods, "mods").add(mod);
            Reflect.<ObjectMap<Class<?>, ModMeta>>get(Mods.class, mods, "metas").put(main.getClass(), meta);
        }, args);

        content.setCurrentMod(mod);
        try{
            mod.main.loadContent();
        }finally{
            content.setCurrentMod(null);
        }

        posts.run();
        for(var proc : processors) proc.run();

        executor.shutdown();
        try{
            do{
                posts.run();
            }while(!executor.awaitTermination(1, TimeUnit.MILLISECONDS));
            posts.run();
        }catch(InterruptedException e){
            executor.shutdownNow();
            throw new RuntimeException(e);
        }

        if(error.isDone()) throw new RuntimeException(error.join());
    }

    private static Seq<Runnable> parseArgs(Cons2<Mod, ModMeta> mod, String[] cmd){
        Queue<String> queue = new Queue<>();
        for(var arg : cmd) queue.addLast(arg);

        Func<String, String> next = err -> {
            try{
                return queue.removeFirst();
            }catch(NoSuchElementException e){
                throw new IllegalArgumentException(err);
            }
        };

        Func<String, String[]> nextPair = err -> {
            var n = next.get(err);
            int index = n.indexOf('=');
            if(index == -1) throw new IllegalArgumentException("Expected `<key>=<value>`: " + n);

            return new String[]{n.substring(0, index), n.substring(index + 1)};
        };

        var metaPath = next.get("Expected <mod meta file>");
        try{
            // Offload it to another thread to simulate `Vars.loadAsync()`.
            // Some mods expect the main class constructor to be called off the main thread.
            var future = executor.submit(() -> {
                try{
                    var meta = new Json().fromJson(ModMeta.class, Jval.read(Fi.get(metaPath).readString()).toString(Jformat.plain));
                    meta.cleanup();

                    if(meta.main == null) throw new ClassNotFoundException("`main` property not found in mod meta");

                    var instance = (Mod)Class.forName(meta.main).getConstructor().newInstance();
                    mod.get(instance, meta);
                }catch(Exception e){
                    throw new RuntimeException("Couldn't instantiate mod", e);
                }
            });

            while(!future.isDone()) posts.run();
            future.get(); // Ensure happens-before relationship and error propagation.
        }catch(Exception e){
            throw new RuntimeException(e);
        }

        Seq<Runnable> runs = new Seq<>();
        while(!queue.isEmpty()){
            var arg = queue.removeFirst();
            if(!arg.equals("-p")) throw new IllegalArgumentException("Expected `-p`");

            var procName = next.get("Expected <proc class name>");
            AssetProc proc;
            try{
                proc = (AssetProc)Class.forName(procName).getConstructor().newInstance();
            }catch(Exception e){
                throw new RuntimeException("Couldn't instantiate processor", e);
            }

            ObjectMap<String, Fi> inputs = new ObjectMap<>(), outputs = new ObjectMap<>();
            StringMap arguments = new StringMap();

            proc:
            while(!queue.isEmpty()){
                arg = queue.removeFirst();
                switch(arg){
                    case "-p" -> {
                        queue.addFirst("-p");
                        break proc;
                    }
                    case "-i" -> {
                        var pair = nextPair.get("Expected <key>=<input directory>");
                        inputs.put(pair[0], Fi.get(pair[1]));
                    }
                    case "-o" -> {
                        var pair = nextPair.get("Expected <key>=<output directory>");
                        outputs.put(pair[0], Fi.get(pair[1]));
                    }
                    case "-a" -> {
                        var pair = nextPair.get("Expected <key>=<value>");
                        arguments.put(pair[0], pair[1]);
                    }
                    default -> throw new IllegalArgumentException("Unexpected argument: " + arg);
                }
            }

            runs.add(() -> proc.process(inputs, outputs, arguments));
        }

        return runs;
    }
}
