package mb.spoofax.gradle.util

import com.google.inject.Singleton
import org.metaborg.meta.core.config.*
import org.metaborg.spoofax.meta.core.SpoofaxMetaModule
import org.metaborg.spoofax.meta.core.config.*

class SpoofaxGradleMetaModule : SpoofaxMetaModule() {
  override fun bindLanguageSpecConfig() {
    bind(ILanguageSpecConfigWriter::class.java).to(LanguageSpecConfigService::class.java).`in`(Singleton::class.java)

    bind(SpoofaxGradleLanguageSpecConfigService::class.java).`in`(Singleton::class.java)
    bind(ILanguageSpecConfigService::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)
    bind(ISpoofaxLanguageSpecConfigService::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)
    bind(ISpoofaxLanguageSpecConfigWriter::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)

    bind(SpoofaxLanguageSpecConfigBuilder::class.java)
    bind(ILanguageSpecConfigBuilder::class.java).to(SpoofaxLanguageSpecConfigBuilder::class.java)
    bind(ISpoofaxLanguageSpecConfigBuilder::class.java).to(SpoofaxLanguageSpecConfigBuilder::class.java)
  }
}