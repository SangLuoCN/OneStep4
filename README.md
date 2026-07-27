<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_artwork.png" width="168" height="168" alt="OneStep4.0 应用图标">
</p>

<h1 align="center">OneStep4.0</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.0-4caf50" alt="当前版本 1.0.0">
  <img src="https://img.shields.io/badge/Android-7.0%2B-3ddc84" alt="支持 Android 7.0 及以上版本">
  <img src="https://img.shields.io/badge/API-24--36-1976d2" alt="Android API 24 至 36">
  <img src="https://img.shields.io/badge/Root-Required-e53935" alt="需要 Root 或系统特权权限">
  <img src="https://img.shields.io/badge/License-Apache--2.0-1565c0" alt="Apache License 2.0">
</p>

## 项目简介

OneStep4.0 是面向 Android Root 与系统特权环境的多应用桌面容器，旨在延续 One Step 的多窗口交互方式。应用自身作为系统 Home 运行，在同一工作区中提供一个主窗口、多个侧边小窗口、顶部应用栏、媒体与导航组件，并允许用户在这些容器之间快速打开和切换真实应用。

项目不依赖 Android 自由窗口模式。具备系统任务嵌入能力时会优先使用系统宿主；在通用 Root 环境中，则通过可信 `VirtualDisplay + SurfaceView` 承载应用画面，并将触摸、输入法和焦点准确交给对应的虚拟显示。

> 容器内运行第三方应用需要 Root/SU、Magisk 特权模块、平台签名或等效的系统级任务嵌入权限。仅以普通 APK 安装时，Android 不会授予项目所需的签名级权限。

## 核心特性

- **主屏与侧屏应用容器**：以主窗口为当前操作中心，并支持 3 至 6 个侧边小窗口。打开新应用时会根据空闲窗口自动迁移当前主屏内容，所有窗口占满后则替换当前主屏内容。
- **符合系统习惯的后台处理**：只有主屏内容被替换时，原应用才交由 Android 系统进入后台，不主动关闭进程；侧边小窗口仍可通过侧滑手势关闭。
- **内嵌桌面与 Home 逻辑**：OneStep4.0 保持系统 Home 身份，内置应用列表作为工作区的最后一层。按下 Home 只会让容器返回内嵌桌面，不会退出或重启 OneStep4.0。
- **可信虚拟显示**：Root 模式下为每个应用创建独立的可信虚拟显示，在固定窗口中显示真实 Activity、Dialog、Popup 与系统权限界面。
- **多窗口输入与焦点管理**：将触摸事件转换为目标显示坐标并注入对应虚拟显示，切换主屏时同步转移输入焦点，同时为每个显示配置本地输入法策略。
- **完整的应用切换动画**：主屏有空闲侧窗时，原内容等比缩小并移动到侧屏；主屏替换时原内容渐隐，新内容以放大动画出现。
- **一步设置与应用一致**：设置页按照普通应用参与窗口分配、主屏替换和缩放流程，切换到侧屏时保持页面状态并进行整页等比缩放，不挤压内部布局。
- **顶部应用与组件区域**：提供可横向翻页的应用快捷栏，并集成媒体播放控制、通知媒体会话和高德导航信息展示。
- **可调工作区布局**：支持调整桌面图标行列、侧边窗口数量、顶部图标大小与间距、导航栏和图标栏高度、角落触发区域及灵敏度。
- **统一背景显示**：支持在一步设置中选择工作区背景，并同步系统壁纸，减少 Home 切换或容器刷新时出现不同壁纸闪现的问题。
- **多种系统级部署方式**：支持普通 Root/SU 运行、ADB Root 安装为 `priv-app`，以及生成与应用版本自动同步的 Magisk 特权模块。
