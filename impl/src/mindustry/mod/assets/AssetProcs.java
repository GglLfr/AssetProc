package mindustry.mod.assets;

import arc.*;
import arc.assets.*;
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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class AssetProcs{
    private static final TaskQueue posts = new TaskQueue();
    private static ProcAtlas atlas;
    private static LoadedMod mod;

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
        assets = new AssetManager(tree = new FileTree());
        Core.atlas = atlas = new ProcAtlas();

        CompletableFuture<Throwable> error = new CompletableFuture<>();
        executor = Executors.newFixedThreadPool(OS.cores, run -> {
            var thread = new Thread(run);
            thread.setUncaughtExceptionHandler((t, e) -> error.complete(e));
            return thread;
        });

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
        mod.main.loadContent();
        content.setCurrentMod(null);

        posts.run();
        for(var proc : processors) proc.run();

        executor.shutdown();
        while(!executor.isTerminated()) posts.run();
        try{
            // Ensure happens-before relationship.
            assert executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        }catch(Exception e){
            throw new RuntimeException(e);
        }

        if(error.isDone()) throw new RuntimeException(error.join());
    }

    private static Seq<Runnable> parseArgs(Cons2<Mod, ModMeta> mod, String[] args){
        try{
            // Offload it to another thread to simulate `Vars.loadAsync()`.
            // Some mods expect the main class constructor to be called off the main thread.
            var future = executor.submit(() -> {
                try{

                    var instance = (Mod)Class.forName(args[0]).getConstructor().newInstance();
                    var meta = new Json().fromJson(ModMeta.class, Jval.read(Fi.get(args[1]).readString()).toString(Jformat.plain));
                    meta.cleanup();

                    mod.get(instance, meta);

                }catch(IndexOutOfBoundsException e){
                    throw new RuntimeException("Expected <mod class> <mod meta file>");
                }catch(ClassNotFoundException | NoSuchMethodException | InstantiationException |
                       IllegalAccessException |
                       InvocationTargetException e){
                    throw new RuntimeException("Couldn't instantiate mod", e);
                }
            });

            while(!future.isDone()) posts.run();
            future.get(); // Ensure happens-before relationship and error propagation.
        }catch(Exception e){
            throw new RuntimeException(e);
        }

        Queue<String> arguments = new Queue<>();
        for(int i = 2; i < args.length; i++) arguments.addLast(args[i]);

        Seq<Runnable> runs = new Seq<>();
        while(!arguments.isEmpty()){
            var arg = arguments.removeFirst();
            if(!arg.equals("-p")) throw new IllegalArgumentException("Expected `-p`");

            AssetProc proc;
            try{
                proc = (AssetProc)Class.forName(arguments.removeFirst()).getConstructor().newInstance();
            }catch(NoSuchElementException e){
                throw new RuntimeException("Expected path to processor class after `-p`");
            }catch(ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                   InvocationTargetException e){
                throw new RuntimeException("Couldn't instantiate processor", e);
            }

            Fi in, out;
            try{
                in = Fi.get(arguments.removeFirst());
                out = Fi.get(arguments.removeFirst());
            }catch(NoSuchElementException e){
                throw new RuntimeException("Expected <input dir> <output dir> after path to processor class");
            }

            var options = new StringMap();
            while(!arguments.isEmpty()){
                var opt = arguments.removeFirst();
                if(opt.startsWith("-")){
                    arguments.addFirst(opt);
                    break;
                }

                int index = opt.indexOf('=');
                if(index == -1) throw new IllegalArgumentException("Expected `<key>=<value>`: " + opt);

                if(options.put(opt.substring(0, index), opt.substring(index + 1)) != null)
                    throw new IllegalArgumentException("Duplicate option");
            }

            runs.add(() -> proc.process(in, out, options));
        }
        return runs;
    }
}
