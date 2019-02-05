package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.util.*
import org.gradle.api.*
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.plugins.*
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.*
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.LanguageVersion
import org.metaborg.core.project.ISimpleProjectService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.build.SpoofaxLangSpecCommonPaths

@Suppress("unused")
class SpoofaxLangSpecPlugin : Plugin<Project> {
  companion object {
    const val spoofaxLanguageExtension = "spoofax-language"
  }

  override fun apply(project: Project) {
    project.pluginManager.apply(SpoofaxBasePlugin::class)
    val extension = SpoofaxExtension(project)
    project.extensions.add("spoofax", extension)
    project.afterEvaluate { configure(this, extension) }
  }

  private fun configure(project: Project, extension: SpoofaxExtension) {
    val compileLanguageConfig = project.compileLanguageConfig
    val sourceLanguageConfig = project.sourceLanguageConfig
    val languageConfig = project.languageConfig

    // Apply Java plugin.
    project.pluginManager.apply(JavaLibraryPlugin::class)
    val javaApiConfig = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

    // Create Spoofax and SpoofaxMeta instance.
    val spoofax = Spoofax()
    val resourceSrv = spoofax.resourceService
    val spoofaxMeta = SpoofaxMeta(spoofax, SpoofaxGradleMetaModule())

    // Get project location
    val projectDir = project.projectDir
    val projectLoc = resourceSrv.resolve(projectDir)

    // Override Spoofax language specification configuration from Gradle build script.
    val configService = spoofaxMeta.injector.getInstance(SpoofaxGradleLanguageSpecConfigService::class.java)
    val configOverride = run {
      val groupId = project.group.toString()
      val id = project.name
      val version = LanguageVersion.parse(project.version.toString())
      val metaborgVersion = extension.metaborgVersion
      val compileDeps = compileLanguageConfig.dependencies.map { it.toSpoofaxDependency() }
      val sourceDeps = sourceLanguageConfig.dependencies.map { it.toSpoofaxDependency() }
      val javaDeps = javaApiConfig.allDependencies.map { it.toSpoofaxDependency() }
      ConfigOverride(groupId, id, version, metaborgVersion, compileDeps, sourceDeps, javaDeps)
    }
    configService.addOverride(projectLoc, configOverride)

    // Create Spoofax language specification project.
    val projectService = spoofax.projectService as ISimpleProjectService
    val spoofaxProject = projectService.create(projectLoc)
    val langSpecProject = spoofaxMeta.languageSpecService.get(spoofaxProject)
      ?: throw GradleException("Project at $projectDir is not a Spoofax language specification project")
    val langSpecPaths = SpoofaxLangSpecCommonPaths(projectLoc)

    // Read Spoofax language specification configuration.
    val config = langSpecProject.config()
    project.group = config.identifier().groupId
    if(project.name != config.identifier().id) {
      throw GradleException("Project name ${project.name} is not equal to language ID ${config.identifier().id} from metaborg.yaml")
    }
    // Set project version only if it it has not been set yet.
    if(project.version == Project.DEFAULT_VERSION) {
      project.version = config.identifier().version.toString()
    }
    // Add dependencies to corresponding dependency configurations when they are empty.
    if(compileLanguageConfig.dependencies.isEmpty()) {
      for(langId in config.compileDeps()) {
        val dep = langId.toGradleDependency(project, languageConfig.name, null, spoofaxLanguageExtension)
        compileLanguageConfig.dependencies.add(dep)
      }
    }
    if(sourceLanguageConfig.dependencies.isEmpty()) {
      for(langId in config.sourceDeps()) {
        val dep = langId.toGradleDependency(project, languageConfig.name, null, spoofaxLanguageExtension)
        sourceLanguageConfig.dependencies.add(dep)
      }
    }
    if(javaApiConfig.allDependencies.isEmpty()) {
      for(id in config.javaDeps()) {
        val dep = id.toGradleDependency(project)
        javaApiConfig.dependencies.add(dep)
      }
      javaApiConfig.dependencies.add(project.dependencies.create("org.metaborg", "org.metaborg.spoofax.core", extension.metaborgVersion))
      extension.addSpoofaxRepos()
    }


    // Eagerly load languages, as task configuration already requires languages to be loaded.
    languageConfig.forEach { spoofaxLanguageFile ->
      val spoofaxLanguageLoc = resourceSrv.resolve(spoofaxLanguageFile)
      try {
        spoofax.languageDiscoveryService.languageFromArchive(spoofaxLanguageLoc)
      } catch(e: MetaborgException) {
        throw GradleException("Failed to load language from $spoofaxLanguageFile", e)
      }
    }


    // Build tasks.
    val buildTask = project.tasks.registerSpoofaxBuildTask(spoofaxProject, spoofax)


    val metaBuilder = spoofaxMeta.metaBuilder
    val metaBuilderInput = LanguageSpecBuildInput(langSpecProject)
    val langSpecGenSourcesTask = project.tasks.register("spoofaxLangSpecGenerateSources") {
      dependsOn(buildTask)
      // No inputs/outputs known: always execute.
      doLast {
        metaBuilder.initialize(metaBuilderInput)
        metaBuilder.generateSources(metaBuilderInput, null)
      }
    }
    val langSpecCompileTask = project.tasks.register("spoofaxLangSpecCompile") {
      dependsOn(langSpecGenSourcesTask)
      // No inputs/outputs known: always execute. Compile itself will run incrementally.
      doLast {
        metaBuilder.compile(metaBuilderInput)
      }
    }
    // Since langSpecCompileTask will generate .java files, the compileJava task from the java plugin depends on it.
    val compileJavaTask = project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
    compileJavaTask.dependsOn(langSpecCompileTask)
    val langSpecPackageTask = project.tasks.register("spoofaxLangSpecPackage") {
      dependsOn(langSpecCompileTask)
      // No inputs/outputs known: always execute. Pkg itself will run incrementally.
      doLast {
        metaBuilder.pkg(metaBuilderInput)
      }
    }
    val archiveFile = resourceSrv.localPath(langSpecPaths.spxArchiveFile(config.identifier().toFileString()))!!
    val langSpecArchiveTask = project.tasks.register("spoofaxLangSpecArchive") {
      dependsOn(langSpecPackageTask)
      outputs.file(archiveFile)
      doLast {
        metaBuilder.archive(metaBuilderInput)
      }
    }
    val assembleTask = project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME)
    assembleTask.dependsOn(langSpecArchiveTask)


    // Clean tasks.
    val spoofaxCleanTask = project.tasks.registerSpoofaxCleanTask(spoofaxProject, spoofax)
    val langSpecCleanTask = project.tasks.register("spoofaxLangSpecClean") {
      dependsOn(spoofaxCleanTask)
      // No inputs/outputs known: always execute.
      doLast {
        metaBuilder.clean(metaBuilderInput)
      }
    }
    val cleanTask = project.tasks.getByName(BasePlugin.CLEAN_TASK_NAME)
    cleanTask.dependsOn(langSpecCleanTask)


    // TODO: build examples tasks.


    // TODO: SPT test tasks.
//    val spoofaxTestTask = project.tasks.register("spoofaxTest") {
//      dependsOn(loadLanguagesTask)
//      // TODO: inputs
//      // TODO: outputs
//      doLast {
//        // TODO: implement
//      }
//    }
//    val checkTask = project.tasks.getByName("check")
//    checkTask.dependsOn(spoofaxTestTask)


    // Add the result of the archive task as an artifact in the 'language' configuration.
    var artifact: PublishArtifact? = null
    project.artifacts {
      artifact = add(languageConfig.name, archiveFile) {
        this.extension = spoofaxLanguageExtension
        builtBy(langSpecArchiveTask)
      }
    }
    if(extension.createPublication) {
      // Add artifact as main publication.
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("SpoofaxLanguage") {
            artifact(artifact) {
              this.extension = spoofaxLanguageExtension
            }
            pom {
              packaging = spoofaxLanguageExtension
              withXml {
                val root = asElement()
                val doc = root.ownerDocument
                val dependenciesNode = doc.createElement("dependencies")
                for(dependency in languageConfig.allDependencies) {
                  val dependencyNode = doc.createElement("dependency")

                  val groupIdNode = doc.createElement("groupId")
                  groupIdNode.appendChild(doc.createTextNode(dependency.group))
                  dependencyNode.appendChild(groupIdNode)

                  val artifactIdNode = doc.createElement("artifactId")
                  artifactIdNode.appendChild(doc.createTextNode(dependency.name))
                  dependencyNode.appendChild(artifactIdNode)

                  val versionNode = doc.createElement("version")
                  versionNode.appendChild(doc.createTextNode(dependency.version))
                  dependencyNode.appendChild(versionNode)

                  val scopeNode = doc.createElement("type")
                  scopeNode.appendChild(doc.createTextNode(spoofaxLanguageExtension))
                  dependencyNode.appendChild(scopeNode)

                  dependenciesNode.appendChild(dependencyNode)
                }
                root.appendChild(dependenciesNode)
              }
            }
          }
        }
      }
    }
  }
}

@Suppress("unused")
open class SpoofaxExtension(private val project: Project) {
  var metaborgVersion: String = "2.5.1"
  var createPublication: Boolean = true

  private val compileLanguageConfig = project.compileLanguageConfig

  fun compileLanguage(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, SpoofaxBasePlugin.language)
    compileLanguageConfig.dependencies.add(dependency)
    return dependency
  }

  fun compileLanguageProject(path: String): Dependency {
    val dependency = project.dependencies.project(path, SpoofaxBasePlugin.language)
    compileLanguageConfig.dependencies.add(dependency)
    return dependency
  }

  private val sourceLanguageConfig = project.sourceLanguageConfig

  fun sourceLanguage(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, SpoofaxBasePlugin.language)
    sourceLanguageConfig.dependencies.add(dependency)
    return dependency
  }

  fun sourceLanguageProject(path: String): Dependency {
    val dependency = project.dependencies.project(path, SpoofaxBasePlugin.language)
    sourceLanguageConfig.dependencies.add(dependency)
    return dependency
  }

  fun addSpoofaxRepos() {
    project.repositories {
      maven("https://artifacts.metaborg.org/content/repositories/releases/")
      maven("https://artifacts.metaborg.org/content/repositories/snapshots/")
      maven("https://pluto-build.github.io/mvnrepository/")
      maven("https://sugar-lang.github.io/mvnrepository/")
      maven("http://nexus.usethesource.io/content/repositories/public/")
    }
  }
}
