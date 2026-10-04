# 单 app 分发清单（证据索引）

本清单依据根目录 `LICENSE`、`README.md`、`各版本 openvape-macos-agent.jar` 和 `build/macos/deps/*.jar` 的归档内容整理，不作许可解释或再分发权结论。根 README 明确指出，CC0 仅覆盖贡献者有权处置的内容，第三方库、商标、字体、纹理等仍受各自权利约束。

候选载荷中可确认的 Java 库：

- **Gson 2.10.1**：载荷含 217 个 `com/google/gson/` 类及 `META-INF/maven/com.google.code.gson/gson/pom.xml`，后者标注 Apache-2.0。依赖包 `build/macos/deps/gson-2.10.1.jar` 没有独立 `META-INF/LICENSE` 或 `NOTICE` 文件；候选载荷有 `META-INF/LICENSE.txt`，但没有 Gson 单独的 NOTICE。
- **Guava 17.0**：载荷含 1,683 个 `com/google/common/` 类及 Maven 元数据。`build/macos/deps/guava-17.0.jar` 的 manifest `Bundle-License` 指向 Apache License 2.0；依赖包内没有独立 LICENSE/NOTICE 文件。
- **Apache Commons IO 2.4**：载荷含 `org/apache/commons/io/` 类。依赖包有 `META-INF/LICENSE.txt` 和 `META-INF/NOTICE.txt`；候选载荷也有同名文件，NOTICE 标明 Apache Commons IO 与 ASF 版权。该 NOTICE 只点名 Commons IO。
- **Netty all-in-one 4.0.23.Final**：载荷含 `io/netty/` 类及 `META-INF/maven/io.netty/netty-all/pom.xml`。依赖 JAR manifest 未见 `Bundle-License`，JAR 内无 LICENSE/NOTICE；候选载荷唯一 NOTICE 点名 Commons IO。现有这些文件未提供明确的 Netty 许可声明。
- **Javassist 3.29.2-GA**：载荷含 426 个重定位到 `gg/vape/shaded/javassist/` 的类，以及 `META-INF/maven/org.javassist/javassist/pom.xml`。本地依赖 JAR manifest 和 POM 列有 MPL 1.1、LGPL 2.1、Apache License 2.0 许可选项；依赖 JAR 内没有独立 LICENSE/NOTICE 文件。
- **ASM 9.7.1**：载荷含 143 个重定位到 `gg/vape/shaded/org/objectweb/asm/` 的类。`build/macos/deps/asm-9.7.1.jar`、`asm-analysis`、`asm-commons`、`asm-tree`、`asm-util` 五个 manifest 均标示 BSD-3-Clause；这些依赖 JAR 没有独立 LICENSE/NOTICE 文件。

依赖目录还包含 JetBrains annotations 24.1.0、LWJGL 与 lwjgl_util 2.9.4 nightly。候选载荷中未找到 `org/jetbrains/annotations/` 或 `org/lwjgl/` 类，故不把它们列为已打入载荷的库。ASM 与 Javassist 已重定位，原始命名空间不存在不能作为未打包的证据。

载荷内字体资源为 `resources/noto.ttf`、`resources/poppins_rg.ttf`、`resources/proxima.ttf`、`resources/proximabd.ttf`。候选载荷没有逐字体的许可或来源说明；根 LICENSE 的 CC0 声明本身不能证明这些字体的来源或再分发权。载荷另含 206 个 `resources/textures/` 文件，本清单未逐项识别其来源或许可。

载荷自身包含 `META-INF/LICENSE.txt`（Apache License 2.0 全文）和 `META-INF/NOTICE.txt`（Commons IO notice）。依赖证据的本地副本按库名放在 `third-party-notices/`；其中保留了上游 JAR 内现有 LICENSE/NOTICE、POM 和/或 manifest。对未包含 LICENSE/NOTICE 的依赖，该目录的 `EXTRACTION.txt` 仅记录本地归档内的缺失情况。此处只记录文件内容与归档位置；不能据此推断一个通用 LICENSE 覆盖所有组件或资源。

当前候选 app 的主可执行文件 `App 主执行文件` 经 `file` 识别为 **Mach-O 64-bit arm64**，即 Apple Silicon 构建；本产物不保证支持 Intel Mac。
