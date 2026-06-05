import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
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

val checkForbiddenModuleDependencies = tasks.register<CheckForbiddenModuleDependenciesTask>("checkForbiddenModuleDependencies") {
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

tasks.named("check") {
    dependsOn("checkForbiddenModuleDependencies")
}
