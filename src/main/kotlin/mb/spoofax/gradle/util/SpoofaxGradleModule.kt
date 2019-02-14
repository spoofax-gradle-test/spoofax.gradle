package mb.spoofax.gradle.util

import com.google.inject.Singleton
import org.metaborg.core.editor.IEditorRegistry
import org.metaborg.core.editor.NullEditorRegistry
import org.metaborg.spoofax.core.SpoofaxModule

class SpoofaxGradleModule : SpoofaxModule() {
  override fun bindEditor() {
    bind(IEditorRegistry::class.java).to(NullEditorRegistry::class.java).`in`(Singleton::class.java)
  }

  override fun bindProjectConfig() {
    // TODO: override with own config service.
    super.bindProjectConfig()
  }

  override fun bindLanguageComponentConfig() {
    // TODO: override with own config service.
    super.bindLanguageComponentConfig()
  }
}
