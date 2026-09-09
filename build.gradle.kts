import java.io.OutputStream
import java.util.zip.ZipFile

plugins {
    java
}

group = "com.plexon"
version = "1.0.0"

val pluginVersion = version.toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("com.zpkdxgames:PlexonCore:2.0.2")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("org.xerial:sqlite-jdbc:3.53.4.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:deprecation", "-Xlint:-processing"))
}

tasks.processResources {
    val resourceProperties = mapOf("version" to pluginVersion)
    inputs.properties(resourceProperties)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(resourceProperties)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    enabled = false
}

val shadowJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the installable PlexonJobs JAR with SQLite shaded."
    archiveBaseName.set("PlexonJobs")
    archiveVersion.set(pluginVersion)
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { dependency ->
        if (dependency.isDirectory) dependency else zipTree(dependency)
    })

    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    manifest {
        attributes(
            "Implementation-Title" to "PlexonJobs",
            "Implementation-Version" to pluginVersion,
            "Implementation-Vendor" to "ZpkDxGames",
            "Multi-Release" to "true"
        )
    }
}

val verifyDistribution by tasks.registering {
    group = "verification"
    description = "Verifies release JAR contents, shaded SQLite, and forbidden APIs."
    dependsOn(shadowJar)
    inputs.file(shadowJar.flatMap { it.archiveFile })

    doLast {
        val jarFile = inputs.files.singleFile
        val requiredEntries = listOf(
            "plugin.yml",
            "com/plexon/jobs/PlexonJobs.class",
            "com/plexon/jobs/api/PlexonJobsAPI.class",
            "com/plexon/jobs/event/PlexonJobJoinEvent.class",
            "com/plexon/jobs/event/PlexonJobLeaveEvent.class",
            "com/plexon/jobs/event/PlexonJobXpGainEvent.class",
            "com/plexon/jobs/event/PlexonJobLevelUpEvent.class",
            "com/plexon/jobs/event/PlexonJobPayoutEvent.class",
            "com/plexon/jobs/event/PlexonJobPayoutCommittedEvent.class",
            "org/sqlite/JDBC.class"
        )
        ZipFile(jarFile).use { archive ->
            requiredEntries.forEach { name ->
                check(archive.getEntry(name) != null) { "Release JAR is missing required entry: $name" }
            }
            val forbiddenPrefixes = listOf(
                "com/zpkdxgames/plexoncore/",
                "io/papermc/paper/",
                "net/milkbowl/vault/",
                "me/clip/placeholderapi/"
            )
            check(archive.entries().asSequence().none { entry ->
                forbiddenPrefixes.any { prefix -> entry.name.startsWith(prefix) }
            }) { "Compile-only runtime API classes must not be shaded into PlexonJobs" }

            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory) {
                    archive.getInputStream(entry).use { input ->
                        input.transferTo(OutputStream.nullOutputStream())
                    }
                }
            }
        }
    }
}

tasks.check {
    dependsOn(verifyDistribution)
}

tasks.build {
    dependsOn(shadowJar)
}
