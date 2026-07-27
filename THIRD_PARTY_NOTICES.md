# 第三方软件及资源声明

OneStep4.0 的原创代码和文档，以及下列明确标注为 Apache License 2.0
的衍生内容，依照项目根目录 `LICENSE` 中的 Apache License 2.0 发布。
第三方组件仍由各自权利人所有，并适用其各自的许可证。

## 随应用构建的运行时组件

### Smartisan One Step

- 项目：Smartisan Open Source Project - `packages_apps_OneStep`
- 来源：https://github.com/SmartisanTech/packages_apps_OneStep
- 许可证：Apache License 2.0
- 使用范围：部分 One Step 界面资源、资源选择器及相关实现参考；本项目已对其进行修改和扩展。
- 原始版权声明：Copyright 2016 The Smartisan Open Source Project

### AndroidX

- 来源：https://github.com/androidx/androidx
- 许可证：Apache License 2.0
- 直接依赖：
  - `androidx.appcompat:appcompat:1.6.1`
  - `androidx.core:core-ktx:1.10.1`
  - `androidx.viewpager2:viewpager2:1.1.0`
- 随直接依赖解析的 AndroidX 组件包括 Activity、Annotation、AppCompat
  Resources、Arch Core、CardView、Collection、Concurrent Futures、
  ConstraintLayout、CoordinatorLayout、Core、CursorAdapter、CustomView、
  DocumentFile、DrawerLayout、DynamicAnimation、Emoji2、Fragment、
  Interpolator、Legacy Support Core Utils、Lifecycle、Loader、
  LocalBroadcastManager、Print、ProfileInstaller、RecyclerView、
  ResourceInspection、SavedState、Startup、Tracing、Transition、
  VectorDrawable、ViewPager 和 ViewPager2。

### Material Components for Android

- 组件：`com.google.android.material:material:1.10.0`
- 来源：https://github.com/material-components/material-components-android
- 许可证：Apache License 2.0

### Kotlin 标准库

- 组件：`org.jetbrains.kotlin:kotlin-stdlib:2.2.10`
- 来源：https://github.com/JetBrains/kotlin
- 许可证：Apache License 2.0

### Kotlin Coroutines

- 组件：`org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4`
- 来源：https://github.com/Kotlin/kotlinx.coroutines
- 许可证：Apache License 2.0

### JetBrains Annotations

- 组件：`org.jetbrains:annotations:13.0`
- 来源：https://github.com/JetBrains/java-annotations
- 许可证：Apache License 2.0

### Guava ListenableFuture

- 组件：`com.google.guava:listenablefuture:1.0`
- 来源：https://github.com/google/guava
- 许可证：Apache License 2.0

### Error Prone Annotations

- 组件：`com.google.errorprone:error_prone_annotations:2.15.0`
- 来源：https://github.com/google/error-prone
- 许可证：Apache License 2.0

## 仅用于测试的组件

以下组件用于测试，不随正式 APK 分发：

### JUnit 4

- 组件：`junit:junit:4.13.2`
- 来源：https://github.com/junit-team/junit4
- 许可证：Eclipse Public License 1.0

### AndroidX Test JUnit

- 组件：`androidx.test.ext:junit:1.1.5`
- 来源：https://github.com/android/android-test
- 许可证：Apache License 2.0

### AndroidX Espresso Core

- 组件：`androidx.test.espresso:espresso-core:3.5.1`
- 来源：https://github.com/android/android-test
- 许可证：Apache License 2.0

## 仅用于构建的组件

以下组件用于构建或解析开发工具链，不随正式 APK 分发：

### Gradle

- 组件：Gradle Wrapper / Gradle 9.4.1
- 来源：https://github.com/gradle/gradle
- 许可证：Apache License 2.0

### Android Gradle Plugin

- 组件：`com.android.application` / Android Gradle Plugin 9.2.1
- 来源：https://android.googlesource.com/platform/tools/base
- 许可证：Apache License 2.0

### Foojay Toolchains Resolver

- 组件：`org.gradle.toolchains.foojay-resolver-convention:1.0.0`
- 来源：https://github.com/gradle/foojay-toolchains
- 许可证：Apache License 2.0

## 资源与商标

- 来自 Smartisan 公开 One Step 仓库的资源依照其 Apache License 2.0
  使用，并在根目录 `NOTICE` 中保留来源和修改说明。
- 应用运行时从设备和其他应用读取的应用名称、图标、媒体封面及其他
  内容不属于 OneStep4.0 的发布资源，其权利归相应权利人所有。
- Smartisan、One Step、Android、Google、GitHub、哔哩哔哩、抖音、
  高德及其他第三方名称、标志和产品名称可能是其权利人的商标。
  本项目中的引用仅用于识别、兼容性描述或链接，不表示权利人认可本项目。
- Apache License 2.0 不授予任何第三方商标、服务标志、产品名称或徽标
  的使用权。

完整的 Apache License 2.0 文本见项目根目录 `LICENSE`。其他许可证的
完整文本可通过上述各项目来源获取。
