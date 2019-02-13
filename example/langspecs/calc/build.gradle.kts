plugins {
  id("org.metaborg.gradle.config.root-project") version "0.5.0"
  id("org.metaborg.spoofax.gradle.langspec") version("develop-SNAPSHOT")
}

version = "develop-SNAPSHOT"

spoofax {
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.esv", metaborgVersion)
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.template", metaborgVersion)
  addSourceLanguageDep("org.metaborg", "meta.lib.spoofax", metaborgVersion)
  addSourceLanguageDep("org.metaborg", "spoofax.gradle.example.calc.lib", "develop-SNAPSHOT")
  addSpoofaxCoreDep()
}
