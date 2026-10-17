# 今日花费 · Daily Spending

面向小米 13 的本地通知记账 Android 原型：识别微信、支付宝付款、转出、发红包与退款，统计每日净额。

## 安装

本仓库已按用户要求设为私有。Release 下载需 GitHub 登录；免登录应用内更新暂不可用，可从聊天提供的 APK 覆盖安装。

Android 8 及以上。0.4 起提供“检查更新”：确认下载后校验文件、版本、包名和签名，再打开系统安装器。必须由用户确认安装，不能静默更新。0.3 及以前需要先手动覆盖安装 0.4 一次。覆盖安装会保留数据库；不要卸载旧应用。

## 重要边界

- 只能读取系统实际送达的通知，无法保证不漏账，无法读取历史账单。
- 转账给别人、发红包计入支出；收到转账、领红包不计入。
- 成功退款按收到通知当天抵扣，不关联原订单。退款大于支出时显示正净额。
- 小米后台清理可能中断监听，强行停止后应重新打开。自启动和后台设置需要在手机上配置并验证。
- 本地保存账单及含支付相关词的通知文字。联网仅用于用户发起的 GitHub 更新，不上传账单，不使用统计服务。
- 0.5 起已将糖果风界面、月历、月度净支出及底部导航集成进 APK。使用安装包内置的本地页面，通过受限接口读取本机 SQLite 账本，无模拟消费数据。
- 已通过 JVM 规则测试、编译、签名和发布资源校验；尚未完成小米真机采集、权限设置、下载安装的端到端测试。

## 0.5 界面验证

实际从 APK 提取 `assets/index.html`，检查空账本、退款净额 +5、月份切换、明细过滤、无单日汇总、通知原文转义、待识别记录排除及设置/导出接口。此检查使用浏览器和接口桩，不能代替小米真机上的 Android WebView、通知采集与安装测试。

## 构建

需要 Python 3、JDK 17、Android SDK platform 35 与 build-tools 35。不依赖 Gradle 或第三方运行库。

```powershell
python build.py --jdk C:/tools/jdk17 --tools C:/Android/build-tools/35.0.0 --platform C:/Android/platforms/android-35 --work ./build --key C:/private/daily-spending.jks --output ./build/app-release.apk
```

构建会执行解析与更新策略测试。首次生成本机测试签名；后续发布必须使用原签名文件。密钥不在本仓库内，不能提交或作为 Release 附件上传。失去原密钥将无法覆盖更新现有安装。

## 发布更新

1. 修改 Manifest 的 `versionCode`（必须递增）及 `versionName`，构建并验证安装包。
2. 使用 `publish.py` 发布相同版本的 `app-release.apk` 和 `update.json`。需要已登录且有仓库权限的 GitHub CLI。

```powershell
python publish.py --apk ./build/app-release.apk --notes-file ./release-notes.md
```

脚本从 Manifest 读取版本、计算 SHA-256，先创建草稿并上传两个附件，再正式发布，避免更新元数据指向未上传的安装包。签名一致性由构建者负责，App 安装前还会核对。

详见 [使用说明](使用说明.md)。
