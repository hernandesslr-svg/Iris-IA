plugins { id("com.android.application") }
android { namespace = "com.iris.galeria"; compileSdk = 35
 defaultConfig { applicationId = "com.iris.galeria"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
