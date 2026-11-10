# 0.4.0 验证记录

日期：2026-11-10（北京时间）。包名 com.doublesail.scheduleboard；versionCode 6；最低 API 24，目标 API 36。基于 GitHub main 的 4e3925e 开发。

- 本地 assembleDebug、assembleDebugAndroidTest、固定签名 assembleRelease（R8）、lintDebug 与 lintRelease 成功。
- 108 项 Android 主机测试通过，0 失败、0 错误；13 项 Python 发布脚本测试通过。
- 新增 12 项纯逻辑课表休屏测试：10/5 分钟边界、20 分钟合并边界（含秒数）、整上午课间、午间分段、重复/重叠/乱序课程、凌晨优先级、当地时区、全天/非课程排除、短课、空课表和跨午夜课程。
- 原生窗口测试验证课时和夜间为最低亮度、正常模式恢复系统亮度、常亮开关不被亮度规则篡改。
- 官方 Compose Pager 交互测试验证下拉打开/上推返回、待办列表上下滚动隔离、手指尚未松开时页面连续移动、短慢拖动松手回弹。原生截图确认半途拖动时两页均可见；休屏纯黑且可点击临时查看。
- 已检查原生 1280×720 浅/深色主页、浅/深色番茄钟、半途拖动及纯黑休屏渲染。仅使用测试数据。
- Debug 与 Release Lint 均为 0 错误、31 警告、1 提示；未禁用检查。
- 本地 Release APK 的 v2 签名和固定证书检查通过，版本信息为 0.4.0 / 6。分发使用 GitHub Actions 生成的 APK；[工作流](https://github.com/Doublesail-A/ScheduleBoard/actions/workflows/android.yml)与 [v0.4.0 Release](https://github.com/Doublesail-A/ScheduleBoard/releases/tag/v0.4.0)为正式发布状态入口。

本轮 ADB 没有检测到设备，未进行实机安装、环境光传感器测试或跨真实课时/凌晨长时间运行测试。XZ1 Compact 为 LCD：纯黑界面不等于物理关闭背光，窗口亮度 0 请求的是设备允许的最低背光。本方案维持应用前台和默认常亮，不依赖锁屏后的后台唤醒；手动关闭常亮/电源键关屏、离开应用后由系统控制。系统自动亮度须已开启；设置页提供一次性特殊访问授权入口，授权后启用系统自动模式，课时和夜间仅覆盖应用窗口亮度。规则按缓存课表和本机当地时间计算，分钟/日期/时间/时区变化及课表更新后重新判断。

---

# 0.3.0 历史验证记录

日期：2026-10-23（北京时间）。包名 com.doublesail.scheduleboard；versionCode 5；最低 API 24，目标 API 36。

## 本地验证

- assembleDebug、assembleDebugAndroidTest、assembleRelease（R8）成功。
- 已审查并整合 GitHub PR #1（9bc5b6a）的 ICS 折行性能、后台缓存解码和教务续登会话及时保存，保留其 7 项新增回归测试与历史分析记录。
- Android 主机测试 92 项通过，0 失败、0 错误。包括 Google Tasks 分页、局部 PATCH、拒绝携带令牌跳转、离线队列持久化、丢失新增应答去重、同步中继续编辑、401 续取令牌、撤销授权后保留修改、账号隔离、云端删除优先等。
- 番茄钟测试覆盖暂停/恢复、同次开机调整墙钟、整机重启后重新绑定单调时钟、完成只计数一次、第四次专注后长休息、自定义时长。
- 实际原生 Compose / View / Canvas 渲染：主页浅色与深色、番茄钟浅色与深色，均为 1280×720。已人工检查。下滑打开、待办区域滚动隔离及暂停/继续操作通过 Compose 测试。
- Python 发布规则及发布器测试 13 项通过：版本递增、更新说明、拒绝覆盖正式 Release、拒绝别的提交草稿、草稿上传后验证大小/摘要才发布、同提交草稿恢复，以及 GitHub 新草稿读取短暂延迟时的有界重试。
- lintDebug 与 lintRelease：0 错误，分别 31 / 31 警告（另有提示项），没有为了通过检查禁用规则。
- Release APK v2 签名验证通过；证书 SHA-256 与旧安装使用的固定证书一致。发布包 debuggable 关闭，可作为同签名旧版本的覆盖更新。

## GitHub 正式发布

- [v0.3.0](https://github.com/Doublesail-A/ScheduleBoard/releases/tag/v0.3.0) 已于 2026-10-23 由 GitHub Actions 构建和发布，来源提交 357ad93113773afacb44e5bb81ebed099c00c758；[对应工作流](https://github.com/Doublesail-A/ScheduleBoard/actions/runs/39826445396)的验证与签名发布任务均成功。
- 仓库固定签名 Secret 已由用户配置。正式 Release 附有 ScheduleBoard-0.3.0.apk、RELEASE-NOTES-0.3.0.md、SIGNATURE.txt 和 SHA256SUMS.txt。
- 已重新下载 GitHub 发布的 APK，核对 SHA-256 为 efd053c174efb742e45edc82af4a5f9c69f2811f9b4cab35fc1b839e43f58a17，与 Release 附件校验一致；APK v2 签名及固定证书验证通过。
- 首次签名运行在新建草稿后立即查询列表，遭遇读取延迟而安全停止，未发布空附件 Release。同提交草稿恢复后正式发布成功；发布器补充有界读取重试及回归用例，防止后续版本再次因该时序问题中断。

## 外部配置和未验证范围

- 本轮 ADB 未检测到设备，未安装到手机、未执行本轮实机集成测试。旧版本教务真实登录/续登记录见 0.2.2 历史；不能把历史结果当作本轮验证。
- 用户自己的 Google Cloud 项目已启用 Tasks API、创建匹配包名/固定签名的 Android OAuth 客户端，并加入当前账号为测试用户、保存 Tasks 读写范围。当前为外部测试模式，尚未发布 OAuth 生产应用。自动测试使用测试数据和本地模拟 HTTP 服务；真实手机 Google 账号授权和双向同步结果仍待用户验证。手机需 Google Play 服务和可达 Google 的网络，详见 docs/GOOGLE-TASKS.md。
- 番茄钟真实设备的后台结束提醒、整机重启恢复及通知声音尚待实测；前台原生交互与计时状态逻辑已测。未允许精确闹钟时后台提醒可能延迟，通知关闭/强行停止会限制系统提醒。
- GitHub Actions 已实际完成“验证 → 固定签名构建 → 校验证书与附件 → 发布 Release”。后续应用更新递增版本后自动发布；仅修改文档或发布脚本且应用内容不变时跳过重复发布，保留已发布 APK。
- 所有后续应用更新递增 versionCode/versionName 并通过 GitHub 构建发布的要求已写入 AGENTS.md。签名密钥、OAuth 令牌、用户账号密码及真实任务数据均不提交。

构建：JDK 21 / Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21 / Compose BOM 2025.12.00 / Material 3 1.4.0 / Google Play services Auth 22.0.0。
