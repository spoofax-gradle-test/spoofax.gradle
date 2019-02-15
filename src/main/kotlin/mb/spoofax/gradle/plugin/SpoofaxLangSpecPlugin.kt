package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.*
import mb.spoofax.gradle.util.*
import org.gradle.api.*
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.*
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.language.ILanguageImpl
import org.metaborg.core.language.LanguageVersion
import org.metaborg.core.project.ISimpleProjectService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.meta.core.SpoofaxExtensionModule
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.build.SpoofaxLangSpecCommonPaths
import java.io.File

@Suppress("unused")
class SpoofaxLangSpecPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(BasePlugin::class)
    project.pluginManager.apply(SpoofaxBasePlugin::class)
    // Apply Java library plugin before afterEvaluate to make configurations available to extension.
    project.pluginManager.apply(JavaLibraryPlugin::class)

    val extension = SpoofaxExtension(project)
    project.extensions.add("spoofax", extension)

    Spoofax(SpoofaxGradleModule(), SpoofaxExtensionModule()).use { spoofax ->
      spoofax.configureAsHeadlessApplication()
      SpoofaxMeta(spoofax, SpoofaxGradleMetaModule()).use { spoofaxMeta ->
        project.afterEvaluate { configure(this, extension, spoofax, spoofaxMeta) }
      }
    }
  }

  private fun configure(project: Project, extension: SpoofaxExtension, spoofax: Spoofax, spoofaxMeta: SpoofaxMeta) {
    val compileLanguageConfig = project.compileLanguageConfig
    val sourceLanguageConfig = project.sourceLanguageConfig
    val languageConfig = project.languageConfig
    val javaApiConfig = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

    // Get project location.
    val resourceSrv = spoofax.resourceService
    val projectDir = project.projectDir
    val projectLoc = resourceSrv.resolve(projectDir)

    // Override Spoofax configuration from Gradle build script.
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
    spoofaxMeta.injector.getInstance(SpoofaxGradleProjectConfigService::class.java).addOverride(projectLoc, configOverride)
    spoofaxMeta.injector.getInstance(SpoofaxGradleLanguageComponentConfigService::class.java).addOverride(projectLoc, configOverride)
    spoofaxMeta.injector.getInstance(SpoofaxGradleLanguageSpecConfigService::class.java).addOverride(projectLoc, configOverride)

    // Create Spoofax language specification project.
    val projectService = spoofax.projectService as ISimpleProjectService
    val langSpecProject = spoofaxMeta.languageSpecService.get(projectService.create(projectLoc))
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
        val dependency = langId.toGradleDependency(project, Dependency.DEFAULT_CONFIGURATION)
        project.dependencies.add(compileLanguageConfig.name, dependency) {
          configureSpoofaxLanguageArtifact(dependency)
        }
      }
    }
    if(sourceLanguageConfig.dependencies.isEmpty()) {
      for(langId in config.sourceDeps()) {
        val dependency = langId.toGradleDependency(project, Dependency.DEFAULT_CONFIGURATION)
        project.dependencies.add(sourceLanguageConfig.name, dependency) {
          configureSpoofaxLanguageArtifact(dependency)
        }
      }
    }
    if(javaApiConfig.allDependencies.isEmpty()) {
      for(id in config.javaDeps()) {
        val dependency = id.toGradleDependency(project)
        javaApiConfig.dependencies.add(dependency)
      }
      javaApiConfig.dependencies.add(project.dependencies.create("org.metaborg", "org.metaborg.spoofax.core", extension.metaborgVersion))
      extension.addSpoofaxRepos()
    }


    // Configure Java source and output directories.
    project.configure<SourceSetContainer> {
      val mainSourceSet = getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      mainSourceSet.java {
        // Spoofax build uses the following additional source directories.
        srcDir("src/main/strategies")
        srcDir("src-gen/stratego-java")
        srcDir("src-gen/ds-java")
        // Spoofax build expects compiled Java classes in (Maven-style) 'target/classes' directory.
        outputDir = File(project.projectDir, "target/classes")
      }
    }


    // Load languages task.
    val loadLanguagesTask = project.tasks.registerLoadLanguagesTask(languageConfig, spoofax)


    // TODO: add task that load dialects. See: https://github.com/metaborg/spoofax-maven/blob/master/spoofax-maven-plugin/src/main/java/org/metaborg/spoofax/maven/plugin/AbstractSpoofaxMojo.java#L174-L180


    // Build tasks.
    val buildTask = project.tasks.registerSpoofaxBuildTask(spoofax, langSpecProject)
    buildTask.configure {
      dependsOn(loadLanguagesTask)
      // No inputs/outputs known: always execute.
      langSpecProject.config().pardonedLanguages().forEach {
        addPardonedLanguage(it)
      }
    }

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
    val archiveLoc = langSpecPaths.spxArchiveFile(config.identifier().toFileString())
    val archiveFile = resourceSrv.localPath(archiveLoc)!!
    val langSpecArchiveTask = project.tasks.register("spoofaxLangSpecArchive") {
      dependsOn(langSpecPackageTask)
      outputs.file(archiveFile)
      doLast {
        metaBuilder.archive(metaBuilderInput)
      }
    }
    val assembleTask = project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
    assembleTask.dependsOn(langSpecArchiveTask)

    val loadCompiledLanguageTask = project.tasks.register("spoofaxLoadCompiledLanguage") {
      dependsOn(langSpecArchiveTask)
      inputs.file(archiveFile)
      doLast {
        spoofax.languageDiscoveryService.languageFromArchive(archiveLoc)
      }
    }
    val checkTask = project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
    checkTask.dependsOn(loadCompiledLanguageTask)


    // Clean tasks.
    val spoofaxCleanTask = project.tasks.registerSpoofaxCleanTask(langSpecProject, spoofax)
    spoofaxCleanTask.configure {
      dependsOn(loadLanguagesTask) // TODO: only depends on compile languages
      // No inputs/outputs known: always execute.
    }
    val langSpecCleanTask = project.tasks.register("spoofaxLangSpecClean") {
      dependsOn(spoofaxCleanTask)
      // No inputs/outputs known: always execute.
      doLast {
        metaBuilder.clean(metaBuilderInput)
      }
    }
    val cleanTask = project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME)
    cleanTask.dependsOn(langSpecCleanTask)


    // Build examples tasks.
    val buildExamplesTask = project.tasks.registerSpoofaxBuildTask(spoofax, langSpecProject, "spoofaxBuildExamples")
    buildExamplesTask.configure {
      dependsOn(loadCompiledLanguageTask)
      // No inputs/outputs known: always execute.
      addLanguage(langSpecProject.config().identifier())
    }
    checkTask.dependsOn(buildExamplesTask)

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


    // Add the archive file as an artifact.
    val artifact = project.artifacts.add(Dependency.DEFAULT_CONFIGURATION, archiveFile) {
      this.name = project.name
      this.extension = SpoofaxBasePlugin.spoofaxLanguageExtension
      this.type = SpoofaxBasePlugin.spoofaxLanguageExtension
      builtBy(langSpecArchiveTask)
    }
    if(extension.createPublication) {
      // Add artifact as main publication.
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("SpoofaxLanguage") {
            artifact(artifact) {
              this.extension = SpoofaxBasePlugin.spoofaxLanguageExtension
            }
            pom {
              packaging = SpoofaxBasePlugin.spoofaxLanguageExtension
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
                  scopeNode.appendChild(doc.createTextNode(SpoofaxBasePlugin.spoofaxLanguageExtension))
                  dependencyNode.appendChild(scopeNode)

                  dependenciesNode.appendChild(dependencyNode)
                }
                // TODO: add Java dependencies
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
  var metaborgGroup: String = "org.metaborg"
  var metaborgVersion: String = "2.6.0-SNAPSHOT"
  var createPublication: Boolean = true


  private val compileLanguageConfig = project.compileLanguageConfig

  fun addCompileLanguageDep(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(compileLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }

  fun addCompileLanguageProjectDep(path: String): Dependency {
    val dependency = project.dependencies.project(path, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(compileLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }


  private val sourceLanguageConfig = project.sourceLanguageConfig

  fun addSourceLanguageDep(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(sourceLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }

  fun addSourceLanguageProjectDep(path: String): Dependency {
    val dependency = project.dependencies.project(path, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(sourceLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }


  private val javaApiConfig = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

  fun addSpoofaxCoreDep(): Dependency {
    val dependency = project.dependencies.create(metaborgGroup, "org.metaborg.spoofax.core", metaborgVersion)
    javaApiConfig.dependencies.add(dependency)
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
