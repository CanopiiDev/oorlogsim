plugins {
    java
}

group = "nl.oorlogsim"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.135-stable")
    compileOnly("com.github.NEZNAMY:TAB-API:6.1.2")
    compileOnly("me.clip:placeholderapi:2.12.3")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test { useJUnitPlatform() }
    compileJava { options.encoding = "UTF-8"; options.release.set(25) }
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") { expand("version" to project.version) }
    }
}
