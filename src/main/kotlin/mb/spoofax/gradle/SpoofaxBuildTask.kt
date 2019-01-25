package mb.spoofax.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.metaborg.core.action.CompileGoal
import org.metaborg.core.build.BuildInputBuilder
import org.metaborg.core.build.dependency.IDependencyService
import org.metaborg.core.build.paths.ILanguagePathService
import org.metaborg.core.language.LanguageUtils
import org.metaborg.core.project.IProject
import java.io.File

open class SpoofaxBuildTask(private val project: IProject, private val dependencyService: IDependencyService, private val languagePathService: ILanguagePathService) : DefaultTask() {
  private val builder: BuildInputBuilder = BuildInputBuilder(project)
  private val

  init {
    builder.withCompileDependencyLanguages(false)
    builder.withDefaultIncludePaths(false)
    builder.withSourcesFromDefaultSourceLocations(false)
    builder.addTransformGoal(CompileGoal())

    val langComponents = dependencyService.compileDeps(project)
    val langImpls = LanguageUtils.toImpls(langComponents)
    builder.withLanguages(langImpls)

    for(langImpl in langImpls) {
      val includePaths = languagePathService.includePaths(project, langImpl.belongsTo().name())
      builder.addIncludePaths(langImpl, includePaths)
    }
  }


  @InputFiles
  fun inputFiles(): List<File> {
    val compileComponents = dependencyService.compileDeps(project)
    val compileImpls = LanguageUtils.toImpls(compileComponents)
  }

  @InputFiles
  fun includeDirs(): List<File> {

  }

  @TaskAction
  fun execute(inputs: IncrementalTaskInputs) {

  }
}