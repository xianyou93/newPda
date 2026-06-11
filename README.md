# mfPda — 美丰客户中心 PDA 版

> 商米 V3PLUS（Android 14）专用 App。
> 第一步：完整移植微信小程序"美丰客户中心" 5 个模块（登录、个人主页、全部出库单、新增出库、全部客户）的功能与体验。**后端 0 改动**。
> 第二步：对接商米硬件扫码 / 打印 SDK（待你发接口文档）。

---

## 一、目录结构

```
mfPda/
├── build.gradle                # project 级
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/             # gradle-wrapper 资源（首次打开会下载）
├── app/
│   ├── build.gradle            # module 级
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mefront/mfPda/
│       │   ├── MfApplication.kt
│       │   ├── base/BaseActivity.kt
│       │   ├── data/           # 缓存层
│       │   ├── net/            # OkHttp 封装
│       │   ├── util/           # Log / Date / PdaUtil
│       │   ├── widget/         # Ui / Adapter 工具
│       │   └── ui/             # 11 个 Activity
│       └── res/                # layout / drawable / values / mipmap / xml
```

## 二、关键模块速查

| 关注点 | 文件 |
|--------|------|
| 设备 MAC → wxopenid/wxPhone | `util/PdaUtil.kt` |
| 缓存（保留原 wx key） | `data/SpCache.kt` + `data/CuserInfo.kt` |
| 网络请求 4 种分类 | `net/Net.kt` |
| 登录入口 | `ui/login/LoginActivity.kt` |
| 菜单主页 | `ui/mime/MimeActivity.kt` |
| 扫码按钮占位（第二步对接商米） | `ui/orderConfirm/OrderConfirmActivity.kt` 的 `onScanClick()` |

## 三、wxopenid / wxPhone 方案

**核心**：PDA 没有微信，无法走 `wx.login()→getToken→openid` 链路。

我们用 **设备 MAC 地址** 当 wxopenid/wxPhone 占位：
- `wxopenid = "pda_" + deviceMac.replace(":","")`
- `wxPhone = "pda_" + deviceMac.replace(":","")`（与 wxopenid 完全一致）

登录时传给后端 `loginapi/login`：
- 后端 `wxPhone || code` 短路 OR 校验通过
- `getCurUser` 看到 wxPhone 非空，**不会调微信 `getuserphonenumber` 接口**
- 走 Membership 业务校验链路，返回 CuserInfo

**后端 0 改动**。

`CuserInfo` 9 个字段全存，缺失用空串：
```
wxopenid / loginname / logincode / userCode / userName
parentCode / membershipId / wxTableId / wxPhone
```

## 四、编译 & 打包

### 4.1 准备

- 安装 **Android Studio Hedgehog | 2023.1.1** 或更新
- 安装 **JDK 17**（Studio 自带）
- 安装 **Android SDK 34**（Studio 自带）
- 启动 SDK Manager 勾选 `Android 14 (API 34)` 和 `Build-Tools 34.0.0`

### 4.2 打开工程

1. Android Studio → File → Open → 选择 `mfPda/` 目录
2. 等待 Gradle Sync 完成（首次会下载 AGP / Kotlin / OkHttp / AndroidX 等约 500MB 依赖，**10-30 分钟**取决于网络）
3. Sync 完成后右下角不再转圈

### 4.3 编译 Debug APK

**命令行**：

```bash
cd mfPda
./gradlew :app:assembleDebug
# 产出：app/build/outputs/apk/debug/app-debug.apk
```

**Android Studio 菜单**：
Build → Build Bundle(s) / APK(s) → Build APK(s)

### 4.4 安装到 V3PLUS

```bash
# USB 连接 V3PLUS，确认开发者模式 + USB 调试已开
adb devices                    # 应能看到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4.5 编译 Release APK

1. 菜单 Build → Generate Signed Bundle / APK...
2. 选 APK → Create new keystore（按提示填）
3. 选 release，签名后产出 `app-release.apk`

## 五、模拟器 / 真机 验证清单

- [ ] 打开 App → 看到登录页
- [ ] 输入客户代码 / 用户名 / 密码 → 登录成功 → 进入菜单主页
- [ ] 菜单 4 项点击 → 跳转正常
- [ ] 全部出库单：选客户、查日期、查看明细、出库
- [ ] 新增出库：选客户、手动输入条码、确认/删除、保存 → 跳列表
- [ ] 收货单列表：分页加载、查看详情、生成出库
- [ ] 全部客户：搜索、启用/禁用 Tab、新建、编辑
- [ ] 个人中心：信息展示、修改密码、解除绑定 → 跳登录
- [ ] 修改密码：必填校验、一致性校验、缺关键字段提示
- [ ] 解绑：成功清除本地缓存 + 跳登录

## 六、已知限制

1. **第一步不接扫码硬件**。OrderConfirm 的"扫码"按钮点击只弹 Toast "扫码功能待对接"。
2. **第一步不接打印**。
3. **App 图标是占位**（M 字 + 绿底）。后续可换正式图标。
4. **t_wxLoginInfo 表里 PDA 这条记录**没有——后端 `t_logInfo` 写日志时 `wxTableId` 字段会为空串。不影响功能。

## 七、后续 TODO（第二步）

- [ ] 商米 V3PLUS 扫码 SDK 接入（OrderConfirm `onScanClick` 替换为商米调用）
- [ ] 商米打印 SDK 接入（出库单/收货单打印）
- [ ] 如果后端补 `loginapi/getPdaInfo` 接口，去掉 mime 页面的本地缓存兜底

## 八、版本

- App versionName: `1.0.0`
- App versionCode: `1`
- minSdk: 24（Android 7.0+）
- targetSdk: 34（Android 14）
- compileSdk: 34
- Kotlin: 1.9.22
- AGP: 8.2.0
