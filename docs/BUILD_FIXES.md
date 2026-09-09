# PlexonJobs 1.0.0 Build Fixes

## Build fix 1

VaultAPI 1.7 declares a transitive legacy Bukkit 1.13 dependency. PlexonJobs compiles against Paper 26.2, so the Vault dependency explicitly excludes `org.bukkit:bukkit` to prevent Gradle capability conflicts and keep Paper as the authoritative Bukkit/Paper compile API.
