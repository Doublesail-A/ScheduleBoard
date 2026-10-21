# 0.2.1 验证记录

日期：2026-10-21（北京时间）。包名 com.doublesail.scheduleboard；versionCode 3；Debug 签名。

已完成：

- assembleDebug / assembleDebugAndroidTest：成功。
- 主机单元、持久化和原生 Compose 测试：50 项通过，0 失败、0 错误。含仅学号/密码的登录表单、自动验证码重试上限、错误密码不重试、当前学期读取、过期会话续登、课表解析、ICS、待办与原生布局。
- lintDebug：0 错误、20 警告。
- apksigner：APK v1 / v2 签名验证通过。
- 已通过 ADB 覆盖安装到 Sony Xperia XZ1 Compact / G8441、Android 12，保留原应用数据。
- 用户在手机上输入真实账号，已确认登录并显示课表；真实密码未导出。
- 实机 3 项集成测试通过：真实教务页面和验证码获取及本地识别；Android Keystore 加密读写；使用本机已加密登录信息对新的未认证会话自动续登，再成功同步课表。
- 实机发现系统限制联网模式阻止应用联网。经用户明确确认，将 restricted_networking_mode 从 1 改为 0；网络测试随即通过。应用本身不修改该策略。

边界：

- 15 分钟同步受 WorkManager、网络和系统调度影响；尚未做长时间后台或整夜稳定性测试。
- 尚未实测整机重启后的默认桌面 / Magisk 启动；前台常亮和沉浸式全屏沿用已有实现。
- 教务沿用 iFAFU 的 HTTP 入口；仅向学校域名发送凭据。密码和会话使用本机 Keystore 加密，退出教务后删除，不进入系统备份。
- 当前使用本部作息；旧版保存的旗山设置仍兼容。缺少已核对校历时保留缓存并提示。
- Google Tasks / CalDAV 尚未连接，待办为本机存储。
- GitHub Actions 配置已加入仓库，在线执行结果以 Actions 页面为准。CI 使用临时 Debug 签名，与本机测试包可能不同。

构建：JDK 21 / Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21 / SDK 36 / Build Tools 35.0.0。

使用与来源见 README.md、THIRD-PARTY-NOTICES.md；版本变化见 CHANGELOG.md。
