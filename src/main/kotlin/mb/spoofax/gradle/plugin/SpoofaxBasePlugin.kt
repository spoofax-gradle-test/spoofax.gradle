package mb.spoofax.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

open class SpoofaxBasePlugin : Plugin<Project> {
  companion object {
    const val compileLanguage = "spoofaxCompileLanguage"
    const val sourceLanguage = "spoofaxSourceLanguage"
    const val language = "spoofaxLanguage"
  }

  override fun apply(project: Project) {
    val compileLanguageConfig = project.configurations.create(compileLanguage) {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = false
      isCanBeResolved = false
    }
    val sourceLanguageConfig = project.configurations.create(sourceLanguage) {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = false
      isCanBeResolved = false
    }
    project.configurations.create(language) {
      isVisible = true
      isTransitive = true
      isCanBeConsumed = true
      isCanBeResolved = true
      extendsFrom(compileLanguageConfig, sourceLanguageConfig)
    }
  }
}

val Project.compileLanguageConfig get() = this.configurations.getByName(SpoofaxBasePlugin.compileLanguage)
val Project.sourceLanguageConfig get() = this.configurations.getByName(SpoofaxBasePlugin.sourceLanguage)
val Project.languageConfig get() = this.configurations.getByName(SpoofaxBasePlugin.language)
