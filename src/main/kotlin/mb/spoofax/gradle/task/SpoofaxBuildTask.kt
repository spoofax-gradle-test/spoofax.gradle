package mb.spoofax.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.metaborg.core.action.CompileGoal
import org.metaborg.core.build.BuildInputBuilder
import org.metaborg.core.build.dependency.IDependencyService
import org.metaborg.core.build.paths.ILanguagePathService
import org.metaborg.core.language.*
import org.metaborg.core.messages.MessageSeverity
import org.metaborg.core.project.IProject
import org.metaborg.core.resource.*
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.build.ISpoofaxBuilder
import java.io.File

fun TaskContainer.registerSpoofaxBuildTask(spoofaxProject: IProject, spoofax: Spoofax, name: String = "spoofaxBuild") =
  register(name, SpoofaxBuildTask::class.java, spoofaxProject, spoofax.resourceService, spoofax.dependencyService, spoofax.languagePathService, spoofax.builder)

open class SpoofaxBuildTask(
  private val spoofaxProject: IProject,
  private val resourceService: IResourceService,
  private val dependencyService: IDependencyService,
  private val languagePathService: ILanguagePathService,
  private val builder: ISpoofaxBuilder
) : DefaultTask() {
  private val inputBuilder: BuildInputBuilder = BuildInputBuilder(spoofaxProject)
  private val langImpls: Set<ILanguageImpl>
  private val includeDirs: List<File>
  private val sourceFiles: List<File>
  private val spoofaxSourceFiles: List<IdentifiedResource>
  private val outputDirs: List<File>

  init {
    val langComponents = dependencyService.compileDeps(spoofaxProject)
    langImpls = LanguageUtils.toImpls(langComponents)
    inputBuilder.withCompileDependencyLanguages(false)
    inputBuilder.withLanguages(langImpls)

    includeDirs = run {
      inputBuilder.withDefaultIncludePaths(false)
      val mutableIncludeDirs = mutableListOf<File>()
      for(langImpl in langImpls) {
        val includePaths = languagePathService.includePaths(spoofaxProject, langImpl.belongsTo().name())
        mutableIncludeDirs.addAll(includePaths.map { resourceService.localPath(it)!! })
        inputBuilder.addIncludePaths(langImpl, includePaths)
      }
      mutableIncludeDirs
    }

    val mutableSourceFiles = mutableListOf<File>()
    val mutableSpoofaxSourceFiles = mutableListOf<IdentifiedResource>()
    inputBuilder.withSourcesFromDefaultSourceLocations(false)
    for(language in langImpls) {
      val sources = languagePathService.sourceFiles(spoofaxProject, language)
      mutableSourceFiles.addAll(sources.map { resourceService.localPath(it.resource)!! })
      mutableSpoofaxSourceFiles.addAll(sources)
      // Do not add sources to builder yet, as we will be sending source *changes* to the builder in execute.
    }
    sourceFiles = mutableSourceFiles
    spoofaxSourceFiles = mutableSpoofaxSourceFiles

    outputDirs = langImpls.flatMap { langImpl ->
      langImpl.config().generates()
    }.map { generates ->
      val location = spoofaxProject.location().resolveFile(generates.directory())
      resourceService.localPath(location)!!
    }

    inputBuilder.addTransformGoal(CompileGoal())
  }


  @Input
  fun langImpls() = langImpls

  @InputFiles
  fun includeDirs() = includeDirs

  @InputFiles
  fun sourceFiles() = sourceFiles

  @OutputDirectories
  fun outputDirs() = outputDirs


  @TaskAction
  fun execute(inputs: IncrementalTaskInputs) {
    val incremental = if(!inputs.isIncremental) {
      false
    } else {
      val changes = mutableListOf<ResourceChange>()
      inputs.outOfDate {
        val resource = resourceService.resolve(file)
        when {
          isAdded -> changes.add(ResourceChange(resource, ResourceChangeKind.Create))
          isModified -> changes.add(ResourceChange(resource, ResourceChangeKind.Modify))
        }
      }
      // Do not reorder: removed MUST BE CALLED AFTER outOfDate.
      val hasDeletion = run {
        var hasDeletion = false
        inputs.removed {
          when {
            isRemoved -> hasDeletion = true
          }
        }
        hasDeletion
      }
      if(hasDeletion) {
        // Give up on incrementality, since Spoofax languages typically do not handle deletion well.
        false
      } else {
        inputBuilder.addSourceChanges(changes)
        true
      }
    }

    if(!incremental) {
      inputBuilder.addIdentifiedSources(spoofaxSourceFiles)
      for(outputDir in outputDirs) {
        project.delete(outputDir.listFiles())
      }
    }

    val buildInput = inputBuilder.build(dependencyService, languagePathService)
    val output = builder.build(buildInput)
    output.allMessages().forEach { msg ->
      val level = when(msg.severity()) {
        MessageSeverity.NOTE -> LogLevel.INFO
        MessageSeverity.WARNING -> LogLevel.WARN
        MessageSeverity.ERROR -> LogLevel.ERROR
        null -> LogLevel.INFO
      }
      project.logger.log(level, msg.toString())
    }
    if(!output.success()) {
      throw GradleException("Spoofax build failed")
    }
  }
}