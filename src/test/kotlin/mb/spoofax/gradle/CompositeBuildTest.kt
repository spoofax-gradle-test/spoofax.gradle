package mb.spoofax.gradle

import io.github.glytching.junit.extension.folder.TemporaryFolder
import io.github.glytching.junit.extension.folder.TemporaryFolderExtension
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeBuildTest {
  @Test
  @ExtendWith(TemporaryFolderExtension::class)
  fun compositeBuildTest(tempDir: TemporaryFolder) {
    tempDir.createFile("settings.gradle.kts").apply {
      writeText("""
        rootProject.name = "compositeBuildTest"

        includeBuild("calc")
        includeBuild("sdf")
        includeBuild("stratego")
      """.trimIndent())
    }
    tempDir.createFile("build.gradle.kts")

    val calcDir = tempDir.createDirectory("calc")
    calcDir.resolve("build.gradle.kts").apply {
      createNewFile()
      writeText("""
        plugins {
          id("org.metaborg.spoofax.gradle.langspec")
        }
      """.trimIndent())
    }
    val sdfDir = tempDir.createDirectory("sdf")
    sdfDir.resolve("build.gradle.kts").apply {
      createNewFile()
      writeText("""
        plugins {
          id("org.metaborg.spoofax.gradle.langspec")
        }
      """.trimIndent())
    }
    val strategoDir = tempDir.createDirectory("stratego")
    strategoDir.resolve("build.gradle.kts").apply {
      createNewFile()
      writeText("""
        plugins {
          id("org.metaborg.spoofax.gradle.langspec")
        }
      """.trimIndent())
    }

    // TODO: create settings.gradle.kts, which includes all builds.
    // TODO: create lang project, with examples and tests, compile dep on metalang SDF and Stratego.
    // TODO: create metalang SDF project, generates Stratego, source dep on Stratego.
    // TODO: create metalang Stratego project.

    GradleRunner.create()
      .withPluginClasspath()
      .withProjectDir(tempDir.root)
      .build()
  }
}