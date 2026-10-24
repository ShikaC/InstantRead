<p align="center">
  <img src="docs/artifacts/app-icon-a3-padded-84.png" width="112" alt="即阅 InstantRead 应用图标">
</p>

<h1 align="center">即阅 InstantRead</h1>

<p align="center">在 Android 上导入即看 HTML、Markdown、TXT 与 ZIP 网页包。</p>

即阅是一款本地优先的 Android 文件阅读与网页包预览应用。它把 App 内选择、系统打开方式和单文件分享收敛为统一导入流程，让本地文档进入应用后即可阅读；文件保存在 App 私有目录，不依赖云端处理。

## 核心能力

- **HTML / HTM**：使用受限 WebView 预览本地页面，并将外部链接交给系统浏览器。
- **Markdown**：支持阅读与审查模式、全文搜索、阅读位置恢复、Aa 显示设置、浮动目录和当前位置跟踪。
- **TXT**：提供编码识别、可读排版、搜索和阅读位置恢复。
- **ZIP 网页包**：安全解包并自动选择 HTML 入口；没有 HTML 时可用 `index.md` 等 Markdown 入口渲染为阅读页，支持包内相对 CSS、图片等本地资源。
- **本地资料管理**：提供最近记录、收藏、删除和 ZIP 缓存清理；删除记录会移除即阅保存的应用内副本与缓存，手机里的原始文件不会被删除。

## 工程设计

- **本地优先**：文件复制到 App 私有目录处理，不上传，不申请广泛存储权限。
- **安全 WebView**：JavaScript 默认关闭；HTML 与 ZIP 网页包的脚本只能在预览页对当前文档单独授权，且只对该次导入的副本生效，App 生成的 Markdown 阅读页只用内置脚本。每个文档只能读取自己副本目录内的资源，联网地址与越界文件会被拦截并提示页面可能不完整。
- **ZIP 安全边界**：防止路径穿越，并对条目、体积、取消和缓存发布过程设置限制。
- **职责分离**：导入、预览、ZIP、历史、缓存和 programmatic View UI 保持独立边界，`MainActivity` 只承载顶层协调。
- **测试驱动**：关键格式、安全路径、数据清理和交互状态通过自动化测试保护。

## 技术栈

| 范围 | 技术 |
| --- | --- |
| Android 客户端 | Kotlin、Android SDK、原生 programmatic View |
| 内容预览 | WebView、HTML / CSS、受控 JavaScript |
| 本地数据 | App 私有存储、SQLiteOpenHelper |
| 构建与验证 | Gradle、JUnit 4、Robolectric、ADB |

## 验证状态

- 首个稳定版 `0.1.0`（`versionCode=2`）已完成生产签名构建与真机覆盖升级验证：debug / release 两个变体分别通过 58 个 test suites、556 项测试，0 failures、0 errors、0 skipped（2026-10-23 发布候选基线）。
- 已在真机上完成四格式与中文编码、来源入口、脚本授权、异常 ZIP、旋转与同签名覆盖升级验收。
- 通过 GitHub Releases 分发签名 APK；未上架任何应用商店。

## 构建

要求 JDK 17，并安装 Android SDK 34。Gradle wrapper 位于 `apps/android-instantread`，请从该目录运行：

```powershell
Set-Location apps/android-instantread
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug
```

debug APK 输出到（相对仓库根目录）：

```text
apps/android-instantread/app/build/outputs/apk/debug/app-debug.apk
```

## 安装

首个稳定版 `0.1.0` 通过 [GitHub Releases](https://github.com/ShikaC/InstantRead/releases) 分发，提供已签名的 APK、SHA-256 校验值与安装说明。

- 下载 `instantread-0.1.0-release.apk`，在手机上允许「安装未知来源应用」后点开安装。
- App 不申请网络权限，文件只在本机解析。
- 后续版本使用同一签名密钥，直接覆盖安装即可，最近记录与收藏会保留。

想自行构建请见上一节。

## 反馈与安全

- 缺陷与使用问题请开 Issue，模板会询问版本、导入来源与复现步骤。
- 安全问题请走私密漏洞报告，不要开公开 Issue，见 [安全说明](SECURITY.md)。
- 数据处理方式见 [隐私说明](PRIVACY.md)。

## 范围边界

即阅专注于 HTML、Markdown、TXT 和 ZIP 网页包的本地阅读，不是通用文件管理器、浏览器或编辑器；当前不包含 PDF / Office 内置预览、账号、云同步和多设备同步。

## 许可证

本项目以 [MIT 许可证](LICENSE)发布。打包进 APK 的第三方组件及其许可见 [第三方组件许可说明](THIRD-PARTY-NOTICES.md)。
