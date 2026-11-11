# R8 keep 规则（决策 docs/decisions/2026-11-11-r8-minification.md）
#
# 口径：只留反射 / JNI / 按名实例化这三类真正会断的入口，其余交给各库自带的 consumer rules
# 和 R8 的静态分析。不写「一揽子 -keep class com.example.**」这种把收益吃光又掩盖问题的规则。
#
# 每一节都写清楚「为什么非留不可」；没有理由的条目不要往里加。

# ---------------------------------------------------------------- 通用

# 崩溃栈要能对回源码：R8 默认会去掉这两样，线上问题就只能看 mapping.txt 里的行号区间。
-keepattributes SourceFile,LineNumberTable
# 注解与泛型签名参与运行时反射（Serialization / Room / Compose 都有读注解的路径）。
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Kotlin 元数据：反射读 suspend / 默认参数 / 属性时是必需的。
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# ---------------------------------------------------------------- kotlinx.serialization
# 生成的 serializer 由 Companion.serializer() 按名取，R8 改名后 @Serializable 类就取不到。
# 库自带 consumer rules 覆盖了大部分，这里补上「serializer 静态入口」这条。
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------- ONNX Runtime（Kokoro / 词卡发音）
# ai.onnxruntime 的 Java 侧与 native 侧靠 JNI 的类名/方法名互相寻找，改名即 NoSuchMethodError。
-keep class ai.onnxruntime.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ---------------------------------------------------------------- Tesseract4Android（OCR 兜底引擎）
# native 层回调 Java 的 TessBaseAPI 内部类，同样靠固定名字。
-keep class com.googlecode.tesseract.android.** { *; }

# ---------------------------------------------------------------- BouncyCastle（JCA provider）
# Provider 与算法实现类按类名字符串反射装载。
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---------------------------------------------------------------- Tink（备份加密 / F4-04）
# KeyManager、Registry 与 Proto 序列化都靠反射 + 注解找实现。
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ---------------------------------------------------------------- ML Kit（F3-03 主 OCR 引擎）
# ML Kit 的模型组件由注册表按名加载。
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---------------------------------------------------------------- Room / Hilt / Compose
# 三家随 AAR 发布 consumer rules，这里不重复声明。若某库升级把 consumer rules 丢了，
# 症状是「release 启动即崩」，用 -Pr8=false 对照二分。

# ---------------------------------------------------------------- 已知的无害缺失引用
# 这些类只在「桌面 JVM 才有的可选依赖」路径上被引用；Android 上跑不到，交给 R8 报 missing class
# 反而会让构建红。逐条加 -dontwarn 而不是全局 -ignorewarnings，是为了让真正的缺失仍然可见。
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.element.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
-dontwarn kotlinx.coroutines.debug.**
