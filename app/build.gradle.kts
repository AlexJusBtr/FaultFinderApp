plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.eduvos.s4.faultfinderapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.eduvos.s4.faultfinderapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("com.cloudinary:cloudinary-android:3.1.3")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    //Firebase
    //BoM - manages library versions automatically
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    //Firebase Authentication (login, sign-up, admin roles)
    implementation("com.google.firebase:firebase-auth")
    //Cloud Firestore (report descriptions, GPS tags, status, and categories)
    implementation("com.google.firebase:firebase-firestore")
    //Cloud Storage (user pictures of their fault)
    implementation("com.google.firebase:firebase-storage")
    //Analytics (idk maybe we use)
    implementation("com.google.firebase:firebase-analytics")

    //OSM openstreetmap osmdroid
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
