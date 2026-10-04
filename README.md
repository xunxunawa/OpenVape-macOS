# OpenVape macOS

基于 OpenVape v4.21；本项目未额外混淆，且不是 Vape 官方产品。上游项目：[OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)。

提供 Forge 1.8.9 / OptiFine（Java 8）和 Lunar 1.8.9 Forge（Java 17）两版，在 macOS 26.3.1、Apple Silicon M1 上测试。已运行游戏主菜单、单人游戏及一个多人服务器，并完成本地动态注入；进入世界后按 RShift 打开 OpenVape。本地配置保存已验证。

Lunar 启动器须启用内置 **Enable Attach**。注入时会自动屏蔽 Lunar 的 RShift，并临时关闭 **HUD Caching** 和 **Limit Unfocused FPS**；销毁时会归还本工具所作修改。销毁表示停用和有限恢复，已加载的 Java 类会保留到 JVM 退出；不承诺无痕销毁，也不保证兼容所有服务器或模块。

Lunar 版配置可从 JSON 导入；重启游戏后首次注入生效。

源码目录：`forge/src`、`forge/injector-src`、`lunar/src`、`lunar/injector-src`、`common/config-export-src`。使用 `python3 tools/build.py forge` 或 `python3 tools/build.py lunar` 构建；JDK 17 可通过 `JAVA_HOME` 指定，游戏载荷输出 Java 8 字节码，注入器使用 Java 17，App 自带运行时。

App ZIP 请从 GitHub Releases 获取。
