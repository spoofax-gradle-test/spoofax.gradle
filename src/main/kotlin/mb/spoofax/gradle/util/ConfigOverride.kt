package mb.spoofax.gradle.util

import org.apache.commons.configuration2.HierarchicalConfiguration
import org.apache.commons.configuration2.tree.ImmutableNode
import org.metaborg.core.config.ILanguageComponentConfig
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion

data class ConfigOverride(
  val groupId: String? = null,
  val id: String? = null,
  val version: LanguageVersion? = null,
  val metaborgVersion: String? = null,
  val compileDeps: Collection<LanguageIdentifier> = mutableListOf(),
  val sourceDeps: Collection<LanguageIdentifier> = mutableListOf(),
  val javaDeps: Collection<LanguageIdentifier> = mutableListOf()
) {
  fun applyToConfig(config: HierarchicalConfiguration<ImmutableNode>, languageComponentConfig: ILanguageComponentConfig) {
    val identifier = run {
      val identifier = languageComponentConfig.identifier()
      LanguageIdentifier(groupId ?: identifier.groupId, id ?: identifier.id, version ?: identifier.version)
    }
    config.setProperty("id", identifier)

    if(metaborgVersion != null) {
      config.setProperty("metaborgVersion", metaborgVersion)
    }

    if(!compileDeps.isEmpty()) {
      config.setProperty("dependencies.compile", compileDeps)
    }

    if(!sourceDeps.isEmpty()) {
      config.setProperty("dependencies.source", sourceDeps)
    }

    if(!javaDeps.isEmpty()) {
      config.setProperty("dependencies.java", javaDeps)
    }
  }

  override fun toString(): String {
    return "ConfigOverride(groupId=$groupId, id=$id, version=$version, metaborgVersion=$metaborgVersion, compileDeps=$compileDeps, sourceDeps=$sourceDeps, javaDeps=$javaDeps)"
  }
}