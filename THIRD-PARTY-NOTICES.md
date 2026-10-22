# 来源与依赖

课表协议、节次时间和校历语义参考：

- 原项目 [woolsen/iFAFU](https://github.com/woolsen/iFAFU)，原作者 woolsen 及贡献者。
- 用户提供的维护版本 [Doublesail-A/iFAFU](https://github.com/Doublesail-A/iFAFU)，参考分支 codex/material-you-redesign，提交 fdddcafee6459eebde6a7102abbcfc4327bc6be7。
- app/src/main/assets/school-calendars.json 来自该提交的 data/school-calendars.json，包含各学期学校原始校历来源 URL。运行时按同一维护分支更新，不把未公布的日程当作已确认安排。
- 参考其旧版正方 Table1/DBGrid 格式、单双周、教务调停换补课、周日周起点，以及本部/旗山作息。此应用自行实现 HTTP 客户端、解析和日期展开；不依赖第三方课表解析服务，不复制其登录数据库或私有账号数据。

验证码识别：

- app/src/main/assets/theta.dat 直接来自上述 iFAFU 参考提交的同名资源。SchoolCaptchaRecognizer.kt 按 VerifyParser.java 的灰度、分割和 34 类线性分类流程改写为 Kotlin；省略不影响分类结果的 sigmoid 运算。
- 来源：[VerifyParser.java](https://github.com/Doublesail-A/iFAFU/blob/fdddcafee6459eebde6a7102abbcfc4327bc6be7/app/src/main/java/cn/ifafu/ifafu/service/parser/VerifyParser.java)、[theta.dat](https://github.com/Doublesail-A/iFAFU/blob/fdddcafee6459eebde6a7102abbcfc4327bc6be7/app/src/main/assets/theta.dat)，原项目 woolsen/iFAFU 及贡献者。
- 参考快照未包含根目录许可证；保留来源归属，不对这些参考资源另行宣称许可或所有权。

界面基于用户提供的设计图实现为原生 Compose；预览中的课程、姓名、地点及待办仅用于匹配设计样本。

运行依赖：AndroidX / Jetpack Compose / Material 3 / WorkManager（Apache-2.0），Kotlin / kotlinx.coroutines（Apache-2.0），jsoup（MIT）。官方项目及许可说明：[AndroidX](https://android.googlesource.com/platform/frameworks/support/)、[Kotlin](https://github.com/JetBrains/kotlin)、[jsoup](https://github.com/jhy/jsoup/blob/jsoup-1.18.3/LICENSE)。

测试依赖：JUnit、Robolectric 及 AndroidX Test。Robolectric 仅用于可选的主机测试，不随 APK 打包。预览图为原生 View / Canvas 渲染，不冒充设备截图。

0.3.0 新增：Google Play services Auth 22.0.0（Google 官方授权 SDK，遵循其适用的 Google SDK/开发者条款）；OkHttp 4.12.0 及 Okio（Apache-2.0）。授权由系统 Google 服务处理；REST 协议依据 [Google Tasks 官方文档](https://developers.google.com/workspace/tasks/reference/rest)，网络库来源与许可见 [OkHttp](https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt)、[Okio](https://github.com/square/okio)。MockWebServer 仅为测试依赖，不打包进 APK。
