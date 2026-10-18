plugins {
    kotlin("jvm") version "2.0.21"
}

dependencies {
    implementation("net.jthink:jaudiotagger:3.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.json:json:20240303")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val spikeDir = layout.projectDirectory
val python = providers.gradleProperty("spike.python").orElse("python3")

tasks.test {
    useJUnitPlatform()
    // The matrix records unsupported cases as failures on purpose; the report is the result.
    ignoreFailures = providers.gradleProperty("spike.strict").map { it != "true" }.getOrElse(true)
    systemProperty("spike.fixtures", spikeDir.dir("build/fixtures").asFile.absolutePath)
    systemProperty("spike.reports", layout.buildDirectory.dir("matrix").get().asFile.absolutePath)
    systemProperty("spike.python", python.get())
    systemProperty("spike.oracle", spikeDir.file("fixtures/dump_tags.py").asFile.absolutePath)
    systemProperty("spike.taglib.lib", spikeDir.file("build/native-host/libtaglib_jni.dylib").asFile.absolutePath)
    systemProperty("spike.perf.files", providers.gradleProperty("spike.perf.files").getOrElse("0"))
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
    testLogging { events("failed") }
    outputs.upToDateWhen { false }
}
