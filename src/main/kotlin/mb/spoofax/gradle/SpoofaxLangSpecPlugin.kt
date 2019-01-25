package mb.spoofax.gradle

import org.gradle.api.*
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.*
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.*
import org.metaborg.core.MetaborgException
import org.metaborg.core.build.BuildInputBuilder
import org.metaborg.core.build.CleanInput
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.messages.MessageSeverity
import org.metaborg.core.project.ISimpleProjectService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput

class SpoofaxLangSpecPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = SpoofaxExtension()
    project.extensions.add("spoofax", extension)
    project.afterEvaluate { configure(this, extension) }
  }

  private fun configure(project: Project, extension: SpoofaxExtension) {
    val log = project.logger
    val spoofaxLanguageExtension = "spoofax-language"

    // Apply Java plugin.
    project.pluginManager.apply(JavaLibraryPlugin::class)

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

    // Create Spoofax and SpoofaxMeta instance.
    val spoofax = Spoofax()
    val resourceSrv = spoofax.resourceService
    val spoofaxMeta = SpoofaxMeta(spoofax)

    // Load Spoofax language specification project
    val projectDir = project.projectDir
    val projectService = spoofax.projectService as ISimpleProjectService
    val spoofaxProject = projectService.create(resourceSrv.resolve(projectDir))
    val langSpecProject = spoofaxMeta.languageSpecService.get(spoofaxProject)
      ?: throw GradleException("Project at $projectDir is not a Spoofax language specification project")

    // Read metaborg.yaml configuration.
    val config = langSpecProject.config()
    // Group
    project.group = config.identifier().groupId
    // Name
    if(project.name != config.identifier().id) {
      throw GradleException("Project name ${project.name} is not equal to language ID ${config.identifier().id} from metaborg.yaml")
    }
    // Version
    project.version = config.identifier().version.toString()
    // Dependencies
    fun LanguageIdentifier.toDependency(configuration: String? = null, classifier: String? = null, ext: String? = null): ExternalModuleDependency {
      return project.dependencies.create(this.groupId, this.id, this.version.toString(), configuration, classifier, ext)
    }
    for(langId in config.compileDeps()) {
      val dep = langId.toDependency(languageConfig.name, null, spoofaxLanguageExtension)
      compileLanguageConfig.dependencies.add(dep)
    }
    for(langId in config.sourceDeps()) {
      val dep = langId.toDependency(languageConfig.name, null, spoofaxLanguageExtension)
      sourceLanguageConfig.dependencies.add(dep)
    }
    val javaApiConfig = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)
    for(id in config.javaDeps()) {
      val dep = id.toDependency()
      javaApiConfig.dependencies.add(dep)
    }
    javaApiConfig.dependencies.add(project.dependencies.create("org.metaborg", "org.metaborg.spoofax.core", extension.metaborgVersion))

    // TODO: support setting configuration (group, id, version, dependencies) in Gradle, to support project dependencies.
    // TODO: extend SpoofaxLanguageSpecConfigService and override toConfig to inject

    // Shared tasks.
    val loadLanguagesTask = project.tasks.register("spoofaxLoadLanguages") {
      dependsOn(languageConfig)
      inputs.files(languageConfig)
      doLast {
        languageConfig.forEach { spoofaxLanguageFile ->
          val spoofaxLanguageLoc = resourceSrv.resolve(spoofaxLanguageFile)
          try {
            spoofax.languageDiscoveryService.languageFromArchive(spoofaxLanguageLoc)
          } catch(e: MetaborgException) {
            throw GradleException("Failed to load language from $spoofaxLanguageFile", e)
          }
        }
      }
    }

    // Build tasks.
    val builder = spoofax.builder
    val buildTask = project.tasks.register("spoofaxBuild") {
      dependsOn(loadLanguagesTask)
      // TODO: inputs
      // TODO: outputs
      doLast {
        val buildInputBuilder = BuildInputBuilder(spoofaxProject)
        // TODO: feed build state to the builder.
        // TODO: feed source input changes to the builder.
        // TODO: feed a printer to the builder?
        val buildInput = buildInputBuilder.build(spoofax.dependencyService, spoofax.languagePathService)
        // TODO: feed progress/cancellation to the builder.
        val output = builder.build(buildInput)
        output.allMessages().forEach { msg ->
          val level = when(msg.severity()) {
            MessageSeverity.NOTE -> LogLevel.INFO
            MessageSeverity.WARNING -> LogLevel.WARN
            MessageSeverity.ERROR -> LogLevel.ERROR
            null -> LogLevel.INFO
          }
          log.log(level, msg.toString())
        }
        if(!output.success()) {
          throw GradleException("Spoofax build failed")
        }
      }
    }
    val metaBuilder = spoofaxMeta.metaBuilder
    val metaBuilderInput = LanguageSpecBuildInput(langSpecProject)
    val langSpecGenSourcesTask = project.tasks.register("spoofaxLangSpecGenerateSources") {
      dependsOn(buildTask)
      // TODO: inputs
      // TODO: outputs
      doLast {
        metaBuilder.initialize(metaBuilderInput)
        metaBuilder.generateSources(metaBuilderInput, null)
      }
    }
    val langSpecCompileTask = project.tasks.register("spoofaxLangSpecCompile") {
      dependsOn(langSpecGenSourcesTask)
      // TODO: inputs
      // TODO: outputs
      doLast {
        metaBuilder.compile(metaBuilderInput)
      }
    }
    val compileJavaTask = project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
    compileJavaTask.dependsOn(langSpecCompileTask)
    val langSpecPackageTask = project.tasks.register("spoofaxLangSpecPackage") {
      dependsOn(langSpecCompileTask)
      // TODO: inputs
      // TODO: outputs
      doLast {
        metaBuilder.pkg(metaBuilderInput)
      }
    }
    val langSpecArchiveTask = project.tasks.register("spoofaxLangSpecArchive") {
      dependsOn(langSpecPackageTask)
      // TODO: inputs
      // TODO: outputs
      doLast {
        metaBuilder.archive(metaBuilderInput)
      }
    }
    val assembleTask = project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME)
    assembleTask.dependsOn(langSpecArchiveTask)

    // Clean tasks.
    val spoofaxCleanTask = project.tasks.register("spoofaxClean") {
      dependsOn(loadLanguagesTask) // TODO: only depends on compile languages!
      // TODO: inputs
      // TODO: outputs
      doLast {
        builder.clean(CleanInput(spoofaxProject, spoofax.languageService.allImpls, null))
      }
    }
    val langSpecCleanTask = project.tasks.register("spoofaxLangSpecClean") {
      dependsOn(spoofaxCleanTask)
      // TODO: inputs
      // TODO: outputs
      doLast {
        metaBuilder.clean(metaBuilderInput)
      }
    }
    val cleanTask = project.tasks.getByName(BasePlugin.CLEAN_TASK_NAME)
    cleanTask.dependsOn(langSpecCleanTask)

    // Test tasks.
    val spoofaxTestTask = project.tasks.register("spoofaxTest") {
      dependsOn(loadLanguagesTask)
      // TODO: inputs
      // TODO: outputs
      doLast {
        // TODO: implement
      }
    }
    val checkTask = project.tasks.getByName("check")
    checkTask.dependsOn(spoofaxTestTask)

    // Add the result of the archive task as an artifact in the 'language' configuration.
    var artifact: PublishArtifact? = null
    project.artifacts {
      artifact = add(languageConfig.name, langSpecArchiveTask) {
        this.extension = spoofaxLanguageExtension
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

open class SpoofaxExtension {
  var metaborgVersion: String = "2.5.1"
  var createPublication: Boolean = true
}
