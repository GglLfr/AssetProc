package mindustry.mod.assets;

import org.gradle.api.file.*;
import org.gradle.api.provider.*;

import java.io.*;
import java.util.*;

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

    /** Convenience method to add a processor. */
    default void addProcessor(String directory, String className, Map<String, String> options){
        addProcessor(directory, directory, className, options);
    }

    /** Convenience method to add a processor. */
    default void addProcessor(String input, String output, String className, Map<String, String> options){
        getProcessors().add(new Processor(input, output, className, options));
    }

    record Processor(String input, String output, String className,
                     Map<String, String> options) implements Serializable{
    }
}
