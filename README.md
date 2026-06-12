# mfPda — 美丰客户中心 PDA 版

> 商米 V3PLUS（Android 14）专用 App。
> 第一步：完整移植微信小程序"美丰客户中心" 5 个模块（登录、个人主页、全部出库单、新增出库、全部客户）的功能与体验。**后端 0 改动**。
> 第二步：对接商米硬件扫码 / 打印 SDK（待你发接口文档）。

---

## 〇、本地无 Studio 一键编译

沙箱环境在 `.dev-env/`（JDK 17 + Android SDK 34 + Gradle 缓存），不污染系统。

```bash
export JAVA_HOME=/workspace/.dev-env/jdk/jdk-17.0.2
export ANDROID_HOME=/workspace/.dev-env/android-sdk
export GRADLE_USER_HOME=/workspace/.dev-env/gradle-home
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
cd /workspace && ./gradlew assembleDebug
# 产出：app/build/outputs/apk/debug/app-debug.apk
```

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
│       │   ├── data/           # 缓存层（SpCache / CuserInfo）
│       │   ├── net/            # OkHttp 封装（Net / ApiClient / ApiResponse）
│       │   ├── util/           # Log / DateUtil / PdaUtil
│       │   ├── widget/         # MfUi（Toast/Dialog/Loading）/ MfListAdapter
│       │   └── ui/             # 11 个 Activity
│       └── res/                # layout / drawable / values / mipmap / xml
├── .wxxxc/                     # 参考原型（不提交仓库，.gitignore 排除）
│   ├── qd/                     # 微信小程序前端源码（49 文件）
│   ├── hd/                     # JFinal 后端源码（799 文件，永不变）
│   └── pad-newApp/             # 用户本地改后的完整 PDA 项目（已合并到主项目）
└── .dev-env/                   # 沙箱构建环境（不提交仓库）
```

## 二、关键模块速查

| 关注点 | 文件 |
|--------|------|
| 设备 MAC → wxopenid/wxPhone | `util/PdaUtil.kt` |
| 缓存（保留原 wx key） | `data/SpCache.kt` + `data/CuserInfo.kt` |
| 网络请求 4 种分类 | `net/Net.kt`（req/req2/reqLogincode/reqPost） |
| 登录入口 | `ui/login/LoginActivity.kt` |
| 菜单主页 | `ui/mime/MimeActivity.kt` |
| 扫码按钮占位（第二步对接商米） | `ui/orderConfirm/OrderConfirmActivity.kt` 的 `onScanClick()` |
| 对话框/Toast/Loading | `widget/MfUi.kt` + `dialog_confirm.xml` |

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

## 五、当前进度（2026-06-11）

### 5.1 已修复的 Bug

| # | Bug | 根因 | 修复 |
|---|-----|------|------|
| 1 | 登录按钮永远 disabled，点不动 | `refreshBtnState()` 检查类字段 `psword` 但 TextWatcher 从未更新该字段，psword 永远为空 | `refreshBtnState()` 里先从 EditText 读取最新值再判断 |
| 2 | 所有网络请求回调不触发 | `Net.doRequest()` 调 `exec()` 时传了空 lambda `{ err, res -> }` 吞掉 onResult | `doRequest` 增加 onResult 参数，所有调用方传入 onResult |
| 3 | BaseActivity toolbar 永远不生效 | `onCreate` 里 `findViewById(R.id.toolbar)` 在子类 `setContentView` 之前执行，视图树为空 | 重载 `setContentView()` 方法，布局加载完再 `installToolbar()` |
| 4 | 登录 parentcorp 传空串导致后端校验失败 | 原代码 `parentcorp=""` | 改为 `"mf"`（与小程序 login.js 一致） |
| 5 | 登录用 Net.req() 带空认证头 | 登录时无 CuserInfo 缓存，req() 会传空的 logincode/usercode 头 | 改用 `Net.req2()`（裸 GET，与小程序首次登录时 https.js 走无 token 分支一致） |

### 5.2 已完成的 UI 优化

- **确认/提示对话框**：从系统默认 AlertDialog 改为自定义圆角卡片样式（`dialog_confirm.xml` + `bg_dialog_confirm.xml`）
  - 圆角 14dp 白色卡片 + 半透明遮罩
  - 标题居中加粗，内容灰色居中
  - 底部双按钮：灰色"取消" + 主题绿加粗"确定"
  - alert 单按钮模式隐藏取消按钮

- **列表小按钮尺寸统一加大**（PDA 触屏点击更舒适）：
  | 文件 | 按钮 | 原尺寸 | 新尺寸 |
  |------|------|--------|--------|
  | `item_address.xml` | 编辑/删除/停用/启用 | 60×28 | 68×36 |
  | `item_order_confirm.xml` | 删 | 50×24→50×32 | 56×36 |
  | `item_goodlist.xml` | ×(删) | 50×24 | 56×36 |
  | `item_receive_list.xml` | 查看/生成 | 60×32 | 68×36 |
  | `activity_address_manager.xml` | 查询 | 60×32 | 68×36 |

### 5.3 已修复的功能 Bug（续）

| # | Bug | 根因 | 修复 |
|---|-----|------|------|
| 6 | 菜单主页点"新增出库单"后"查询收货单出库"按钮不显示 | `MimeActivity` 跳转 `OrderConfirmActivity` 时未传 `type` 参数，小程序传了 `?type=1` | 加 `.putExtra("type", "1")` |
| 7 | 收货单列表不能滑动，从出库页返回后列表被清空 | `onResume` 每次执行 `data.clear(); pageNo=1; load()`，从 OrderConfirm 返回触发 onResume 重建列表 | 改为 `firstLoad` 标记仅首次加载 |
| 8 | 全部客户点编辑客户信息，数据未带过来 | 后端 `getcustom` 返回 `data` 是对象（`Custom.get(0)`），PDA 用 `dataJson.optJSONObject(0)` 按数组解析失败 | 改为优先用 `dataObject`，兼容 `dataJson` |
| 9 | 收货单列表加载时无加载动画 | `load()` 未显示/隐藏 loading | 布局加 `include_loading`，`load()` 请求前显示、回调后隐藏 |
| 10 | 新增出库单产品列表无法滚动，只能看4个左右 | RecyclerView 放在 ScrollView 内，`nestedScrollingEnabled=false` + `wrap_content`，无法独立滚动 | 去掉 ScrollView，改为三段式：顶部表单固定 + 中间 RecyclerView(`weight=1`) 独立滚动 + 底部按钮固定 |

### 5.5 v2026-06-11 下午修复（第二批）

| # | 模块 | Bug | 根因 | 修复 |
|---|------|-----|------|------|
| 20 | 新增出库 | "仅保存"后不跳列表页，小程序点取消跳 ordertotal | `MfUi.confirm` 的 `cancelText="仅保存"` 没绑 `onCancel` 回调 | 加 `onCancel = { goList() }` |
| 21 | 新增出库 | 扫码添加条码不更新列表 | 后端 `getcode` 返回 data 是单个对象，PDA 用 `res.dataJson`(JSONArray) 解析为 null | 改为 `res.dataObject`(JSONObject) |
| 22 | 新增出库 | `getALLCode`/`saveorder` 的 `goodnos`/`orderData` 参数传 `List.toString()` 不是 JSON | `[A, B]` 不是合法 JSON 数组 | `JSONArray(list).toString()` 转 JSON 字符串 |
| 23 | 新增出库 | 日期选择器默认当天而非当前选择 | 与 Bug#16 同理 | 从 TextView 文本解析日期作默认值 |
| 24 | 收货单列表 | 分页竞态，快速切换查询旧响应混入 | 与 Bug#14 同理 | 加 `loadVersion` 版本号 |
| 25 | 收货单列表→出库 | 部分出库后点出库仍加载全部产品 | `loadReceiveDetail` 直接用 `getReceiveDetail` 全量条码，未过滤已出库 | 先取全部条码，再调 `getALLCode` 校验可用性，只保留未出库条码 |
| 26 | 全部出库单 | 状态文字全黑，无颜色区分 | `tv_state` XML 写死 `textColor="@color/text_primary"`，代码无 `setTextColor` | XML 去掉写死颜色，代码加状态颜色逻辑 |
| 27 | 收货单列表 | 出库状态文字颜色不生效 | `0xFF....toInt()` Long→Int 截断可能异常 | 改用 `Color.parseColor("#XXXXXX")` |

**⚠️ 未解决 — 收货单列表/出库单列表状态颜色不生效**：
- 代码已加 `setTextColor(Color.parseColor(...))`，XML 已去掉写死颜色
- 用户安装最新 APK 后颜色仍然全黑
- 可能原因：Android Material 主题 `android:textColor` 全局覆盖、或 `TextView` 被 `TextInputLayout` 等容器包装、或需用 `setTextColor(ContextCompat.getColor())` 兼容写法
- **待明天排查**：① 确认设备 Android 版本；② 试用 `ContextCompat.getColor()` + `textView.post { setTextColor }` 方式；③ 检查是否有 AppCompat 主题自动 tint

### 5.5 v2026-06-11 下午修复

| # | 模块 | Bug | 根因 | 修复 |
|---|------|-----|------|------|
| 11 | 修改密码 | 失败时提示"网络错误" | 失败不一定是网络问题，可能是密码规则不满足 | 改为提示"修改失败，请重试" |
| 12 | 全部出库单 | 从详情页返回后列表重新加载，滚动位置丢失 | `onResume` 每次都清空重载 | 加 `needRefresh` 标志位，仅必要时重载 |
| 13 | 全部出库单 | 选了客户后 cusCode 实际传空，筛选不生效 | `SpCache.getCustom2()` 在 `load()` 异步回调时缓存已被清空 | 加 `currentCustCode` 成员变量存客户代码，`load()` 用变量而非缓存 |
| 14 | 全部出库单 | 切换客户后旧数据混入新列表 | OkHttp 多线程回调竞态，旧请求响应追加到新列表 | 加 `loadVersion` 请求版本号，旧响应丢弃 |
| 15 | 全部出库单 | 列表拉到底"没有数据"不消失 | 分页到末尾返回空数组也触发了空数据提示 | 只有 `pageNo==1` 才显示空数据提示 |
| 16 | 全部出库单 | 日期选择器默认显示当天而非当前选择日期 | `pickDate()` 用 `Calendar.getInstance()` 硬编码 | 从 TextView 文本解析当前日期作为默认值 |
| 17 | 全部出库单 | 退库单背景色纯黄辨识度低 | `#FFFF00` 与白色背景对比度不足 | 改为橙色 `#FFA500` |
| 18 | 全部出库单 | 查看/出库按钮 60×32 偏小 | 之前改其他列表时漏改 | 加大到 76×36 |
| 19 | 出库单明细 | 删除行条码先清空列表，失败后无法恢复 | `rowDelete()` 先 `list.clear()` 再发请求 | 改为成功后再清空并刷新，失败保留原数据 |

- **OrderConfirmActivity 布局重构**：从 ScrollView 包裹全部内容改为三段式布局
  - 顶部：客户/日期/单据类型/备注（固定不滚动）
  - 中间：产品列表 RecyclerView（`layout_weight=1` 独立滚动，可显示全部产品）
  - 底部：输入框+扫码/确认按钮 + 保存/粘贴/查询收货单出库按钮（固定不滚动）
  - 扫码/确认按钮尺寸同步加大：60×32 → 68×36

- **OrderConfirmActivity 布局紧凑化**：减少顶部和底部间距，扩大中间产品列表区域
  - 顶部表单 padding 12dp → 4~6dp，日期/备注 minHeight 32dp → 28dp
  - 底部按钮区 padding 8dp → 4dp，按钮间距 4dp → 2dp，左右 margin 20dp → 16dp
  - 中间列表 marginTop 6dp → 4dp，按钮尺寸不变

### 5.3 代码来源

2026-06-11 用户提供了本地修改后的完整项目 `.wxxxc/pad-newApp/`，已整体替换到主项目：
- 替换了 `app/src/main/` 全部源码（22 kt + 58 xml）
- 替换了项目级配置（build.gradle / settings.gradle / gradle.properties / gradle/wrapper/）
- 替换了 app/build.gradle / proguard-rules.pro / README.md
- 保留了 `.dev-env/` 构建环境和 `.gitignore`
- 旧代码备份在 `app/src/main.bak/`（.gitignore 已排除）

### 5.4 功能验证状态

| 功能 | 状态 | 备注 |
|------|------|------|
| 登录 | ✅ 已验证通过 | 用户确认登录成功 |
| 菜单主页 | 待验证 | |
| 全部出库单 | ✅ 已修复 | 客户筛选/分页竞态/空数据提示/日期选择器/按钮尺寸已修复 |
| 新增出库 | 待验证 | |
| 收货单列表 | ✅ 已修复 | 滑动问题+按钮显示已修复 |
| 全部客户 | ✅ 已修复 | 编辑客户数据带回已修复，按钮尺寸已加大 |
| 个人中心 | ✅ 已修复 | 修改密码失败提示已修正 |
| 修改密码 | ✅ 已修复 | 失败提示从"网络错误"改为"修改失败，请重试" |
| 解除绑定 | 待验证 | |

## 五-B、Bug 排查方法论

查 bug 必须三层联动，逐字逐句看代码：

1. **qd（小程序前端）**：作为参照对比，看 PDA 前端逻辑是否与小程序一致，有无遗漏或偏差
2. **hd（后端 Java）**：逐字逐句看后端代码，确认：
   - PDA 前端调后端接口时，传的参数名、参数值、请求方式是否与后端期望的一致
   - 后端对 PDA 请求的处理逻辑有没有边界问题或异常
   - 后端返回的数据结构 PDA 解析是否正确
3. **PDA 与小程序共用后端的影响**：PDA 的操作会直接修改后端数据，要确认：
   - PDA 写入的数据不会破坏小程序的读取
   - PDA 传的参数不会导致后端走入非预期分支
   - PDA 特有逻辑（如 wxPhone 用 MAC 占位、wxopenid 格式不同）不会影响后端对小程序请求的处理

**原则：排查时只看不动，先报告问题，用户让改才改。**

## 六、移植一致性硬规则

1. **后端（hd/）永不变**。PDA 和微信小程序共用同一后端。
2. **网络请求参数必须与小程序一致**。对比参照 `.wxxxc/qd/` 前端代码。
3. **不添加冗余字段**。如 psnId/psnName/psnIDCardNum/psnMobile/psnAuthStatue 等当前项目未使用的字段不加。
4. **只做必要修复**。不做"保险性"改动（如 MimeActivity 双重 finish()）。
5. **BASE_URL 固定生产环境** `https://xxpt.mefront.com/`，不做环境切换。

## 七、已知限制

1. **第一步不接扫码硬件**。OrderConfirm 的"扫码"按钮点击只弹 Toast "扫码功能待对接"。
2. **第一步不接打印**。
3. **App 图标是占位**（M 字 + 绿底）。后续可换正式图标。
4. **t_wxLoginInfo 表里 PDA 这条记录**没有——后端 `t_logInfo` 写日志时 `wxTableId` 字段会为空串。不影响功能。

## 八、后续 TODO（第二步）

- [ ] 商米 V3PLUS 扫码 SDK 接入（OrderConfirm `onScanClick` 替换为商米调用）
- [ ] 商米打印 SDK 接入（出库单/收货单打印）
- [ ] 如果后端补 `loginapi/getPdaInfo` 接口，去掉 mime 页面的本地缓存兜底
- [ ] "查询收货单出库"功能排查（按钮显示已修复，功能逻辑待验证）
- [ ] 全部功能模块与小程序逐一对比验证

## 九、开发协作规则

- **提交代码时**（用户说"提交到仓库"、"提交代码"等）：必须同步更新 README.md 写清本次版本修改内容，README.md 随代码一起 git add + commit + push。

## 十、版本

- App versionName: `1.1.0`
- App versionCode: `2`
- minSdk: 24（Android 7.0+）
- targetSdk: 34（Android 14）
- compileSdk: 34
- Kotlin: 1.9.22
- AGP: 8.2.0

## 十一、版本日志

### v1.1.0 - 2026/06/12 Bug 修复 + UI 优化

**Bug 修复**

- 登录 parentcorp 传空串 → 改为 "mf"（与小程序一致）
- 登录用 `req2()` 裸 GET，避免无 token 时传空认证头
- 新增出库 "仅保存" 后跳列表页（绑定 onCancel 回调）
- 新增出库扫码添加条码改用 `dataObject` 解析
- 新增出库 `getALLCode`/`saveorder` 参数改为 JSON 数组格式
- 新增出库日期选择器默认显示当前选择日期而非当天
- 新增出库产品列表可滚动（布局重构为三段式）
- 全部出库单列表状态文字加颜色区分（红/蓝/橙色）
- 全部出库单切换客户后加载竞态（加 loadVersion 版本号）
- 全部出库单从详情返回后保持滚动位置（加 needRefresh 标志）
- 全部出库单日期选择器默认显示当前选择日期
- 收货单列表解决滑动清空问题（加 firstLoad 标记）
- 收货单列表分页竞态（加 loadVersion 版本号）
- 收货单列表→出库先校验条码可用性（过滤已出库条码）
- 全部客户编辑时数据带回（兼容 dataObject/dataJson）
- 修改密码失败提示改为"修改失败，请重试"
- 删除出库单行先成功再清空列表（避免失败后丢失数据）

**UI 优化**

- 对话框从系统 AlertDialog 改为自定义圆角卡片样式
- 所有列表小按钮尺寸统一加大（PDA 触屏更舒适）
- OrderConfirmActivity 布局重构为三段式（顶部表单 + 中间列表 + 底部按钮）
- 出库单明细列表删除后更新 UI 更流畅
- 退库单背景色黄色 → 橙色（辨识度提升）
- 出库单列表查看/出库按钮加大到 76×36
