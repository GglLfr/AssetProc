package mindustry.mod.assets;

import arc.files.*;
import arc.struct.*;
import arc.struct.Queue;
import arc.util.*;
import mindustry.mod.*;

import java.lang.reflect.*;
import java.util.*;

import static mindustry.Vars.*;

public class AssetProcs{
    public static void main(String[] args){
        ArcNativesLoader.load();
        loadLogger();

        Mod mod;
        try{
            mod = (Mod)Class.forName(args[0]).getConstructor().newInstance();
        }catch(ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
               InvocationTargetException e){
            throw new RuntimeException("Couldn't instantiate mod", e);
        }

        Queue<String> queue = new Queue<>();
        for(int i = 1; i < args.length; i++) queue.addLast(args[i]);

        while(!queue.isEmpty()){
            var arg = queue.removeFirst();
            if(!arg.equals("-p")) throw new IllegalArgumentException("Expected `-p`");

            AssetProc proc;
            try{
                proc = (AssetProc)Class.forName(queue.removeFirst()).getConstructor().newInstance();
            }catch(NoSuchElementException e){
                throw new RuntimeException("Expected path to processor class after `-p`");
            }catch(ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                   InvocationTargetException e){
                throw new RuntimeException("Couldn't instantiate processor", e);
            }

            Fi in, out;
            try{
                in = Fi.get(queue.removeFirst());
                out = Fi.get(queue.removeFirst());
            }catch(NoSuchElementException e){
                throw new RuntimeException("Expected <input dir> <output dir> after path to processor class");
            }

            var options = new StringMap();
            while(!queue.isEmpty()){
                var opt = queue.removeFirst();
                if(opt.startsWith("-")){
                    queue.addFirst(opt);
                    break;
                }

                int index = opt.indexOf('=');
                if(index == -1) throw new IllegalArgumentException("Expected `<key>=<value>`: " + opt);

                if(options.put(opt.substring(0, index), opt.substring(index + 1)) != null)
                    throw new IllegalArgumentException("Duplicate option");
            }

            proc.process(in, out, options);
        }
    }
}
