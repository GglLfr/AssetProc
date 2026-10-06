package mindustry.mod.assets.proc;

import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.PixmapIO.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.graphics.*;
import mindustry.mod.assets.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.concurrent.*;

import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.mod.assets.AssetProcs.*;

public class SpriteProc extends TextureAtlas implements AssetProc{
    private final ObjectMap<String, Path> vanilla = new ObjectMap<>();
    private final ObjectMap<String, GenRegion> regions = new ObjectMap<>();

    protected void walk(Path path, Cons<Path> cons) throws IOException{
        var matcher = path.getFileSystem().getPathMatcher("glob:**/*.png");
        try(var walk = Files.walk(path)){
            walk.filter(f -> Files.isRegularFile(f) && matcher.matches(f)).forEach(cons::get);
        }
    }

    protected void addVanilla(Path path){
        vanilla.put(name(path), path);
    }

    protected static String name(Path path){
        var n = path.getFileName().toString();
        int dot = n.indexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    protected static Pixmap read(Path path){
        try(var in = Files.newInputStream(path)){
            var reader = new PngReader();
            var result = reader.read(in);
            return new Pixmap(result, reader.width, reader.height);
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void process(ObjectMap<String, Fi> inputs, ObjectMap<String, Fi> outputs, StringMap arguments){
        atlas = this;

        FileSystem fs = null;
        var vanillaSprites = SpriteProc.class.getClassLoader().getResource("assets-raw/sprites");
        if(vanillaSprites == null) Log.err("Vanilla raw sprites not found; this might have severe consequences.");
        else{
            try{
                var uri = vanillaSprites.toURI();
                if(uri.getScheme().equals("jar")){
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                    walk(fs.getPath("assets-raw/sprites"), this::addVanilla);
                }else{
                    walk(Paths.get(uri), this::addVanilla);
                }
            }catch(Exception e){
                throw new RuntimeException(e);
            }
        }

        var prefix = mod.name;
        Fi sprites = inputs.get("sprites"), spritesOverride = inputs.get("sprites-override");
        try{
            Seq<Future<Runnable>> tasks = new Seq<>();
            if(sprites != null) walk(sprites.file().toPath(), f -> tasks.add(executor.submit(() -> {
                var pixmap = read(f);
                var n = prefix + '-' + name(f);
                return () -> addRegion(n, new GenRegion(n, pixmap, RegionSource.sprites));
            })));

            if(spritesOverride != null) walk(spritesOverride.file().toPath(), f -> tasks.add(executor.submit(() -> {
                var pixmap = read(f);
                var n = name(f);
                if(vanilla.remove(n) == null)
                    throw new IllegalArgumentException("Overriding nonexistent sprite `" + n + "`");
                return () -> addRegion(n, new GenRegion(n, pixmap, RegionSource.spritesOverride));
            })));

            for(var task : tasks) task.get().run();
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }catch(Exception e){
            throw new RuntimeException(e);
        }

        var packer = new GenPacker();
        try{
            mod.main.packSprites(packer);
        }catch(MissingSpriteException e){
            Log.err("Missing sprite `@`", e.name);
        }

        content.each(content -> {
            if(!(content instanceof UnlockableContent c) || c.minfo.mod != mod) return;
            try{
                c.init();
                c.load();
                c.createIcons(packer);
            }catch(MissingSpriteException e){
                Log.err("Missing sprite `@`", e.name);
            }
        });

        if(fs != null) try{
            fs.close();
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public GenRegion find(String name) throws MissingSpriteException{
        if(!regions.containsKey(name) && vanilla.containsKey(name)){
            var pixmap = read(vanilla.remove(name));
            regions.put(name, new GenRegion(name, pixmap, RegionSource.vanilla));
        }

        return regions.getThrow(name, () -> new MissingSpriteException(name));
    }

    @Override
    public GenRegion find(String name, String def) throws MissingSpriteException{
        return find(name, find(def));
    }

    @Override
    public GenRegion find(String name, TextureRegion def){
        try{
            return find(name);
        }catch(MissingSpriteException e){
            if(!(def instanceof GenRegion reg))
                throw new UnsupportedOperationException("Cannot use `atlas.find()` with a TextureRegion in sprite processing context unless the sprite was obtained through `atlas.find()`");
            return reg;
        }
    }

    @Override
    public GenRegion addRegion(String name, TextureRegion region){
        if(!(region instanceof GenRegion reg))
            throw new UnsupportedOperationException("Cannot use `atlas.addRegion()` in sprite processing context");

        regions.put(name, reg);
        return reg;
    }

    @Override
    public GenRegion addRegion(String name, Texture texture, int x, int y, int width, int height){
        throw new UnsupportedOperationException("Cannot use `atlas.addRegion()` in sprite processing context");
    }

    @Override
    public PixmapRegion getPixmap(String name){
        return getPixmap(find(name));
    }

    @Override
    public PixmapRegion getPixmap(AtlasRegion region){
        if(!(region instanceof GenRegion reg))
            throw new UnsupportedOperationException("Cannot use `atlas.getPixmap()` in sprite processing context unless the sprite was obtained through `atlas.find()`");
        return reg.pixmapRegion;
    }

    @Override
    public PixmapRegion getPixmap(TextureRegion region){
        if(!(region instanceof GenRegion reg))
            throw new UnsupportedOperationException("Cannot use `atlas.getPixmap()` in sprite processing context unless the sprite was obtained through `atlas.find()`");
        return reg.pixmapRegion;
    }

    public static class GenRegion extends AtlasRegion{
        public final RegionSource src;

        public GenRegion(String name, Pixmap pixmap, RegionSource src){
            pixmapRegion = new PixmapRegion(pixmap);
            this.name = name;
            this.src = src;
            // TODO `splits` and `pads`...maybe
        }
    }

    public enum RegionSource{
        vanilla,
        sprites,
        spritesOverride
    }

    protected class GenPacker extends MultiPacker{
        public GenPacker(){
            super(false);
        }
    }

    public static class MissingSpriteException extends IllegalArgumentException{
        public final String name;

        public MissingSpriteException(String name){
            this.name = name;
        }
    }
}
