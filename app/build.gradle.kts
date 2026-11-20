plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val baseVersionCode = 12
val baseVersionName = "0.6.3"

android {
    namespace = "com.simple.mobile"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.simple.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.toInt() ?: baseVersionCode
        versionName = providers.gradleProperty("releaseVersionName").orNull ?: baseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    flavorDimensions += "distribution"
    productFlavors {
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "DIRECT_DISTRIBUTION", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "DIRECT_DISTRIBUTION", "false")
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// Builds an update compatible with locally installed debug APKs signed by this machine's key.
tasks.register<Copy>("packageDebugUpdate") {
    dependsOn("assembleDirectDebug")
    from(layout.buildDirectory.file("outputs/apk/direct/debug/app-direct-debug.apk"))
    into(layout.buildDirectory.dir("outputs/updates"))
    rename("app-direct-debug.apk", "SIMple-${android.defaultConfig.versionCode}.apk")
}


// Keep bundled attribution inventory aligned with the artifacts actually shipped.
afterEvaluate {
val runtimeCoordinates = configurations.named("directReleaseRuntimeClasspath").map { configuration ->
    configuration.resolvedConfiguration.resolvedArtifacts.map { it.moduleVersion.id.toString() }.distinct().sorted()
}
tasks.register("verifyLicenseInventory") {
    inputs.property("runtimeCoordinates", runtimeCoordinates)
    inputs.file(layout.projectDirectory.file("src/main/assets/legal/runtime-components.txt"))
    doLast {
        val expected = inputs.files.singleFile.readLines().filter { it.isNotBlank() }.sorted()
        val actual = inputs.properties["runtimeCoordinates"] as List<*>
        check(expected == actual) { "Runtime dependencies changed: review and update bundled license notices and inventory." }
    }
}
}
