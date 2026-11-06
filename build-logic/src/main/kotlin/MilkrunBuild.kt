import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Shared helpers for the convention plugins. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

/** `:shared:sync` -> `com.milkrun.shared.sync` */
internal val Project.milkrunNamespace: String
    get() = "com.milkrun" + path.replace(':', '.').replace('-', '_')
