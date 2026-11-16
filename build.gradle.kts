plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<ProcessResources>("processResources") {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version, "description" to project.description)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks {
    jar {
        archiveBaseName = "scitulus"
    }

    runServer {
        minecraftVersion("1.21.11")
        jvmArgs(
            "-Xms2G", "-Xmx2G",
            "-Dcom.mojang.eula.agree=true",
            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
        )
    }
}
