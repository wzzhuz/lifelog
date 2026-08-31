// 顶层构建脚本：只声明插件，不实际应用
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    // KSP 版本必须与 Kotlin 主版本严格对齐：
    // 格式 <Kotlin版本>-<KSP版本>，Kotlin 2.3.21 → KSP 2.3.21-2.0.2
    id("com.google.devtools.ksp") version "2.3.21-2.0.2" apply false
}
