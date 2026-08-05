plugins {
    alias(libs.plugins.android.application)
}

val zygiskHookRuntime by configurations.creating

abstract class GenerateLegalAssetsTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    abstract val legalFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    @get:javax.inject.Inject
    abstract val fileSystemOperations: org.gradle.api.file.FileSystemOperations

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        fileSystemOperations.sync {
            from(legalFiles) {
                into("legal")
            }
            into(outputDirectory)
        }
    }
}

val generateLegalAssets = tasks.register<GenerateLegalAssetsTask>("generateLegalAssets") {
    legalFiles.from(
        rootProject.layout.projectDirectory.file("LICENSE"),
        rootProject.layout.projectDirectory.file("NOTICE"),
        rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"),
    )
}

android {
    namespace = "com.sangluo.onestep"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.sangluo.onestep"
        minSdk = 29
        targetSdk = 36
        maxSdk = 37
        versionCode = 68
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generateLegalAssets) {
            it.outputDirectory
        }
    }
}

val prepareZygiskHookRuntime = tasks.register<Sync>("prepareZygiskHookRuntime") {
    from(zygiskHookRuntime.incoming.artifacts.resolvedArtifacts.map { artifacts ->
        artifacts.map { zipTree(it.file) }
    })
    include("classes.dex")
    include("jni/arm64-v8a/**")
    include("jni/armeabi-v7a/**")
    into(layout.buildDirectory.dir("zygisk-hook-runtime"))
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    compileOnly("com.aliucord:Aliuhook:1.1.4")
    compileOnly(project(":xposed-api-stubs"))
    zygiskHookRuntime("com.aliucord:Aliuhook:1.1.4") {
        isTransitive = false
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
