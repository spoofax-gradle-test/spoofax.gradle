plugins {
  id("org.metaborg.gradle.config.root-project") version "0.5.0"
  id("org.metaborg.spoofax.gradle.langspec") version ("develop-SNAPSHOT")
}

version = "develop-SNAPSHOT"

spoofax {
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.esv", metaborgVersion)
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.template", metaborgVersion)
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.nabl2.lang", metaborgVersion)
  addCompileLanguageDep("org.metaborg", "dynsem", metaborgVersion)

  addSourceLanguageDep("org.metaborg", "meta.lib.spoofax", metaborgVersion)
  addSourceLanguageDep("org.metaborg", "org.metaborg.meta.nabl2.shared", metaborgVersion)
  addSourceLanguageDep("org.metaborg", "org.metaborg.meta.nabl2.runtime", metaborgVersion)

  addSourceLanguageDep("org.metaborg", "spoofax.gradle.example.calc.lib", "develop-SNAPSHOT")

  addSpoofaxCoreDep()
}
