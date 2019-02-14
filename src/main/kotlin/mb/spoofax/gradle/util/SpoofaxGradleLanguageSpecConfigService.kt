package mb.spoofax.gradle.util

import org.apache.commons.configuration2.HierarchicalConfiguration
import org.apache.commons.configuration2.tree.ImmutableNode
import org.apache.commons.vfs2.FileObject
import org.metaborg.core.config.AConfigurationReaderWriter
import org.metaborg.core.config.ConfigRequest
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
      override.applyToConfig(config, languageSpecConfig)
    }

    return ConfigRequest(languageSpecConfig, messages)
  }
}

