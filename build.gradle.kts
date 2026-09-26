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

// Windows 下 javac 默认用 GBK 解码源文件；本插件源码含中文注释/字符串，必须显式指定 UTF-8。
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
    // 无运行时依赖，不需要 shadow，直接产出普通 jar。
    jar {
        archiveBaseName = "scitulus"
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar will be used automatically.
        minecraftVersion("1.21.11")
        jvmArgs(
            "-Xms2G", "-Xmx2G",
            "-Dcom.mojang.eula.agree=true",
            // 让服务端日志/终端按 UTF-8 输出，避免中文乱码。
            "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
        )
    }
}
