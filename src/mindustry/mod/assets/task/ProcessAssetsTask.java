package mindustry.mod.assets.task;

import mindustry.mod.assets.*;
import org.gradle.api.file.*;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;

import javax.inject.*;
import java.util.*;

/** {@code :processAssets} task. */
public abstract class ProcessAssetsTask extends JavaExec{
    /** @return {@code mod.[h]json} file. */
    public abstract @InputFile @PathSensitive(PathSensitivity.NONE) RegularFileProperty getMeta();

    /** @return {@link AssetProcExtension#getProcessors()}. */
    public abstract @Nested ListProperty<Processor> getProcessors();

    protected abstract @Inject FileSystemOperations getFileSystemOperations();

    public ProcessAssetsTask(){
        onlyIf(t -> !getProcessors().get().isEmpty());

        getMainClass().set("mindustry.mod.assets.AssetProcs");
        jvmArgs("--enable-native-access=ALL-UNNAMED");
        getArgumentProviders().add(() -> {
            List<String> args = new ArrayList<>();
            args.add(getMeta().get().getAsFile().getAbsolutePath());

            for(var proc : getProcessors().get()){
                args.add("-p");
                args.add(proc.getClassName().get());

                for(var in : proc.getInputs().get().entrySet()){
                    args.add("-i");
                    args.add(String.format("%s=%s", in.getKey(), in.getValue().getAsFile().getAbsolutePath()));
                }

                for(var out : proc.getOutputs().get().entrySet()){
                    args.add("-o");
                    args.add(String.format("%s=%s", out.getKey(), out.getValue().getAsFile().getAbsolutePath()));
                }

                for(var arg : proc.getArguments().get().entrySet()){
                    args.add("-a");
                    args.add(String.format("%s=%s", arg.getKey(), arg.getValue()));
                }
            }

            return args;
        });
    }

    @TaskAction
    @Override
    public void exec(){
        var procs = getProcessors().get();
        getFileSystemOperations().delete(spec -> {
            for(var proc : procs) spec.delete(proc.getOutputs().map(Map::values).get());
        });

        super.exec();
    }
}
