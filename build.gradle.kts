import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint.gradle) apply false
    alias(libs.plugins.detekt) apply false
}

abstract class CheckForbiddenModuleDependenciesTask : DefaultTask() {
    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun check() {
        val foundViolations = violations.get()
        if (foundViolations.isNotEmpty()) {
            throw GradleException(
                "Forbidden module dependencies:\n" + foundViolations.joinToString(separator = "\n"),
            )
        }
    }
}

abstract class CheckForbiddenMotionApisTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun check() {
        val tween = Regex("""\btween\s*(?:<[^>]*>)?\s*\(""") // matches tween( and tween<Float>(
        val root = projectRoot.get().asFile
        // Scan the source tree fresh on every run (no @InputFiles snapshot), so a newly added
        // violation is always caught regardless of the configuration cache. Cheap: skips build dirs.
        val violations = root.walkTopDown()
            .onEnter { dir -> dir.name !in setOf("build", ".git", ".gradle", ".idea", ".kotlin") }
            .filter { it.isFile && it.extension == "kt" && it.name != "StaxMotion.kt" }
            .filter { it.invariantSeparatorsPath.contains("/src/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//").trim()
                    // Skip comment lines (KDoc/block-comment continuations and openers).
                    if (code.startsWith("*") || code.startsWith("/*")) {
                        null
                    } else if (tween.containsMatchIn(code)) {
                        "${file.toRelativeString(root)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Inline tween(...) is forbidden outside StaxMotion (spec §5.9) — use a StaxMotion spec:\n" +
                    violations.joinToString(separator = "\n"),
            )
        }
    }
}

abstract class CheckForbiddenShapeApisTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun check() {
        val roundedCorner = Regex("""\bRoundedCornerShape\s*\(""")
        val root = projectRoot.get().asFile
        // Scan fresh every run (config-cache safe). :core:design-system owns shape construction
        // (StaxShapes + custom morphing components); everything else must use shape tokens.
        val violations = root.walkTopDown()
            .onEnter { dir -> dir.name !in setOf("build", ".git", ".gradle", ".idea", ".kotlin") }
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.invariantSeparatorsPath.contains("/src/") }
            .filterNot { it.invariantSeparatorsPath.contains("/core/design-system/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("*") || code.startsWith("/*")) {
                        null
                    } else if (roundedCorner.containsMatchIn(code)) {
                        "${file.toRelativeString(root)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Inline RoundedCornerShape(...) is forbidden outside :core:design-system (spec §9) — " +
                    "use MaterialTheme.shapes.<slot> or StaxShapes.Pill:\n" +
                    violations.joinToString(separator = "\n"),
            )
        }
    }
}

abstract class CheckForbiddenColorApisTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun check() {
        val colorLiteral = Regex("""\bColor\s*\(\s*0x""") // matches Color(0xFF…)
        val root = projectRoot.get().asFile
        // Scan fresh every run (config-cache safe). Tokens.kt is the only legal home for raw
        // Color(0x…) literals (scheme seeds); everything else uses MaterialTheme.colorScheme / StaxColors.
        val violations = root.walkTopDown()
            .onEnter { dir -> dir.name !in setOf("build", ".git", ".gradle", ".idea", ".kotlin") }
            .filter { it.isFile && it.extension == "kt" && it.name != "Tokens.kt" }
            .filter { it.invariantSeparatorsPath.contains("/src/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("*") || code.startsWith("/*")) {
                        null
                    } else if (colorLiteral.containsMatchIn(code)) {
                        "${file.toRelativeString(root)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Raw Color(0x…) literals are forbidden outside Tokens.kt (spec §9) — " +
                    "use MaterialTheme.colorScheme.<role> or a StaxColors semantic token:\n" +
                    violations.joinToString(separator = "\n"),
            )
        }
    }
}

abstract class CheckForbiddenInsetApisTask : DefaultTask() {
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun check() {
        // Inset-padding modifiers + any direct WindowInsets read. Modifier.paneInsets() (ruler
        // alignment) is the single inset method a pane may use, so mixing in a second one is the
        // double-padding bug §2.3.6 forbids.
        val insetApi = Regex(
            """\b(safeDrawingPadding|safeContentPadding|safeGesturesPadding|imePadding|""" +
                """systemBarsPadding|navigationBarsPadding|statusBarsPadding|""" +
                """displayCutoutPadding|captionBarPadding|windowInsetsPadding|""" +
                """windowInsetsTopHeight|windowInsetsBottomHeight|windowInsetsStartWidth|""" +
                """windowInsetsEndWidth|consumeWindowInsets)\s*\(|\bWindowInsets\.""",
        )
        val root = projectRoot.get().asFile
        // Scan fresh every run (config-cache safe). :core:design-system owns inset handling
        // (StaxPaneInsets); every other module applies it through Modifier.paneInsets().
        val violations = root.walkTopDown()
            .onEnter { dir -> dir.name !in setOf("build", ".git", ".gradle", ".idea", ".kotlin") }
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.invariantSeparatorsPath.contains("/src/") }
            .filterNot { it.invariantSeparatorsPath.contains("/core/design-system/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("*") || code.startsWith("/*")) {
                        null
                    } else if (insetApi.containsMatchIn(code)) {
                        "${file.toRelativeString(root)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw GradleException(
                "WindowInsets APIs are forbidden outside :core:design-system (spec §2.3.6) — a pane " +
                    "gets exactly one inset method, Modifier.paneInsets():\n" +
                    violations.joinToString(separator = "\n"),
            )
        }
    }
}

private val productModules = setOf(
    ":core:domain",
    ":core:database",
    ":core:data",
    ":core:presentation",
    ":core:design-system",
    ":feature:onboarding:presentation",
    ":feature:compounds:presentation",
    ":feature:protocols:presentation",
    ":feature:sites:presentation",
    ":feature:dashboard:presentation",
    ":feature:reconstitution:presentation",
    ":feature:logging:presentation",
    ":feature:settings:presentation",
    ":widget",
    ":shortcut",
    ":work",
    ":notification",
)

private val allowedProjectDependencies = mapOf(
    ":app" to productModules,
    ":core:domain" to emptySet(),
    ":core:database" to setOf(":core:domain"),
    ":core:data" to setOf(":core:domain", ":core:database"),
    ":core:presentation" to setOf(":core:domain"),
    ":core:design-system" to emptySet(),
    ":feature:onboarding:presentation" to setOf(":core:domain", ":core:presentation", ":core:design-system"),
    ":feature:compounds:presentation" to setOf(":core:domain", ":core:presentation", ":core:design-system"),
    ":feature:protocols:presentation" to setOf(":core:domain", ":core:presentation", ":core:design-system"),
    ":feature:sites:presentation" to setOf(":core:domain", ":core:presentation", ":core:design-system"),
    ":feature:dashboard:presentation" to setOf(":core:domain", ":core:presentation", ":core:design-system"),
    ":feature:reconstitution:presentation" to setOf(":core:domain", ":core:presentation", ":core:design-system"),
    ":feature:logging:presentation" to setOf(":core:domain", ":core:presentation", ":core:design-system"),
    ":feature:settings:presentation" to setOf(":core:domain", ":core:presentation", ":core:design-system"),
    ":widget" to setOf(":core:domain", ":core:data"),
    ":shortcut" to setOf(":core:domain", ":core:data"),
    ":work" to setOf(":core:domain", ":core:data"),
    ":notification" to setOf(":core:domain", ":core:data"),
)

val checkForbiddenModuleDependencies = tasks.register<CheckForbiddenModuleDependenciesTask>(
    "checkForbiddenModuleDependencies",
) {
    group = "verification"
    description = "Fails when Stax modules depend outside the Conventions dependency table."
    violations.convention(emptyList())
}

gradle.projectsEvaluated {
    val checkedConfigurations = setOf(
        "api",
        "implementation",
        "compileOnly",
        "runtimeOnly",
        "debugImplementation",
        "releaseImplementation",
    )

    val violations = subprojects.flatMap { module ->
        val allowed = allowedProjectDependencies[module.path] ?: return@flatMap emptyList()

        module.configurations
            .filter { it.name in checkedConfigurations }
            .flatMap { configuration ->
                configuration.dependencies
                    .filter { dependency -> dependency is ProjectDependency }
                    .map { dependency ->
                        configuration.name to (dependency as ProjectDependency).path
                    }
            }
            .filterNot { (_, dependencyPath) -> dependencyPath in allowed }
            .map { (configurationName, dependencyPath) ->
                "${module.path}:$configurationName -> $dependencyPath"
            }
    }

    checkForbiddenModuleDependencies.configure {
        this.violations.set(violations)
    }
}

val checkForbiddenMotionApis = tasks.register<CheckForbiddenMotionApisTask>("checkForbiddenMotionApis") {
    group = "verification"
    description = "Fails when inline tween(...) is used outside StaxMotion — motion specs must be centralized (spec §5.9, M4-04)."
    projectRoot.set(layout.projectDirectory)
}

val checkForbiddenShapeApis = tasks.register<CheckForbiddenShapeApisTask>("checkForbiddenShapeApis") {
    group = "verification"
    description = "Fails when inline RoundedCornerShape(...) is used outside :core:design-system — use shape tokens (spec §9, M4-05)."
    projectRoot.set(layout.projectDirectory)
}

val checkForbiddenColorApis = tasks.register<CheckForbiddenColorApisTask>("checkForbiddenColorApis") {
    group = "verification"
    description = "Fails when a raw Color(0x…) literal is used outside Tokens.kt — use colorScheme roles / StaxColors (spec §9, M4-06)."
    projectRoot.set(layout.projectDirectory)
}

val checkForbiddenInsetApis = tasks.register<CheckForbiddenInsetApisTask>("checkForbiddenInsetApis") {
    group = "verification"
    description = "Fails when a WindowInsets API is used outside :core:design-system — a pane gets exactly one inset method, Modifier.paneInsets() (spec §2.3.6, M5-09)."
    projectRoot.set(layout.projectDirectory)
}

tasks.named("check") {
    dependsOn(
        checkForbiddenModuleDependencies,
        checkForbiddenMotionApis,
        checkForbiddenShapeApis,
        checkForbiddenColorApis,
        checkForbiddenInsetApis,
    )
}
