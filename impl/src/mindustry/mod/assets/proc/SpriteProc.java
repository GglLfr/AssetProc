package mindustry.mod.assets.proc;

import arc.files.*;
import arc.struct.*;
import mindustry.ctype.*;
import mindustry.mod.assets.*;

import static mindustry.Vars.*;

public class SpriteProc implements AssetProc{
    @Override
    public void process(ObjectMap<String, Fi> inputs, ObjectMap<String, Fi> outputs, StringMap arguments){
        content.each(content -> {
            if(!(content instanceof UnlockableContent c)) return;
        });
    }
}
