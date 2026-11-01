plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

val minecraftVersion = "1.21.8"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // Geyser / Floodgate API
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion-R0.1-SNAPSHOT")
    // Floodgate необязателен: плагин работает и без него, но тогда не отличает Bedrock-игроков
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    shadowJar {
        archiveClassifier.set("")
    }
    build {
        dependsOn(shadowJar)
    }
    runServer {
        // ./gradlew runServer — поднимает локальный Paper-сервер с плагином
        minecraftVersion(minecraftVersion)
    }
}
