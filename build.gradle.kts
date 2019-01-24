plugins {
  id("org.metaborg.gradle.config.root-project") version "0.5.0"
  id("org.metaborg.gitonium") version "0.3.0"
  kotlin("jvm") version "1.3.20"
  `kotlin-dsl`
  `java-gradle-plugin`
  `maven-publish`
}

dependencies {
  compile("org.metaborg:org.metaborg.spoofax.meta.core:2.5.1")
}

kotlinDslPluginOptions {
  experimentalWarning.set(false)
}
gradlePlugin {
//  plugins {
//    create("gitonium") {
//      id = "org.metaborg.gitonium"
//      implementationClass = "mb.gitonium.GitoniumPlugin"
//    }
//  }
}
