// Top-level build file — plugin versions declared here, applied in modules.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}

// Local check of the Google Drive OAuth config (Drive API, consent screen, scope, test user).
// Needs a **Desktop app** OAuth client JSON — the "TVs and Limited Input" client can't do this.
//   ./gradlew verifyDriveConfig -Pclient=/path/to/desktop_client_secret.json
tasks.register<Exec>("verifyDriveConfig") {
    group = "verification"
    description = "Runs a local OAuth check of your Google Drive config (see tools/verify_drive_config.py)."
    workingDir = rootDir
    val client = project.findProperty("client") as String?
    val cmd = mutableListOf("python3", "tools/verify_drive_config.py")
    if (!client.isNullOrBlank()) cmd.add(client)
    commandLine(cmd)
    doFirst {
        if (client.isNullOrBlank()) {
            throw GradleException(
                "Usage: ./gradlew verifyDriveConfig -Pclient=/path/to/desktop_client_secret.json\n" +
                    "Create the JSON in Google Cloud → Credentials → OAuth client ID → type \"Desktop app\".",
            )
        }
    }
}
