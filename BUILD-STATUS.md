# 0.3.0 验证记录

日期：2026-10-23（北京时间）。包名 com.doublesail.scheduleboard；versionCode 5；最低 API 24，目标 API 36。

## 本地验证

- assembleDebug、assembleDebugAndroidTest、assembleRelease（R8）成功。
- 已审查并整合 GitHub PR #1（9bc5b6a）的 ICS 折行性能、后台缓存解码和教务续登会话及时保存，保留其 7 项新增回归测试与历史分析记录。
- Android 主机测试 92 项通过，0 失败、0 错误。包括 Google Tasks 分页、局部 PATCH、拒绝携带令牌跳转、离线队列持久化、丢失新增应答去重、同步中继续编辑、401 续取令牌、撤销授权后保留修改、账号隔离、云端删除优先等。
- 番茄钟测试覆盖暂停/恢复、同次开机调整墙钟、整机重启后重新绑定单调时钟、完成只计数一次、第四次专注后长休息、自定义时长。
- 实际原生 Compose / View / Canvas 渲染：主页浅色与深色、番茄钟浅色与深色，均为 1280×720。已人工检查。下滑打开、待办区域滚动隔离及暂停/继续操作通过 Compose 测试。
- Python 发布规则及发布器测试 12 项通过：版本递增、更新说明、拒绝覆盖正式 Release、拒绝别的提交草稿、草稿上传后验证大小/摘要才发布、同提交草稿恢复。
- lintDebug 与 lintRelease：0 错误，分别 31 / 31 警告（另有提示项），没有为了通过检查禁用规则。
- Release APK v2 签名验证通过；证书 SHA-256 与旧安装使用的固定证书一致。发布包 debuggable 关闭，可作为同签名旧版本的覆盖更新。

## 外部配置和未验证范围

- 本轮 ADB 未检测到设备，未安装到手机、未执行本轮实机集成测试。旧版本教务真实登录/续登记录见 0.2.2 历史；不能把历史结果当作本轮验证。
- Google Tasks 已实现实际原生授权和 REST 同步，自动测试使用测试数据和本地模拟 HTTP 服务；尚未使用用户的真实 Google 账号做端到端验证。首次使用必须在用户自己的 Google Cloud 项目启用 Tasks API、配置同包名/签名 Android OAuth 客户端，手机需 Google Play 服务和可达 Google 的网络，详见 docs/GOOGLE-TASKS.md。
- 番茄钟真实设备的后台结束提醒、整机重启恢复及通知声音尚待实测；前台原生交互与计时状态逻辑已测。未允许精确闹钟时后台提醒可能延迟，通知关闭/强行停止会限制系统提醒。
- GitHub Actions 已配置“验证 → 固定签名构建 → 校验证书与附件 → 发布 Release”。仓库需要一次性保存 ANDROID_SIGNING_KEYSTORE_BASE64；缺少 Secret 会停止发布，不会使用随机密钥。在线执行结果以对应提交的 Actions 和 Release 附件为准；配置文件或本地 APK 不等同于已经发布。
- 所有后续应用更新递增 versionCode/versionName 并通过 GitHub 构建发布的要求已写入 AGENTS.md。签名密钥、OAuth 令牌、用户账号密码及真实任务数据均不提交。

构建：JDK 21 / Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21 / Compose BOM 2025.12.00 / Material 3 1.4.0 / Google Play services Auth 22.0.0。
