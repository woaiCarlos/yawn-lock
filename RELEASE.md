# Yawn Lock — 发布与上架操作清单

本文档记录首次上架(版本 1.0.0)需要做的所有步骤,以及后续每次发版要重复的操作。

---

## 当前产物

构建完成后,APK 在 `app/build/outputs/apk/{buildType}/` 下,**文件名已经按 `yawn-lock-{versionName}-{buildType}.apk` 约定好**(由 `app/build.gradle.kts` 里的 `renameApksToReleaseConvention` 任务在 `assemble*` 之后自动改名):

- `app/build/outputs/apk/release/yawn-lock-1.0.0-release.apk` — 签名好的发布版(约 10 MB,推荐上架用)
- `app/build/outputs/apk/debug/yawn-lock-1.0.0-debug.apk` — 调试版(约 15 MB,带调试符号,本地测试用)

> 想放到项目根方便取用,自己 `cp` 一下即可,不再写进 SOP。

---

## 一、首次发布准备(已完成)

### 1. 版本号

- `app/build.gradle.kts`:`versionName = "1.0.0"`,`versionCode = 1`
- 后续版本:`versionCode` 每次递增(`2`、`3`...),`versionName` 跟随(`1.0.1`、`1.1.0`、`2.0.0`)

### 2. 签名 keystore

- 文件:`app/release.keystore`(已在 `.gitignore` 里,不会进 git)
- 当前密码:`YawnLock@2026`(存放在 `~/.gradle/gradle.properties`,不进项目)
- 别名:`yawn_lock`
- 有效期:10000 天(到 2053 年)

**重要**:keystore 一旦丢失,这个包名下的所有应用都不能升级了。**生产环境的 keystore 一定要备份到安全的地方**(密码管理器 + 离线加密备份),不要只放在项目里。

### 3. 改了密码怎么办

只需要改 `~/.gradle/gradle.properties` 里的 `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD`,不用动 build.gradle.kts。

---

## 二、构建 release APK

### 第一次构建

```bash
cd /Users/carlos/workspace/打哈欠
. ./.env.sh
./gradlew :app:assembleRelease
```

产物:`app/build/outputs/apk/release/yawn-lock-1.0.0-release.apk`(已自动用 keystore 签名,且通过 `renameApksToReleaseConvention` 任务改好名)。

### 重新生成 keystore(谨慎!)

```bash
cd app && \
/opt/homebrew/opt/openjdk@17/bin/keytool -genkey -v \
  -keystore release.keystore \
  -alias yawn_lock \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 'NEW_PASSWORD' \
  -keypass 'NEW_PASSWORD' \
  -dname 'CN=Yawn Lock, OU=App, O=Yawn Lock, L=Shanghai, ST=Shanghai, C=CN'
```

然后改 `~/.gradle/gradle.properties` 里的密码。**注意:换 keystore 等于换应用签名,Play Store 会拒绝升级已有用户**。

---

## 三、Google Play 上架

### 准备

1. 注册 [Google Play Console](https://play.google.com/console) 账号($25 一次性)
2. 创建新应用,填包名 `com.example.yawnlock`
3. 完成 "应用内容" 问卷(目标受众、内容分级、数据安全等)
4. 准备商店资料:
   - 应用图标 512×512 PNG(用 `yawn_lock_icon_source.png` 出图)
   - 截图(手机 1080×1920,至少 2 张,最多 8 张)
   - 特色图片 1024×500
   - 短描述(80 字以内)、完整描述(4000 字以内)
   - 隐私政策 URL(必备)

### 上传 APK

1. Play Console → "发布" → "正式版"
2. 创建新版本
3. 上传 `app/build/outputs/apk/release/yawn-lock-1.0.0-release.apk` 或上传 AAB(推荐,见下)
4. 填写版本说明
5. 送审(首次审核 1-7 天)

### 强烈建议:用 AAB 而不是 APK

```bash
./gradlew :app:bundleRelease
```

产物 `app/build/outputs/bundle/release/app-release.aab` 上传到 Play Console 后,Google 会按用户设备动态生成最优 APK(节省 20-30% 体积)。**AAB 是 Play Store 的官方推荐格式**。

---

## 四、国内应用商店上架(因为是中国 App,必走)

| 商店 | 后台 | 备注 |
|------|------|------|
| 华为应用市场 | https://developer.huawei.com | 需要企业开发者认证(￥0 资料费) |
| 小米应用商店 | https://dev.mi.com | 个人开发者免费 |
| OPPO 软件商店 | https://open.oppomobile.com | 个人开发者免费 |
| vivo 应用商店 | https://dev.vivo.com.cn | 个人开发者免费 |
| 应用宝(腾讯) | https://open.tencent.com | 个人开发者免费 |
| 阿里应用分发 | https://open.taobao.com | 需企业资质 |
| 百度手机助手 | https://app.baidu.com | 个人开发者免费 |

### 共性要求

1. **应用宝/华为/小米/OPPO/vivo**:都需要"计算机软件著作权登记证书"或"应用市场开发者承诺书"(后者更便宜更快)
2. **隐私政策 URL**:必须挂,可以用 GitHub Pages 免费托管
3. **应用截图**:每个商店要求不同,通常 3-5 张
4. **应用分类**:工具 → 效率 → 生活
5. **ICP 备案**(可选,但上架前最好有)

### 国内商店签名注意事项

国内商店签名要求严格:
- **必须用企业或个人 keystore 签名**(已经是了,我们用 RSA 2048)
- 包名 `com.example.yawnlock` 在 Google Play 一旦上架,国内商店不能再用这个包名(Google 占用)。**所以建议先在 Google Play 上架前先在小米/华为上占位,或者改包名**(如 `com.yawnlock.app`)
- 国内商店会要求重新签名,上传时要用我们的 release.keystore 重新签一遍(用 apksigner)

### apksigner 验证命令(各商店上传前自检)

```bash
/opt/homebrew/opt/openjdk@17/bin/java -jar \
  ~/Library/Android/sdk/build-tools/34.0.0/lib/apksigner.jar \
  verify --print-certs app/build/outputs/apk/release/yawn-lock-1.0.0-release.apk
```

应该看到 `CN=Yawn Lock, OU=App, O=Yawn Lock, ...`

---

## 五、上架前最后检查清单

### 法律合规
- [ ] 用户协议(放在应用内 + 官网)
- [ ] 隐私政策(挂官网,链接到应用商店资料)
- [ ] 应用内容自审报告(国内必须)
- [ ] ICP 备案(如果服务器在国内)

### 技术
- [ ] release APK 用 apksigner verify 验证通过
- [ ] proguard / R8 已配置(目前 `isMinifyEnabled = false`,上线前可考虑打开)
- [ ] 申请的权限列表合理(申请了 `SCHEDULE_EXACT_ALARM` 等敏感权限会被 Play 政策审查)
- [ ] 64 位架构(目前默认会编译 arm64-v8a,大部分商店要求)

### 内容
- [ ] 应用截图 5-8 张(展示 wheel picker、悬浮窗、权限引导、主屏跳转)
- [ ] 应用描述中文 + 英文
- [ ] 关键词(番茄钟、锁屏、效率、专注)
- [ ] 应用图标合规(无过度空白、纯色背景、清晰可辨)

### 测试
- [ ] 在至少 3 台真机测试(中低端、高端、特殊 ROM)
- [ ] 国产 ROM 测过后台保活(华为/小米/OPPO/vivo 各一台)
- [ ] 走完完整流程:打开 → 选时间 → 开始 → 悬浮窗显示 → 到点锁屏

---

## 六、版本管理建议

- 主版本号(`1.x.x`):重大重构、不向后兼容的 API 变化
- 次版本号(`x.0.x`):新功能
- 修订号(`x.x.0`):bug 修复
- `versionCode` 必须每次单调递增(Play Store 要求)
- 每次发布后打 git tag,比如 `v1.0.0`、`v1.0.1`

---

## 七、紧急回滚

如果新版本有严重问题需要回滚:

1. Play Console:不能用回滚的 versionCode 上传,只能禁用旧版本并发布新版本
2. 国内商店:可以申请下架 + 重新上传旧版本(用旧 versionCode)
3. 建议:永远保留最近 3 个版本的 build artifacts(`yawn-lock-1.0.0-release.apk`、`yawn-lock-1.0.1-release.apk`...)

---

## 联系人(占位)

- 技术负责人:todo
- 客服邮箱:todo
- 官网:todo
- 隐私政策 URL:todo

填完上面的信息后,把"todo"替换成实际内容,这份文档就可以作为发布 SOP 使用了。
