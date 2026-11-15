plugins { id("com.android.application") }
android { namespace = "com.iris.galeria"; compileSdk = 35
 defaultConfig { applicationId = "com.iris.galeria"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "1.1" }
 signingConfigs { getByName("debug") { storeFile = file("iris-debug.jks"); storePassword = "android"; keyAlias = "irisdebug"; keyPassword = "android" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
