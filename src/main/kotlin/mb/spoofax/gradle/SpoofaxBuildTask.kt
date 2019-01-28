package mb.spoofax.gradle

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
import org.metaborg.spoofax.core.build.ISpoofaxBuilder
import java.io.File

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
    if(!inputs.isIncremental) {
      inputBuilder.addIdentifiedSources(spoofaxSourceFiles)
      for(outputDir in outputDirs) {
        project.delete(outputDir)
      }
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
      var hasDeletion = false
      inputs.removed {
        when {
          isRemoved -> hasDeletion = true
        }
      }

      if(hasDeletion) {
        // Give up on incrementality, since Spoofax languages typically do not handle deletion well.
        inputBuilder.addIdentifiedSources(spoofaxSourceFiles)
        for(outputDir in outputDirs) {
          project.delete(outputDir)
        }
      } else {
        inputBuilder.addSourceChanges(changes)
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