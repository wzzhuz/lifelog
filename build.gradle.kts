// 顶层构建脚本：只声明插件，不实际应用
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    //
    // 重要：KSP 从 2.3.0 起**不再与 Kotlin 版本绑定**。
    // 老写法 `2.3.21-2.0.2`（Kotlin版本-KSP版本）已废弃，
    // 用它会报 "Plugin was not found"，因为该 artifact 根本不存在。
    // 新版本是独立序号，与 Kotlin 版本无对应关系，直接用最新的即可。
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
