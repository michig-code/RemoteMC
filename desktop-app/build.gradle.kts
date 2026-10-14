import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "de.remotemc"
// Release builds get the version from the git tag (-Prmc.version=1.0.0).
version = (findProperty("rmc.version") as String?) ?: "1.0.0"

val androidSources = "../android-app/app/src/main/java"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    sourceSets.main {
        // Network, TLS pinning, models and Minecraft colour codes are shared with the Android app.
        kotlin.srcDir(androidSources)
        kotlin.include(
            "de/remotemc/desktop/**",
            "de/remotemc/app/net/**",
            "de/remotemc/app/data/Models.kt",
            "de/remotemc/app/data/ServerProfile.kt",
            "de/remotemc/app/data/ServerList.kt",
            "de/remotemc/app/data/ListLayout.kt",
            "de/remotemc/app/data/ConnectLink.kt",
            "de/remotemc/app/data/AlertSettings.kt",
            "de/remotemc/app/data/DashboardLayout.kt",
            "de/remotemc/app/notify/AlertRules.kt",
            "de/remotemc/app/notify/ServerWatcher.kt",
            "de/remotemc/app/notify/JoinBundle.kt",
            "de/remotemc/app/ui/util/MinecraftText.kt",
            // Logic shared with the phone: console search, suggestions, favorites/macros, snapshots.
            "de/remotemc/app/ui/ConsoleModel.kt",
            "de/remotemc/app/data/CommandCompletion.kt",
            "de/remotemc/app/data/Shortcuts.kt",
            "de/remotemc/app/data/ServerSnapshot.kt",
            "de/remotemc/app/data/Releases.kt",
            "de/remotemc/app/data/SecureBox.kt",
        )
    }
}

dependencies {
    // -Pwindows builds a jar with the Windows graphics libraries (e.g. from Linux).
    implementation(if (project.hasProperty("windows")) compose.desktop.windows_x64 else compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.json:json:20240303") // same API as Android's built-in org.json
    implementation("net.java.dev.jna:jna-platform:5.15.0") // Windows DPAPI for the stored tokens
    implementation("sh.calvin.reorderable:reorderable:2.5.1") // drag cards on the overview (same as the phone)
    // Native Windows title bar with our own content when running on the JetBrains Runtime.
    implementation("org.jetbrains.runtime:jbr-api:1.10.1")

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4) // find and click things in UI tests
}

// The app's own version (for the update check), from the same -Prmc.version as the installers.
val writeVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    val v = project.version.toString()
    inputs.property("version", v)
    outputs.dir(out)
    doLast { out.get().file("remotemc-version.txt").asFile.apply { parentFile.mkdirs() }.writeText(v) }
}
sourceSets.main { resources.srcDir(writeVersion) }

compose.desktop {
    application {
        mainClass = "de.remotemc.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "RemoteMC"
            packageVersion = project.version.toString()
            description = "Manage your Minecraft server"
            vendor = "RemoteMC"
            copyright = "MIT License"
            // From suggestRuntimeModules, plus EC crypto for the plugin's TLS certificate.
            modules("java.instrument", "jdk.unsupported", "jdk.crypto.ec", "java.naming")
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "RemoteMC"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Same id for every version, so new installers upgrade the old one.
                upgradeUuid = "5b4d2f8e-6c1a-4f0e-9d3b-7a2e8c5f1d64"
            }
        }
    }
}

// Copies the Android strings (and our own images) into the layout Compose resources expect.
val syncStrings by tasks.registering(Sync::class) {
    from("../android-app/app/src/main/res") {
        include("values*/strings.xml", "values*/changelog.xml", "values*/help.xml")
    }
    from("src/main/composeResources")
    includeEmptyDirs = false
    inputs.property("placeholderFormat", 5) // bump when composePlaceholders changes
    // The strings are UTF-8; without this, Windows reads and writes them as cp1252.
    filteringCharset = "UTF-8"
    // Compose resources only fill in numbered string placeholders (%1$s). Android's %d, %s,
    // %.1f and %2$.0f become %1$s, %2$s, …; the app formats numbers itself before passing them.
    filesMatching(listOf("**/strings.xml", "**/changelog.xml", "**/help.xml")) {
        filter { line -> composePlaceholders(line) }
    }
    into(layout.projectDirectory.dir("build/sharedResources"))
}
compose.resources {
    packageOfResClass = "de.remotemc.desktop.res"
    // The app texts (10 languages) come straight from the Android app; mapping the task output
    // makes every resource task depend on the copy.
    customDirectory("main", syncStrings.map { layout.projectDirectory.dir("build/sharedResources") })
}

fun composePlaceholders(line: String): String {
    // Android escapes apostrophes and quotes; Compose resources shows the backslash.
    @Suppress("NAME_SHADOWING") val line = line.replace("\\'", "'").replace("\\\"", "\"")
    var next = 1
    return Regex("""%(?:(\d+)\$)?[-+ 0#]*\d*(?:\.\d+)?([sdf])""").replace(line) { m ->
        val index = m.groupValues[1].ifEmpty { (next++).toString() }
        "%$index\$s"
    }
}

// Windows installer in RemoteMC's colours instead of WiX's red defaults: jpackage takes our
// main.wxs (its own, plus the two pictures) from this folder. The paths in it are filled in here.
val installerResources by tasks.registering(Copy::class) {
    val target = layout.buildDirectory.dir("installer")
    from("installer") {
        include("main.wxs", "*.bmp")
        filesMatching("main.wxs") {
            filter { line -> line.replace("@INSTALLER_DIR@", target.get().asFile.absolutePath.replace('\\', '/')) }
        }
    }
    into(target)
}
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    if (name.contains("Msi") || name.contains("Exe")) {
        dependsOn(installerResources)
        freeArgs.addAll("--resource-dir", layout.buildDirectory.dir("installer").get().asFile.absolutePath)
    }
}
