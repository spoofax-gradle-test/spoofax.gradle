plugins {
  id("org.metaborg.gradle.config.root-project") version "0.5.0"
  id("org.metaborg.gitonium") version "0.3.0"
  kotlin("jvm") version "1.3.20"
  `kotlin-dsl`
  `java-gradle-plugin`
  `maven-publish`
}

dependencies {
  compile("org.metaborg:org.metaborg.spoofax.meta.core:2.6.0-SNAPSHOT")

  testCompile("org.junit.jupiter:junit-jupiter-api:5.3.1")
  testCompile("io.github.glytching:junit-extensions:2.3.0")
  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}

kotlinDslPluginOptions {
  experimentalWarning.set(false)
}
gradlePlugin {
  plugins {
    create("spoofax-langspec") {
      id = "org.metaborg.spoofax.gradle.langspec"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxLangSpecPlugin"
    }
  }
}

tasks.withType<Test> {
  //useJUnitPlatform {}
}
