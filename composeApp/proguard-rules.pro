# PUI 安装器 R8 规则
#
# 目标：靠 tree-shaking 砍掉没用到的库代码（dex 占 APK 的 90%+），
# 但**不做混淆、不做激进优化** —— 我们是替换系统安装器的应用，还带着 LSPosed 核心破解模块，
# 类名/方法名一旦被改，下面这些按名字找类的地方全都会炸：
#   - CorePatch 里 Class.forName("android.app.ActivityThread") / Unsafe
#   - LSPosed 通过清单 + 类名加载模块入口
#   - 清单里声明的四大组件

-dontobfuscate
-dontoptimize

# 自己的代码整体保留：反射、内嵌的 JS 式配置、按类名查找都在这两个包里
-keep class com.panl.installer.** { *; }
-keep interface com.panl.installer.** { *; }
-keep class com.panl.pui.** { *; }
-keep interface com.panl.pui.** { *; }

# 注解、泛型签名、内部类信息：Compose 的 @Composable 判定、Kotlin 反射都依赖
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters

# Compose：@Composable 函数由编译器改写，运行时靠这些成员定位
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn androidx.compose.**

# 清单里声明的组件（Application / Activity / Receiver / Provider / Alias 目标）
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

# LSPosed / libxposed：模块入口和 hook 回调按名字加载
-keep class io.github.libxposed.** { *; }
-keep class org.lsposed.** { *; }
-dontwarn io.github.libxposed.**
-dontwarn org.lsposed.**

# 序列化 / 协程调试名：留着方便看堆栈
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
