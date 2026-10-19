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

应用图标与首页图标由本项目自绘或生成，未使用第三方图标库。仓库中的演示视频
位于不参与分发的 `showcase/` 目录，其脚本依赖 GreenSock GSAP 3.14.2
（GreenSock 标准免费许可，非 OSI 许可），相关说明见该目录内的 notice 文件。
