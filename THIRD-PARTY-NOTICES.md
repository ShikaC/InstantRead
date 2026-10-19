# 第三方组件许可说明

本文件列出 InstantRead（即阅）使用的第三方组件及其许可。项目自身以
[MIT](LICENSE) 许可证发布。

## 打包进 APK 的运行时组件

以下组件会随 APK 一起分发，因此其许可与声明需随产物保留：

| 组件 | 版本 | 许可 | 来源 |
| --- | --- | --- | --- |
| Kotlin 标准库（kotlin-stdlib） | 1.9.24 | Apache License 2.0 | https://github.com/JetBrains/kotlin |
| JetBrains Annotations（org.jetbrains:annotations） | 由 Kotlin 插件引入 | Apache License 2.0 | https://github.com/JetBrains/java-annotations |

Apache License 2.0 全文见 https://www.apache.org/licenses/LICENSE-2.0 。
项目未修改上述组件，仅以依赖形式链接使用。

## 仅用于构建与测试、不打包进 APK 的组件

这些组件不进入发布产物，列出以便追溯：

| 组件 | 版本 | 许可 |
| --- | --- | --- |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| Robolectric | 4.16.1 | MIT License |
| Android Gradle Plugin | 8.5.0 | Apache License 2.0 |
| Gradle | 8.9 | Apache License 2.0 |

## 素材

本项目未引入第三方图标库、字体或音频素材。逐项来源如下：

| 素材 | 位置 | 来源 |
| --- | --- | --- |
| 应用图标源文件 | `docs/artifacts/app-icon-a3-source.png` | 项目维护者用 OpenAI 图像模型（gpt-image）生成；文件内嵌 C2PA 声明 `softwareAgent: gpt-image 2.0`、`digitalSourceType: trainedAlgorithmicMedia` |
| 应用图标成品与启动图标 | `docs/artifacts/app-icon-a3-padded-84.png`、`apps/android-instantread/app/src/main/res/mipmap-*/ic_launcher*.png` | 由上述源文件缩放、加内边距导出，未再引入其他素材 |
| 首页月亮与设置图标 | 代码内 Android Canvas 自绘 | 项目自绘，非第三方图标库 |
| 首页图标对照图 | `docs/artifacts/home-icons-aligned-moon-gear.png` | 项目自绘视图的渲染截图 |
| 验证样例 | `docs/validation/android-instantread/samples/**` | 项目自建测试夹具，内含的 PNG 为脚本生成的极小色块 |
| 字体 | 无 | 未打包任何自定义字体 |

演示视频位于不参与分发的 `showcase/` 目录，其脚本依赖 GreenSock GSAP 3.14.2
（GreenSock 标准免费许可，非 OSI 许可），相关说明见该目录内的 notice 文件；
该目录与视频、音频都不进入公开仓库。
