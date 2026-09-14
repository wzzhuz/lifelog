import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Room 的 DAO 实现在编译期生成，需要注解处理器。
    // 用 KSP 而非 kapt：更快，且 kapt 在新版 AGP 上已不再推荐
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// 签名配置解析
//
// 优先级：环境变量 > keystore.properties > 无（回退 debug 签名）
//
// 环境变量方式主要给 CI 用：密钥以 base64 存在 GitHub Secrets 里，
// 运行时解码成文件再指过来，避免把密钥提交进公开仓库。
// keystore.properties 已加入 .gitignore，适合本地使用。
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        load(FileInputStream(keystorePropsFile))
    }
}

fun signingValue(propKey: String, envKey: String): String =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: ""

val releaseStoreFile = signingValue("storeFile", "LIFELOG_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "LIFELOG_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "LIFELOG_KEY_ALIAS")
// PKCS12 只有一把锁：私钥就是用 storepass 加密的，keytool 会忽略 -keypass。
// 因此 keyPassword 未单独配置时直接复用 storePassword —— 否则空值会让
// useReleaseSigning 判假，release 构建静默回退 debug 签名且不报任何错。
val releaseKeyPasswordRaw = signingValue("keyPassword", "LIFELOG_KEY_PASSWORD")
val releaseKeyPassword = releaseKeyPasswordRaw.ifBlank { releaseStorePassword }

val useReleaseSigning = releaseStoreFile.isNotBlank()
    && file(releaseStoreFile).exists()
    && releaseStorePassword.isNotBlank()
    && releaseKeyAlias.isNotBlank()
    && releaseKeyPassword.isNotBlank()

// release 构建是否开启 R8 混淆（默认关闭）
// 关掉的原因：Glance 小组件依赖反射实例化，R8 容易误删导致运行时崩溃。
// 想要开启：gradle assembleRelease -PminifyRelease=true
val minifyRelease: Boolean =
    (project.findProperty("minifyRelease") as String?)?.toBoolean() ?: false

android {
    namespace = "com.zwz.lifelog"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zwz.lifelog"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // 固定 debug 签名：保证每次编译出的 APK 签名一致，
        // 否则不同批次编译的 APK 会因签名不同而无法覆盖安装（必须先卸载，数据全丢）
        create("commonDebug") {
            storeFile = file("debug.keystore")
            storePassword = "lifelog2026"
            keyAlias = "lifelogdebug"
            keyPassword = "lifelog2026"
        }
        if (useReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("commonDebug")
            applicationIdSuffix = null
            isMinifyEnabled = false
        }
        release {
            signingConfig = if (useReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("commonDebug")
            }
            // 默认不混淆：Glance 小组件依赖反射，R8 容易误删导致运行时崩溃。
            // 本项目是个人自用，体积不是瓶颈，稳定性优先。
            // 需要时传 -PminifyRelease=true 开启。
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Kotlin 2.2+ 起 kotlinOptions 已废弃，必须用 compilerOptions DSL
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---- Compose ----
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Navigation ----
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // ---- Glance（桌面小组件）----
    implementation("androidx.glance:glance:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // Glance 的状态存储基于 DataStore，显式声明避免版本传递问题
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // ---- Room（本地数据库）----
    // 从「全量 JSON 文件」迁过来的原因：记录上万后，
    // 每次写入都要重新序列化整个文件，开销随数据量线性增长。
    // Room 只写变更的那一行。
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ---- 基础 ----
    implementation("androidx.core:core-ktx:1.16.0")

    testImplementation("junit:junit:4.13.2")
}
