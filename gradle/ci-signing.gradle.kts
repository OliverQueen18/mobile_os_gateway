import com.android.build.gradle.AppExtension

// Signature release CI : ANDROID_KEYSTORE_FILE + mots de passe (Jenkins credentials).
subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<AppExtension>("android") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                signingConfigs {
                    create("ciRelease") {
                        storeFile = file(keystorePath)
                        storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                        keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                        keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                    }
                }
                buildTypes {
                    named("release") {
                        signingConfig = signingConfigs.getByName("ciRelease")
                    }
                }
            }
        }
    }
}
