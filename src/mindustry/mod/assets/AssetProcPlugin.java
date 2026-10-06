package mindustry.mod.assets;

import arc.util.serialization.*;
import mindustry.mod.assets.task.*;
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
        procs.convention(pregenerated.zip(antialias, (p, a) -> {
            if(p){
                var proc = objects.newInstance(Processor.class);
                proc.getClassName().set("mindustry.mod.assets.proc.SpriteProc");
                proc.getInputs().put("sprites", assetsRaw.dir("sprites"));
                proc.getInputs().put("sprites-override", assetsRaw.dir("sprites-override"));
                proc.getOutputs().put("sprites", assets.dir("sprites"));
                proc.getOutputs().put("sprites-override", assets.dir("sprites-override"));
                proc.getArguments().put("antialias", a.toString());
                return List.of(proc);
            }else{
                return Collections.emptyList();
            }
        }));

        String pluginVersion;
        try(var in = AssetProcPlugin.class.getClassLoader().getResourceAsStream("asset-proc-version")){
            if(in == null) throw new IOException("Missing `asset-proc-version` file from classpath");
            pluginVersion = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            if(pluginVersion.isEmpty()) throw new IOException("File is empty");
        }catch(IOException e){
            throw new GradleException("Couldn't read plugin version", e);
        }

        deps.add("implementation", "com.github.GglLfr.AssetProc:api:" + pluginVersion);
        deps.add(procDependencies.getName(), "com.github.GglLfr.AssetProc:impl:" + pluginVersion);
        deps.addProvider(procDependencies.getName(), pregenerated.filter(Boolean::booleanValue).map(p -> "Anuken:Mindustry:latest:assets"));

        var main = target.getExtensions()
            .getByType(JavaPluginExtension.class)
            .getSourceSets()
            .named("main");

        tasks.register("processAssets", ProcessAssetsTask.class, t -> {
            t.setDescription("Processes raw assets (e.g. sprites).");
            t.getMeta().set(meta);
            t.getProcessors().set(procs);
            t.classpath(main.map(SourceSet::getCompileClasspath), main.map(SourceSet::getRuntimeClasspath), procClasspath);
        });

        tasks.named("clean", Delete.class).configure(t ->
            t.delete(procs.map(list ->
                list.stream()
                    .map(p -> p.getOutputs().map(Map::values))
                    .toList()
            ))
        );
    }
}