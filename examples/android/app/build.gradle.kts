plugins { id("com.android.application") }

android {
    namespace = "com.lokalized.example"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.lokalized.example"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testProguardFiles("test-proguard-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            // This sample's release APK is for compatibility tests, not publication.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testBuildType = if (providers.gradleProperty("minified").isPresent) "release" else "debug"
}

dependencies {
    // Build the sibling Maven project first; test exactly the distributed JAR.
    implementation(files("../../../target/lokalized-3.1.2.jar"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
