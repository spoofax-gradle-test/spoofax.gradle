package mb.spoofax.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.ILanguageDiscoveryService
import org.metaborg.core.resource.IResourceService
import org.metaborg.spoofax.core.Spoofax

fun TaskContainer.registerLoadLanguagesTask(languageConfig: Configuration, spoofax: Spoofax, name: String = "spoofaxLoadLanguages") =
  register(name, LoadLanguagesTask::class.java, languageConfig, spoofax.resourceService, spoofax.languageDiscoveryService)

open class LoadLanguagesTask(
  private val languageConfig: Configuration,
  private val resourceService: IResourceService,
  private val languageDiscoveryService: ILanguageDiscoveryService
) : DefaultTask() {
  init {
    dependsOn(languageConfig)
  }


  @InputFiles
  fun languageConfigFiles(): FileCollection = languageConfig


  @TaskAction
  fun execute() {
    languageConfig.forEach { spoofaxLanguageFile ->
      val spoofaxLanguageLoc = resourceService.resolve(spoofaxLanguageFile)
      try {
        languageDiscoveryService.languageFromArchive(spoofaxLanguageLoc)
      } catch(e: MetaborgException) {
        throw GradleException("Failed to load language from $spoofaxLanguageFile", e)
      }
    }
  }
}