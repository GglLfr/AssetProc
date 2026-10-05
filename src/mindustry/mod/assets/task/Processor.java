package mindustry.mod.assets.task;

import org.gradle.api.file.*;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.stream.*;

public interface Processor extends Serializable{
    @Internal
    MapProperty<String, Directory> getInputs();

    @Internal
    MapProperty<String, Directory> getOutputs();

    @Input
    Property<String> getClassName();

    @Input
    MapProperty<String, String> getArguments();

    @Nested
    default Provider<Map<String, In>> getInputDirectories(){
        return getInputs().map(inputs ->
            inputs.entrySet().stream().collect(Collectors.toMap(
                Entry::getKey,
                entry -> new In(entry.getValue())
            ))
        );
    }

    @Nested
    default Provider<Map<String, Out>> getOutputDirectories(){
        return getOutputs().map(inputs ->
            inputs.entrySet().stream().collect(Collectors.toMap(
                Entry::getKey,
                entry -> new Out(entry.getValue())
            ))
        );
    }

    record In(Directory directory){
        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        public Directory getDirectory(){
            return directory;
        }
    }

    record Out(Directory directory){
        @OutputDirectory
        public Directory getDirectory(){
            return directory;
        }
    }
}
