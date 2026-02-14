plugins {
    java
    `maven-publish`
}

group = "craftepxly.me"
version = "3.2.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.opencollab.dev/main/") // Geyser repository
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.5")
    
    compileOnly("org.geysermc.geyser:api:2.4.2-SNAPSHOT") {
        isTransitive = false
    }
    
    implementation("com.zaxxer:HikariCP:5.1.0") // Connection pooling
    implementation("org.xerial:sqlite-jdbc:3.45.1.0") // SQLite driver

    implementation("org.apache.commons:commons-lang3:3.17.0")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation("org.apache.commons:commons-lang3:3.17.0")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    
    jar {
        archiveBaseName.set("EmeraldEconomy")
        archiveVersion.set("3.2.0")
    }
    
    test {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
