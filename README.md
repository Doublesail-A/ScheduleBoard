# 日程牌 · 0.2.0

为 Sony Xperia XZ1 Compact（Android 12、1280×720、16:9）准备的横屏常亮日程牌。界面按提供的布局实现：左侧时钟与接下来两项安排，右侧待办。

## 安装后使用

1. 安装 ScheduleBoard-0.2.0-debug.apk；可覆盖同签名的 0.1.0 测试版。
2. 点击大时钟打开设置，选择“登录福建农林大学教务”。
3. 输入学号、密码、验证码，选择学年、学期和校区，点击“登录并同步课表”。
4. 右下角“＋”添加本机待办；点击待办整行可完成或取消完成。
5. 点击课程卡片查看完整名称、时间、教室和教师。长标题会截断，待办最多显示两行，可滚动查看更多。

没有预装演示课表或示例待办。预览图中的内容仅存在于渲染测试。

## 教务同步

参考 [Doublesail-A/iFAFU](https://github.com/Doublesail-A/iFAFU)，直接连接福建农林大学旧版正方教务 jwgl.fafu.edu.cn；不会把账号密码发送给第三方解析服务器。当前只实现 FAFU 本部和旗山的作息选择，没有实现原项目的金山学院入口、成绩或考试查询。

- 原生 MD3 登录表单，手动填写验证码；密码仅用于本次请求，不保存。
- 会话、学号和姓名通过 Android Keystore AES-GCM 加密保存在本机。
- 登录成功后，使用 AndroidX WorkManager 在有网络时约每 15 分钟同步。系统可能延迟后台任务，不保证精确到分钟；重新进入应用时也检查是否需要更新。
- 会话有效期间自动同步；过期或服务器要求重新登录时停止无效重试并提示用户。没有实现绕过验证码或自动重新输入密码。
- 同步失败、解析失败、学期不匹配时保留原课表；显示离线或重新登录提示。
- 教务解析支持单双周、跨多行表格、调课、停课、换课和补课；将课程展开为具体日期及真实上课时间。
- 本部与旗山使用 iFAFU 中的两套节次时间，每节 45 分钟。
- 校历使用 iFAFU 校历数据：包含开学第一周、假期和补班调课；内置数据可离线使用，并尝试每天更新。校历来源和参考版本见 THIRD-PARTY-NOTICES.md。
- 缺少所选学期校历时，需要填写第一周日期；手动日期不会推断未公布的假期，需自行核对。
- “退出教务”会删除会话并停止自动同步，保留已缓存课表。

旧版学校教务入口使用 HTTP，与所给项目相同。登录页会明确显示连接方式；建议在可信网络中使用。应用只对该学校域名放开明文连接，不忽略 HTTPS 证书错误。

仍需在你的手机上完成真实账号登录与学校网络的验证。本地测试使用模拟响应，没有使用真实学号或密码。

## ICS 备用导入

未登录教务时，可在设置中导入 iFAFU 导出的完整 ICS。已开启教务同步时先退出教务，避免自动同步覆盖文件数据。

解析支持 UTF-8 折行、文本转义、UTC、TZID、本地时间、全天事件和稳定 UID。相同 UID 去重，冲突 UID 或不完整文件会拒绝导入并保留旧数据。单个文件最多 4 MB / 10,000 项。当前针对 iFAFU 已展开的 VEVENT；含 RRULE/RDATE/EXDATE 等重复规则的文件会明确拒绝，不会静默遗漏课程。

## 待办与 Google Tasks

本版待办为本机 DataStore 存储：添加、完成/取消完成、清理已完成，重启后保留。已完成项目移至列表后部，列表可滚动。

Google Tasks 尚未接入云端。已建立独立 TaskRepository，待授权方式和后端确定后可以替换数据源。若使用 Google Tasks，手机端勾选完成需要 tasks 写入授权；Android 原生 Google 授权还需配置 Cloud 项目、包名和 APK 签名 SHA-1。Google Play 服务是否可用尚待确认。依据：[Tasks 授权范围](https://developers.google.com/workspace/tasks/auth)、[Android 授权设置](https://developer.android.com/identity/authorization)。

若更偏好开放协议，可考虑 CalDAV / Nextcloud Tasks，Tasks.org 可作为其他设备上的客户端。此方向也尚未集成到日程牌。参考：[Tasks.org 同步](https://tasks.org/docs/sync/)、[Nextcloud Tasks](https://github.com/nextcloud/tasks)。

## 显示与开机启动

- Kotlin、Jetpack Compose、官方 AndroidX Material 3；Android 12+ 使用系统壁纸动态取色。可在设置中关闭，改用图示绿色主题。
- 跟随系统 / 浅色 / 深色；前台 FLAG_KEEP_SCREEN_ON 常亮，切到后台后允许系统正常息屏。
- 沉浸式全屏，边缘滑动临时显示系统栏。横屏支持两个方向。
- 系统分钟广播更新时间、课程状态和倒计时；跨日或调整时区立即刷新。
- 当前课程保留到实际结束；当天无后续课程时显示下一天真实安排，并标明日期。

默认桌面方式：设置 → 设为默认桌面。系统进入桌面后显示日程牌；系统锁屏仍需正常解锁。设置页可以切回其他默认桌面。

Magisk 方式：可继续使用先前的 ScheduleBoard-Magisk-0.1.0.zip，在 Magisk 中安装。它只在开机完成、主用户解锁后启动应用一次，不改锁屏、不反复抢前台。在 Magisk 中禁用即可停止。模块与本版包名、Activity 入口兼容。

“开机启动辅助”只控制开机广播后的启动或提示；默认桌面和 Magisk 模块分别独立生效。Android 10+ 普通应用不能保证从开机广播直接弹出界面，未选这两种方式时回退为通知。

## 构建与验证

需要 JDK 17+、SDK Platform 36、Build Tools 35.0.0。最低 API 23，主要适配并按用户设备目标验证 Android 12。Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Compose BOM 2025.12.00 / Material 3 1.4.0。

常规编译和单元测试：

    .\gradlew.bat assembleDebug testDebugUnitTest lintDebug

主机原生 Compose 渲染与持久化测试（额外下载 Robolectric，仅测试依赖）：

    .\gradlew.bat -PrenderBoard testDebugUnitTest

渲染图在 app/build/reports/board-render/。这些是 Android 12 Robolectric 原生 View / Canvas 渲染，使用测试数据；不是实机或模拟器截图。测试数据不会加入 APK。

构建详情、检查结果与未验证边界见 BUILD-STATUS.md。本版是 Debug 签名测试 APK；正式发布请配置自己的签名。
