plugins { id("com.android.application") }
val irisVc = (project.findProperty("irisVc") as String?)?.toIntOrNull() ?: 3
android { namespace = "com.iris.galeria"; compileSdk = 35
 defaultConfig { applicationId = "com.iris.galeria"; minSdk = 26; targetSdk = 35; versionCode = irisVc; versionName = "1.3.$irisVc" }
 signingConfigs { getByName("debug") { storeFile = file("iris-debug.jks"); storePassword = "android"; keyAlias = "irisdebug"; keyPassword = "android" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
