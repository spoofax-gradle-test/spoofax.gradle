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
import javax.inject.Inject

fun TaskContainer.registerSpoofaxBuildTask(spoofaxProject: IProject, spoofax: Spoofax, name: String = "spoofaxBuild") =
  register(name, SpoofaxBuildTask::class.java, spoofaxProject, spoofax.dependencyService, spoofax.languagePathService, spoofax.builder)

open class SpoofaxBuildTask @Inject constructor(
  private val spoofaxProject: IProject,
  private val dependencyService: IDependencyService,
  private val languagePathService: ILanguagePathService,
  private val builder: ISpoofaxBuilder
) : DefaultTask() {
  @TaskAction
  fun execute() {
    val inputBuilder = BuildInputBuilder(spoofaxProject)
    inputBuilder.withSourcesFromDefaultSourceLocations(true)
    inputBuilder.addTransformGoal(CompileGoal())
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