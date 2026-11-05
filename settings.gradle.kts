// Subito+ — configurazione progetto
// v0.1.0 (2026-08-30) — Fase 0 "Sonda"
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "SubitoPlus"
include(":app")
