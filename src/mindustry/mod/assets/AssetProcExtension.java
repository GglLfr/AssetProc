package mindustry.mod.assets;

import mindustry.mod.assets.task.*;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.model.*;
import org.gradle.api.provider.*;

import javax.inject.*;

public interface AssetProcExtension{
    /** @return {@code mod.[h]json} meta file. */
    RegularFileProperty getMeta();

    /** @return The {@code pregenerated} property in {@linkplain #getMeta() {@code mod.[h]json}}. */
    Property<Boolean> getPregenerated();

    /** @return {@code assets/} directory. */
    DirectoryProperty getAssets();

    /** @return {@code assets-raw/} directory. */
    DirectoryProperty getAssetsRaw();

    /** @return Whether non-UI sprites should be antialiased, just like Vanilla does. */
    Property<Boolean> getAntialias();

    /** @return {@linkplain Class#getName() Full class names} for processors. Defaults to just the sprite processor. */
    ListProperty<Processor> getProcessors();

    /** Adds and configures a new processor. */
    default void processor(Action<? super Processor> configure){
        var proc = getObjects().newInstance(Processor.class);
        configure.execute(proc);
        getProcessors().add(proc);
    }

    @Inject
    ObjectFactory getObjects();
}
