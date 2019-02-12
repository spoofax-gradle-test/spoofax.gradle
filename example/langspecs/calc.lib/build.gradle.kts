plugins {
  id("org.metaborg.gradle.config.root-project") version "0.5.0"
  id("org.metaborg.spoofax.gradle.langspec") version("develop-SNAPSHOT")
}

version = "develop-SNAPSHOT"

spoofax {
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.esv", metaborgVersion)
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.stratego", metaborgVersion)
  addSpoofaxCoreDep()
}
