package mb.spoofax.gradle.util

import org.apache.commons.configuration2.HierarchicalConfiguration
import org.apache.commons.configuration2.tree.ImmutableNode
import org.apache.commons.vfs2.FileObject
import org.metaborg.core.config.*
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion
import org.metaborg.core.messages.MessageBuilder
import org.metaborg.spoofax.core.config.SpoofaxProjectConfig
import org.metaborg.spoofax.meta.core.config.*
import javax.inject.Inject

class SpoofaxGradleLanguageSpecConfigService @Inject constructor(
  configReaderWriter: AConfigurationReaderWriter,
  configBuilder: SpoofaxLanguageSpecConfigBuilder
) : SpoofaxLanguageSpecConfigService(configReaderWriter, configBuilder) {
  private val overrides = mutableMapOf<FileObject, ConfigOverride>()

  fun addOverride(projectLoc: FileObject, override: ConfigOverride) {
    val configFile = getConfigFile(projectLoc)
    overrides[configFile] = override
  }

  override fun toConfig(config: HierarchicalConfiguration<ImmutableNode>, configFile: FileObject): ConfigRequest<ISpoofaxLanguageSpecConfig> {
    val projectConfig = SpoofaxProjectConfig(config)
    val languageSpecConfig = SpoofaxLanguageSpecConfig(config, projectConfig)
    val mb = MessageBuilder.create().asError().asInternal().withSource(configFile)
    val messages = languageSpecConfig.validate(mb)

    val override = overrides[configFile]
    if(override != null) {
      val identifier = run {
        val identifier = languageSpecConfig.identifier()
        LanguageIdentifier(override.groupId ?: identifier.groupId, override.id ?: identifier.id, override.version
          ?: identifier.version)
      }
      config.setProperty("id", identifier)

      if(override.metaborgVersion != null) {
        config.setProperty("metaborgVersion", override.metaborgVersion)
      }

      if(!override.compileDeps.isEmpty()) {
        config.setProperty("dependencies.compile", override.compileDeps)
      }

      if(!override.sourceDeps.isEmpty()) {
        config.setProperty("dependencies.source", override.sourceDeps)
      }

      if(!override.javaDeps.isEmpty()) {
        config.setProperty("dependencies.java", override.javaDeps)
      }
    }

    return ConfigRequest(languageSpecConfig, messages)
  }
}

data class ConfigOverride(
  val groupId: String? = null,
  val id: String? = null,
  val version: LanguageVersion? = null,
  val metaborgVersion: String? = null,
  val compileDeps: Collection<LanguageIdentifier> = mutableListOf(),
  val sourceDeps: Collection<LanguageIdentifier> = mutableListOf(),
  val javaDeps: Collection<LanguageIdentifier> = mutableListOf()
)