# 固定的第三方 compile-only API

这些 11 个 JAR 保持原 Maven 坐标与版本，不进入 Orryx 运行包。原仓库 maven.mcwar.cn 的证书过期使正常完整构建失败，因此将准确版本固化到项目中。`dependencies.lock.json` 记录坐标、来源、字节数和 SHA-256；随附原 POM 用于核对依赖。RedisChannel 公开签名涉及的 Lettuce 6.8.0.RELEASE 仍从 Maven Central 正常解析。

`verifyCompileOnlyApis` 在源码编译与 check 前验证本地文件 SHA-256。缺失或内容变化会明确失败，不静默降级、替换版本或下载未知 JAR。更新 API 时须核对原作者版本、更新锁文件并完成兼容检查；不要将这些 API 安装进 plugins。

来源说明（2026-10-27）：恢复下载保留了原仓库的证书链及域名校验，但因上游证书过期，对证书有效日期使用了一次性例外。记录的 SHA-256 用于固定本次恢复的字节，并不代表独立的上游签名认证。正常 Gradle 配置不包含这一例外，不关闭全局 TLS 校验，也不再请求该失效仓库。未添加自动不安全回退下载器。

Gradle Wrapper 更新为 8.14.5 补丁版本，并固定 downloads.gradle.org 提供的发行包 SHA-256。使用 Java 17 启动 Gradle，Orryx 原有 Java 8 输出及 Kotlin Java 8 toolchain 保持不变。

验证命令：`gradlew.bat --no-daemon build`。它包含完整编译、测试、动作语法快照及可选 DMZ API 打包隔离检查，不使用增量 QA 覆盖包。
