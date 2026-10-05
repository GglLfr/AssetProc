package mindustry.mod.assets;

import arc.files.*;
import arc.struct.*;

public interface AssetProc{
    void process(ObjectMap<String, Fi> inputs, ObjectMap<String, Fi> outputs, StringMap arguments);
}
