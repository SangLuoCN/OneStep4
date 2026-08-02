<p align="center">
  <img src="assets/readme/icon.png" width="168" alt="OneStep4.0 应用图标">
</p>

<h1 align="center">OneStep4.0</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.3-4caf50" alt="当前版本 1.0.3">
  <img src="https://img.shields.io/badge/Android-7.0%2B-3ddc84" alt="支持 Android 7.0 及以上版本">
  <img src="https://img.shields.io/badge/API-24--36-1976d2" alt="Android API 24 至 36">
  <img src="https://img.shields.io/badge/Root-Required-e53935" alt="需要 Root 或系统特权权限">
  <img src="https://img.shields.io/badge/License-Apache--2.0-1565c0" alt="Apache License 2.0">
</p>

<p align="center">
  <img src="assets/readme/navigation.png" width="23%" alt="导航组件与多窗口演示">
  <img src="assets/readme/media.png" width="23%" alt="媒体组件与多窗口演示">
  <img src="assets/readme/settings.png" width="23%" alt="一步设置与多窗口演示">
  <img src="assets/readme/recorder.png" width="23%" alt="录音组件与多窗口演示">
</p>

<p align="center">
  <a href="https://www.bilibili.com/video/BV17b3Y6QE4D"><strong>观看 Bilibili 演示视频</strong></a>
  ·
  <a href="https://github.com/SangLuoCN/OneStep4"><strong>GitHub 项目：SangLuoCN/OneStep4</strong></a>
</p>

<p align="center">
  如果 OneStep4.0 对你有帮助，欢迎为演示视频一键三连，也欢迎为 GitHub 项目点一个 Star！
</p>

## 项目简介

OneStep4.0 是面向 Android Root 与系统特权环境的多应用桌面容器，旨在延续 One Step 的多窗口交互方式。应用自身作为系统 Home 运行，在同一工作区中提供一个主窗口、多个侧边小窗口、顶部应用栏、媒体与导航组件，并允许用户在这些容器之间快速打开和切换真实应用。

项目不依赖 Android 自由窗口模式。具备系统任务嵌入能力时会优先使用系统宿主；在通用 Root 环境中，则通过可信 `VirtualDisplay + SurfaceView` 承载应用画面，并将触摸、输入法和焦点准确交给对应的虚拟显示。

> 容器内运行第三方应用需要 Root/SU、Magisk 特权模块、平台签名或等效的系统级任务嵌入权限。仅以普通 APK 安装时，Android 不会授予项目所需的签名级权限。

## 刷入方法

### Magisk

1. 刷入最新 Magisk 模块。
2. 重启设备。
3. 进入 OneStep4.0，并授权 Root 权限；如果没有弹出权限申请，请在 Magisk 中手动授权。
4. 授权完成后即可正常使用。

### KSU

1. 刷入元模块。
2. 刷入最新 KSU 模块。
3. 重启设备。
4. 打开 OneStep4.0 前，请先在 KSU 中手动授予 Root 权限。如果未授权就打开应用，可能出现黑屏；此时请手动授权，清除应用后台后重新打开。
5. 授权完成后即可正常使用。

## 实测可用系统及对应方案

| 手机系统 | Android 版本 | Root 方案 | 使用情况 |
| --- | ---: | --- | --- |
| ColorOS 16 | 16 | KSU | 可用 |
| ColorOS 16 | 16 | ReSukiSU | 可用 |
| ColorOS 16 | 16 | Magisk | 可用 |
| HyperOS 3 | 16 | KSU | 可用 |
| ZUXOS | 15 | Magisk | 可用 |
| 星云 AIOS | 16 | KSU | 可用 |
| 类原生 | 14 | Magisk | 可用 |

## 核心特性

- **主侧多窗口**：提供一个主窗口和 3 至 6 个侧边小窗口，支持快速打开、切换和关闭应用。
- **智能窗口分配**：侧屏有空位时将原主屏应用移入侧屏，全部窗口占满后在主屏替换应用。
- **流畅切换动画**：应用移入侧屏时缩小过渡，替换主屏时原应用渐隐、新应用放大出现。
- **内置桌面**：可从系统中已安装的桌面应用里选择，Home 返回时直接显示当前所选桌面。
- **顶部快捷区**：提供可翻页的应用快捷栏、媒体播放控制和高德导航信息。
- **一步设置**：设置页可像普通应用一样在主屏和侧屏之间切换，并保持页面状态。
- **自定义布局**：可调整桌面图标排列、侧屏数量、顶部栏尺寸与间距、角落触发区域和灵敏度。
- **工作区背景**：支持选择自定义背景并同步系统壁纸。

## 内测反馈群

欢迎加入 OneStep4.0 内测反馈群，交流使用体验、反馈问题并参与功能测试。

<p align="center">
  <img src="assets/readme/qq-feedback-group.jpg" width="360" alt="OneStep4.0 内测反馈群二维码">
</p>

<p align="center"><strong>QQ 群：1081638982</strong></p>
