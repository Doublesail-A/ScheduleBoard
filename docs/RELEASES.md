# GitHub 构建和发布

每次应用更新都递增 app/build.gradle.kts 中的 versionCode 和 versionName，并更新 CHANGELOG.md；提交 main 后由 Android build and release 工作流验证、签名、发布 GitHub Release。下载 Assets 内的 `ScheduleBoard-版本号.apk` 覆盖安装；附带 SHA256SUMS.txt 和 SIGNATURE.txt。

PR 只运行验证，不读取签名 Secret、不发布。main、匹配版本号的标签和手动重跑可发布。签名和发布只发生在测试与 Lint 通过之后；版本不得倒退、标签不得指向别的提交、已发布二进制不得覆盖。同版本发布后继续改应用代码会失败，需递增版本；文档修改可跳过重复发布。

## 一次性设置签名 Secret

为了覆盖之前已安装的测试版，本项目沿用该用户现有的固定证书；虽然证书别名为 androiddebugkey，Release 构建仍关闭 debuggable 并运行 R8。私钥是本机单独生成的密钥，不是提交在源码中的公共测试密钥。

证书 SHA-256：`F7:A1:EB:56:D6:14:87:24:D8:5C:F4:E3:14:72:76:E3:44:42:CD:CB:8E:E2:A4:84:22:40:B7:1A:08:EA:FD:C8`。

1. 仅在持有这份原密钥的电脑，将 `%USERPROFILE%\.android\debug.keystore` 文件编码为 Base64。不要生成新的同名密钥，不要把编码结果发到聊天或公开仓库。
2. 打开仓库 Settings → Secrets and variables → Actions → New repository secret。
3. Name 填 `ANDROID_SIGNING_KEYSTORE_BASE64`；Secret 填原密钥文件的 Base64 内容。保存在仓库的 Actions Secret 中。
4. Actions → Android build and release → Run workflow，选择 main。或者在首轮因缺 Secret 失败后重新运行失败的 release job。

CI 只在临时目录解码签名密钥，验证 APK 的 SHA-256 证书指纹匹配后才上传。密钥不进入源码、构建日志、Actions artifact 或 Release；结束时清理临时文件。Secret 未配置时发布会明确失败，不会换成随机签名包。

本项目固定密钥当前使用原 Android 调试密钥格式的口令和别名。若将来更换私钥/口令，应同时设计 Android 签名迁移、更新 GitHub Secrets 配置、证书校验和 Google OAuth 客户端，不可直接替换导致用户数据或升级失效。

## 本机构建签名 Release

PowerShell：

```powershell
$env:SCHEDULEBOARD_KEYSTORE = Join-Path $env:USERPROFILE '.android/debug.keystore'
.\gradlew.bat -PrenderBoard assembleDebug testDebugUnitTest lintDebug assembleRelease lintRelease
```

只在开发机器确实持有上述固定密钥时使用。下载与更新以 GitHub Actions 的 Release 为准，不用本地文件冒充 GitHub 构建结果。
