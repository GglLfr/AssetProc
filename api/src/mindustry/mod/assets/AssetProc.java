package mindustry.mod.assets;

import arc.files.*;
import arc.struct.*;

public interface AssetProc{
    void process(Fi inputDirectory, Fi outputDirectory, StringMap options);
}
