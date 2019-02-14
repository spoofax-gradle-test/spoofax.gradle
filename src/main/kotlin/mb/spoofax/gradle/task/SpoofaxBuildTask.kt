package mb.spoofax.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.metaborg.core.action.CompileGoal
import org.metaborg.core.build.BuildInputBuilder
import org.metaborg.core.build.dependency.IDependencyService
import org.metaborg.core.build.paths.ILanguagePathService
import org.metaborg.core.messages.StreamMessagePrinter
import org.metaborg.core.project.IProject
import org.metaborg.core.source.ISourceTextService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.build.ISpoofaxBuilder
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector
import javax.inject.Inject

fun TaskContainer.registerSpoofaxBuildTask(spoofaxProject: IProject, pardonedLanguages: Iterable<String>?, spoofax: Spoofax, name: String = "spoofaxBuild") =
  register(name, SpoofaxBuildTask::class.java, spoofaxProject, spoofax.sourceTextService, pardonedLanguages, spoofax.dependencyService, spoofax.languagePathService, spoofax.builder)

open class SpoofaxBuildTask @Inject constructor(
  private val spoofaxProject: IProject,
  private val sourceTextService: ISourceTextService,
  private val pardonedLanguages: Iterable<String>?,
  private val dependencyService: IDependencyService,
  private val languagePathService: ILanguagePathService,
  private val builder: ISpoofaxBuilder
) : DefaultTask() {
  @TaskAction
  fun execute() {
    val inputBuilder = BuildInputBuilder(spoofaxProject).run {
      withCompileDependencyLanguages(true)
      withDefaultIncludePaths(true)
      withSourcesFromDefaultSourceLocations(true)
      withSelector(SpoofaxIgnoresSelector())
      withMessagePrinter(StreamMessagePrinter(sourceTextService, true, true, System.out, System.out, System.out))
      withThrowOnErrors(true)
      if(pardonedLanguages != null) {
        withPardonedLanguageStrings(pardonedLanguages)
      }
      addTransformGoal(CompileGoal())
    }
    val buildInput = inputBuilder.build(dependencyService, languagePathService)
    val output = builder.build(buildInput)
    if(!output.success()) {
      throw GradleException("Spoofax build failed")
    }
  }
}