package mb.spoofax.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.metaborg.core.build.CleanInput
import org.metaborg.core.language.ILanguageService
import org.metaborg.core.project.IProject
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.build.ISpoofaxBuilder

fun TaskContainer.registerSpoofaxCleanTask(spoofaxProject: IProject, spoofax: Spoofax, name: String = "spoofaxClean") =
  register(name, SpoofaxCleanTask::class.java, spoofaxProject, spoofax.languageService, spoofax.builder)

open class SpoofaxCleanTask(
  private val spoofaxProject: IProject,
  private val languageService: ILanguageService,
  private val builder: ISpoofaxBuilder
) : DefaultTask() {
  @TaskAction
  fun execute() {
    builder.clean(CleanInput(spoofaxProject, languageService.allImpls, null))
  }
}