# Google Tasks 设置

应用实现了 Google Play services AuthorizationClient 原生授权及 Tasks REST API。无需服务器、服务账号、网页客户端密钥或把 Google 密码交给应用。首次登录需要完成你自己的 Google Cloud 配置。

## Google Cloud

1. 打开 [Google Cloud Console](https://console.cloud.google.com/)，选择或创建自己的项目。
2. 在 API Library 启用 **Google Tasks API**（tasks.googleapis.com）。
3. 打开 Google Auth Platform，配置 Branding：应用名 ScheduleBoard、你的支持邮箱与联系邮箱。Audience 通常选择 External；开发阶段使用 Testing，并将准备登录的 Google 账号加入 Test users。
4. 在 Data Access 添加 `https://www.googleapis.com/auth/tasks`。这允许读取和修改任务；应用不请求 Gmail、Drive 或 Calendar 权限。
5. 在 Clients 创建 **Android** OAuth 客户端，名称例如 ScheduleBoard Android。包名填写 `com.doublesail.scheduleboard`，SHA-1 填写下面的固定签名：

   `28:EC:E2:9B:CB:98:15:E8:E3:05:3A:9B:15:D1:5D:F0:D8:0F:54:50`

6. 保存后，在手机点击大时钟 → Google Tasks 账号与同步 → 使用 Google 账号登录。在系统账号选择器中选择或添加自己的账号，允许 Tasks 权限，然后选择清单。

此方式依据 Android 包名和证书登记客户端，不需要把 client_secret 或 client JSON 放进源码。另行自行签名构建的 APK 应登记其自己的 SHA-1；手机设置页的“查看 Google Cloud 注册信息”显示当前安装 APK 的实际证书。换签名不能直接覆盖现有安装。

手机需要可用的 Google Play 服务和能访问 Google 的网络。没有 GApps 的类原生系统需要先由用户安装适配系统的 Google 服务；应用不会自行修改系统。

Testing 状态的授权存在 Google 的测试用户及有效期限制（通常 7 天后需重新授权）；长期个人使用可参考 Google 的 Production 与 personal-use exception 说明调整项目发布状态。公开面向其他用户分发时按 Google 对品牌、权限范围和验证的要求办理，不能保证授权永不过期。应用会自动续取普通短期令牌，但撤销授权、账号移除或 Google 要求再次同意时必须由用户确认。

## 同步行为

- 连接后显示所选账号、所选清单。本机旧待办独立保留，不自动上传给 Google；断开后重新显示。
- 支持双向新增、重命名、完成/取消完成与删除。点击勾选，长按编辑；标题最多 200 字。云端长标题可显示，修改时采用应用的 200 字输入限制。
- 编辑先写入本机 DataStore，再尝试联网。未上传修改重启后保留；前台约每分钟检查，后台 WorkManager 约 15 分钟（受系统调度影响）。设置中可立即同步。
- 读取全部分页，包括已完成、隐藏和删除标记；只有完整拉取成功后替换缓存。隐藏或删除的任务不会显示。
- PATCH 只发送本机修改的标题或状态，保留 Google 端的备注、日期、层级等其他字段。同一字段冲突时仍待上传的本机操作生效；Google 端删除优先，不复活已删除任务。同步中的新修改不会被旧请求应答清掉。
- 为避免“Google 已添加成功但网络应答丢失”后重试出现重复，新建云端任务的备注会包含 `[ScheduleBoard:随机标识]`。它只用于查找该次新增；原有 Google 任务的备注不会改写。不要在一次尚未完成的同步中删除这个标记。
- 切换账号、切换清单或断开前必须先上传修改，防止待上传内容进入另一个账号。需要重新授权时仍能授权当前账号。
- 删除会同步到 Google；删除父任务会连同其子任务一起删除，界面会要求确认。“清理已完成”同样执行删除并要求确认。任务层级/排序、备注、日期、重复规则的编辑不在本版范围，已有这些信息通过局部更新保留。
- 断开可仅清除本机下载数据，或同时撤销 Tasks 授权；都不删除 Google 端清单和系统账号。应用禁用系统备份；OAuth 访问令牌只留在内存，由 Google Play 服务管理续取。

## 验证自己的账号

1. 在 Google Tasks 网页/官方 App 新增一项 → 日程牌立即同步后出现。
2. 在日程牌新增、长按重命名、点击完成 → Google Tasks 中核对。
3. 断网，在日程牌新增/完成；重新启动应用确认保留，再联网同步，核对无重复。
4. 在 Google Tasks 修改/取消完成/删除 → 日程牌同步后一致。
5. 用专门的测试任务验证删除；已有真实任务不应被测试操作改动。

自动测试使用本地 HTTP 服务与假的云端数据验证 API 和同步队列，不能代替你在已配置项目下完成真实 Google 账号授权。

依据：[Android 授权](https://developer.android.com/identity/authorization)、[Tasks API](https://developers.google.com/workspace/tasks/reference/rest)、[OAuth 应用状态](https://developers.google.com/identity/protocols/oauth2/production-readiness/overview)、[个人使用验证例外](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification)。
