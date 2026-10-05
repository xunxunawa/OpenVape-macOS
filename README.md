# OpenVape macOS

基于 OpenVape v4.21，非 Vape 官方产品。上游项目：[OpenVapeCN/OpenVape](https://github.com/OpenVapeCN/OpenVape)。

提供 Forge 1.8.9 / OptiFine（Java 8）和 Lunar 1.8.9 Forge（Java 17）两版，适用于 Apple Silicon Mac。测试环境为 macOS 26.3.1、Apple M1，已完成主菜单、单人世界及一个多人服务器内的动态注入测试；进入世界后按 RShift 打开 OpenVape。本地配置保存已验证。

Lunar 注入时会自动屏蔽其原有的 RShift 设置界面，并临时关闭 **HUD Caching** 和 **Limit Unfocused FPS**。点击“销毁”会停用模块并归还本工具所作修改，但已加载的 Java 类会保留到游戏退出，不承诺无痕销毁，也不保证兼容所有服务器或模块。

Lunar 版支持导入 JSON 配置；导入后重新启动游戏，在首次注入时生效。

## 下载与打开

从 [GitHub Releases](https://github.com/xunxunawa/OpenVape-macOS/releases) 下载对应版本的 App ZIP，解压后可移入“应用程序”目录。App 自带运行时，无需另行安装 Java 来运行注入器。

App 未经过 Apple Developer ID 签名和公证。首次打开若提示无法验证开发者，先尝试打开一次，再进入 **系统设置 → 隐私与安全性 → 仍要打开**，按提示确认。

## Lunar：启用 Enable Attach

使用 Lunar 版前，需要开启启动器内置的 **Enable Attach**，允许本地工具连接游戏 Java 进程。此操作无需修改 Lunar 客户端程序。

1. 完全退出 Minecraft 游戏和 Lunar 启动器。
2. 打开 Finder，按 `Command + Shift + G`，输入：
   ```
   ~/.lunarclient/settings/
   ```
3. 复制一份 `launcher.json` 作为备份，再用纯文本编辑器打开原文件。
4. 找到已有的 `"settings"` 对象，在其中添加 `"enableAttach": true`。若该项已经存在，将其值改为 `true`。
5. 保存文件，重新打开 Lunar 启动器，启动 **1.8.9 Forge** 游戏，再使用注入器。

以下仅展示字段所在位置，**不要用它覆盖整个文件**；保留原有设置，并注意相邻字段之间的逗号：

```json
{
  "settings": {
    "enableAttach": true
  }
}
```

已在 Lunar 启动器 `3.7.22-ow` 上验证。修改配置不会影响已经运行的游戏，必须重新启动游戏。如果启动器界面直接提供 Enable Attach 开关，也可直接开启。

## 从源码构建

源码目录：`forge/src`、`forge/injector-src`、`lunar/src`、`lunar/injector-src`、`common/config-export-src`。

使用 JDK 17，可通过 `JAVA_HOME` 指定其位置，然后执行对应命令：

```bash
python3 tools/build.py forge
python3 tools/build.py lunar
```

游戏载荷输出 Java 8 字节码，注入器使用 Java 17，生成的 App 自带运行时。
