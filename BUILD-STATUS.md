# 0.2.0 验证记录

日期：2026-10-21（北京时间）

包名：com.doublesail.scheduleboard；versionCode 2；Debug 签名。

已完成：

- assembleDebug：成功。
- testDebugUnitTest（启用 renderBoard）：43 项通过，0 失败、0 错误。包含 39 项逻辑测试、2 项持久化测试、2 项原生 Compose 渲染/交互测试。
- lintDebug：通过，0 错误，19 警告。完整报告位于同级目录上一级的 ScheduleBoard-0.2.0-Lint.html。
- apksigner verify：通过，APK v1 / v2 签名有效。
- 1280×720 浅色与深色图：Android 12 / API 31 的 Robolectric 原生图形 View / Canvas 渲染，已逐图检查。两行中文待办布局和点击完成经过测试。
- DataStore：重复导入不累加；无效导入保留旧数据；待办完成状态可跨仓库实例读取，清理仅移除已完成项。
- 教务：模拟响应验证手动验证码、隐藏表单、学期切换、错误学期保护、会话失效；课程验证单双周、rowspan、调停课、校历假期与调课、本部/旗山作息。

未完成的外部验证：

- 没有真实教务账号登录验证，没有验证学校当前网络可达性、会话过期策略或新版页面变化。需安装后在手机上登录测试。
- 没有物理 XZ1c 测试；动态壁纸色板、长时间常亮、实际后台同步、Keystore 重启解密、默认桌面/Magisk 重启仍需实机确认。
- 预览使用设计样本数据，不是同步自真实账号。APK 不包含这些演示数据。
- Google Tasks / CalDAV 尚未连接，当前为本机待办。
- 自动审批拒绝了包含读取参考代码、探测教务地址、在独立测试模拟器安装及启动 APK 的组合命令，只返回 blocked by policy，没有更具体原因。未重试安装；改用主机原生渲染与持久化测试。新建的测试模拟器已关闭。

已知运行边界：

- 自动同步使用已保存的学校会话，不保存密码，不自动识别验证码；会话失效需要重新登录。
- WorkManager 的 15 分钟为请求间隔，实际可因网络、系统调度而延迟。
- 教务服务沿用源项目的 HTTP 地址；仅学校域名允许明文连接，HTTPS 不跳过证书验证。
- 未找到所选学期校历时不估算开学日期，需要手动设置，未公布假期不能推断。
- 固定横屏按 Android 12 设备需求设置；Android 16 的部分大屏窗口会忽略固定方向。

构建：JDK 21 / Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21 / SDK 36 / Build Tools 35.0.0。Compose BOM 2025.12.00、Material 3 1.4.0、WorkManager 2.10.1、jsoup 1.18.3。

安装与使用见 README.md；来源见 THIRD-PARTY-NOTICES.md。
