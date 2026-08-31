# 保留数据模型（JSON 序列化与反序列化依赖字段名）
-keepclassmembers class com.zwz.lifelog.domain.model.** { *; }

# Glance 小组件通过反射实例化
-keep class com.zwz.lifelog.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class androidx.glance.** { *; }

# DataStore 序列化器
-keep class androidx.datastore.preferences.** { *; }

# 保留行号，方便排查线上崩溃
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
