import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.process.ExecOperations

inline fun <reified T> org.gradle.api.Project.service(): T =
    (this as ProjectInternal).services.get(T::class.java)

fun getGitOutput(execOps: ExecOperations, command: List<String>, workingDir: File): String {
    val output = ByteArrayOutputStream()
    execOps.exec {
        commandLine(command)
        setWorkingDir(workingDir)
        setStandardOutput(output)
    }
    return output.toString().trim()
}

fun getSyncthingNativeVersion(execOps: ExecOperations): String {
    val dir = File(rootDir, "syncthing/src/github.com/syncthing/syncthing")
    val describe = getGitOutput(execOps, listOf("git", "describe", "--always"), dir)
    val regex = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-.+)?""")
    val match = regex.find(describe)
    return match?.let {
        "${it.groupValues[1]}.${it.groupValues[2]}.${it.groupValues[3]}"
    } ?: throw GradleException("getSyncthingNativeVersion: FAILED to extract from '$describe'")
}

fun getSourceDateEpoch(execOps: ExecOperations): String {
    val dir = File(rootDir, "syncthing/src/github.com/syncthing/syncthing")
    return getGitOutput(execOps, listOf("git", "log", "-1", "--format=%ct"), dir)
}

fun verifySyncthingNativeVersionMatchesApp(execOps: ExecOperations) {
    val nativeVersion = getSyncthingNativeVersion(execOps)
    val appVersion = libs.versions.version.name.get()
        .split(".")
        .take(3)
        .joinToString(".")
    if (nativeVersion != appVersion) {
        throw GradleException("SyncthingNative version ($nativeVersion) differs from App version ($appVersion).")
    }
}

fun detectPythonBinary(): String {
    val osName = System.getProperty("os.name").lowercase()
    return if (osName.contains("windows")) "python" else "python3"
}

tasks.register("buildNative") {
    group = "build"
    description = "Builds native Syncthing binaries"

    val execOps = project.service<ExecOperations>()
    val workingDir = project.projectDir
    val outputDir = file("$projectDir/../app/src/main/jniLibs/")
    val inputDir = file("$projectDir/src/")

    inputs.dir(inputDir)
    outputs.dir(outputDir)

    doLast {
        val env = mapOf(
            "NDK_VERSION" to libs.versions.ndk.version.get(),
            "SOURCE_DATE_EPOCH" to getSourceDateEpoch(execOps),
            "BUILD_HOST" to "Catfriend1-syncthing-android",
            "BUILD_USER" to "reproducible-build",
            "STTRACE" to ""
        )

        verifySyncthingNativeVersionMatchesApp(execOps)

        val fullEnv = System.getenv().toMutableMap().apply {
            putAll(env)
        }

        val pythonBinary = detectPythonBinary()

        execOps.exec {
            setEnvironment(fullEnv)
            setWorkingDir(workingDir)
            commandLine(pythonBinary, "-u", "./build-syncthing.py")
        }
    }
}

/**
 * Use separate task instead of standard clean(), so these folders aren't deleted by `gradle clean`.
 */
tasks.register<Delete>("cleanNative") {
    delete(
        file("$projectDir/../app/src/main/jniLibs/"),
        file("gobuild"),
        file("go"),
    )
}
