package mb.spoofax.gradle

import org.gradle.api.*
import org.metaborg.core.project.ISimpleProjectService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.meta.core.SpoofaxMeta

class SpoofaxLangSpecPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    // Apply Java plugin.

    // Create configurations.
    val compileLanguageConfig = project.configurations.create("compileLanguage") {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = false
      isCanBeResolved = false
    }
    val sourceLanguageConfig = project.configurations.create("sourceLanguage") {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = false
      isCanBeResolved = false
    }
    val languageConfig = project.configurations.create("language") {
      isVisible = true
      isTransitive = true
      isCanBeConsumed = true
      isCanBeResolved = true
      extendsFrom(compileLanguageConfig, sourceLanguageConfig)
    }

    // Create SpoofaxMeta instance.
    val spoofax = Spoofax()
    val resourceSrv = spoofax.resourceService
    val spoofaxMeta = SpoofaxMeta(spoofax)

    // TODO: load Spoofax langspec project.
    val projectDir = project.projectDir
    val projectService = spoofax.projectService as ISimpleProjectService
    val spoofaxProject = projectService.create(resourceSrv.resolve(projectDir))
    val langSpecProject = spoofaxMeta.languageSpecService.get(spoofaxProject)
      ?: throw GradleException("Project at $projectDir is not a Spoofax language specification project")

    val config = langSpecProject.config()
    project.group = config.identifier().groupId
    if(project.name != config.identifier().id) {
      throw GradleException("Project name ${project.name} is not equal to language ID ${config.identifier().id} from metaborg.yaml")
    }
    project.version = config.identifier().version.toString()
    config.compileDeps()

    // TODO: read metaborg.yaml config.
    // TODO: check ID wrt project name
    // TODO: set version from metaborg.yaml
    // TODO: set dependencies from metaborg.yaml.
    // TODO: add compileOnly dependency to spoofax core.

    // TODO: build task
    // TODO: langspec init task
    // TODO: langspec generate sources task
    // TODO: langspec compile task
    // TODO: langspec package task
    // TODO: langspec archive task

    // TODO: clean task
    // TODO: langspec clean task

    // TODO: test task

    // TODO: create spoofax-language archive
    // TODO: create publication
  }
}