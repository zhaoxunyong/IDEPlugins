plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.5.2"
}

group = "com.zerofinance"
version = "1.0.9"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-exec:1.3")
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    // https://yoncise.com/logs/2022/08/03/88356600/
    updateSinceUntilBuild.set(false)
    version.set("2022.1")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf(/* Plugin Dependencies */
        "org.jetbrains.plugins.terminal"
    ))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
        options.encoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set("212")
        // untilBuild.set("231.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
