package mindustry.mod.assets;

import arc.util.serialization.*;
import mindustry.mod.assets.AssetProcExtension.*;
import org.gradle.api.*;
import org.gradle.api.attributes.*;
import org.gradle.api.plugins.*;
import org.gradle.api.tasks.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

public class AssetProcPlugin implements Plugin<Project>{
    @Override
    public void apply(Project target){
        var conf = target.getConfigurations();
        var deps = target.getDependencies();
        var exts = target.getExtensions();
        var objects = target.getObjects();
        var root = target.getLayout().getProjectDirectory();
        var providers = target.getProviders();
        var tasks = target.getTasks();
        target.getPlugins().apply("java");

        var procDependencies = conf.dependencyScope("procOnly");
        var procClasspath = conf.resolvable("procClasspath", c -> {
            c.extendsFrom(procDependencies.get());
            c.getAttributes()
                .attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage.class, Usage.JAVA_RUNTIME)
                )
                .attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category.class, Category.LIBRARY)
                )
                .attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.class, LibraryElements.JAR)
                );
        });

        var ext = exts.create("assetProc", AssetProcExtension.class);
        var meta = ext.getMeta();
        var pregenerated = ext.getPregenerated();
        var assets = ext.getAssets();
        var assetsRaw = ext.getAssetsRaw();
        var antialias = ext.getAntialias();
        var procs = ext.getProcessors();

        var json = root.file("mod.json");
        var hjson = root.file("mod.hjson");

        meta.convention(providers.provider(() -> json.getAsFile().isFile() ? json : hjson));
        pregenerated.convention(providers.fileContents(meta).getAsText().map(in -> Jval.read(in).getBool("pregenerated", false)));
        assets.convention(root.dir("assets"));
        assetsRaw.convention(root.dir("assets-raw"));
        antialias.convention(false);
        procs.convention(pregenerated.zip(antialias, (p, a) -> p
            ? List.of(new Processor("sprites", "sprites", "mindustry.mod.assets.proc.SpriteProc", Map.of("antialias", a.toString())))
            : Collections.emptyList())
        );

        var mainClass = providers.fileContents(meta).getAsText().map(in -> Jval.read(in).getString("main", null));

        String pluginVersion;
        try(var in = AssetProcPlugin.class.getClassLoader().getResourceAsStream("asset-proc-version")){
            if(in == null) throw new IOException("Missing `asset-proc-version` file from classpath");
            pluginVersion = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            if(pluginVersion.isEmpty()) throw new IOException("File is empty");
        }catch(IOException e){
            throw new GradleException("Couldn't read plugin version", e);
        }

        deps.add(procDependencies.getName(), "com.github.GglLfr.AssetProc:impl:" + pluginVersion);
        deps.addProvider(procDependencies.getName(), pregenerated.filter(Boolean::booleanValue).map(p -> "Anuken:Mindustry:latest:assets"));

        var main = target.getExtensions()
            .getByType(JavaPluginExtension.class)
            .getSourceSets()
            .named("main");

        tasks.register("processAssets", JavaExec.class, t -> {
            t.getInputs().file(meta);
            t.getInputs().property("processors", procs);
            t.getInputs().property("main", mainClass);

            t.getInputs()
                .files(procs.zip(assetsRaw, (list, dir) -> list.stream().map(p -> dir.dir(p.input())).toList()))
                .withPropertyName("assetsRaw")
                .withPathSensitivity(PathSensitivity.RELATIVE);
            t.getOutputs()
                .dirs(procs.zip(assets, (list, dir) -> list.stream().map(p -> dir.dir(p.output())).toList()))
                .withPropertyName("assets");

            t.getMainClass().set("mindustry.mod.assets.AssetProcs");
            t.classpath(main.map(SourceSet::getCompileClasspath), main.map(SourceSet::getRuntimeClasspath), procClasspath);
            t.getArgumentProviders().add(() -> {
                List<String> args = new ArrayList<>();
                args.add(mainClass.get());
                args.add(meta.get().getAsFile().getAbsolutePath());

                var in = assetsRaw.get().getAsFile().toPath();
                var out = assets.get().getAsFile().toPath();

                for(var p : procs.get()){
                    args.add("-p");
                    args.add(p.className());
                    args.add(in.resolve(p.input()).toAbsolutePath().toString());
                    args.add(out.resolve(p.output()).toAbsolutePath().toString());

                    for(var e : p.options().entrySet()){
                        if(e.getKey().startsWith("-"))
                            throw new IllegalArgumentException("Option key cannot start with `-`");
                        args.add(String.format("%s=%s", e.getKey(), e.getValue()));
                    }
                }

                return args;
            });

            t.jvmArgs(
                // Match the ones in native Mindustry client json file.
                "-Dhttps.protocols=TLSv1.2,TLSv1.1,TLSv1",
                "-XX:+ShowCodeDetailsInExceptionMessages",
                "-XX:+UseCompactObjectHeaders",
                "--enable-native-access=ALL-UNNAMED"
            );
        });
    }
}