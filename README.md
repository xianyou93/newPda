# mfPda — 美丰客户中心 PDA 版

> 商米 V3PLUS（Android 14）专用 App。
> 第一步：完整移植微信小程序"美丰客户中心" 5 个模块（登录、个人主页、全部出库单、新增出库、全部客户）的功能与体验。**后端 0 改动**。
> 第二步：对接商米硬件扫码 / 打印 SDK（待你发接口文档）。

---

## 〇、本地无 Studio 一键编译

沙箱环境在 `.dev-env/`（JDK 17 + Android SDK 34 + Gradle 缓存），不污染系统。
若沙箱不可用，也可用系统安装的 JDK 21 + Android SDK。

**方式一：沙箱（JDK 17）**：

```bash
export JAVA_HOME=/workspace/.dev-env/jdk/jdk-17.0.12
export ANDROID_HOME=/workspace/.dev-env/android-sdk
export GRADLE_USER_HOME=/workspace/.dev-env/gradle-home
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
cd /workspace && ./gradlew assembleDebug
# 产出：app/build/outputs/apk/debug/app-debug.apk
```

**方式二：系统 JDK 21（沙箱不可用时）**：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/tmp/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/latest/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
cd /workspace && ./gradlew assembleDebug
# 产出：app/build/outputs/apk/debug/app-debug.apk
```

> **注意**：AGP 8.2.0 与 JDK 21 不兼容（`jlink` 失败），已升级 AGP 到 8.3.2、Gradle 到 8.4。若回退 AGP 版本，必须使用 JDK 17 编译。

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
| 扫码按钮（已接入商米 AIDL） | `ui/orderConfirm/OrderConfirmActivity.kt` 的扫码逻辑 |
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

## 五、当前进度

### 5.1 v2026-06-11 初始修复（Bug#1-5）

| # | Bug | 根因 | 修复 |
|---|-----|------|------|
| 1 | 登录按钮永远 disabled，点不动 | `refreshBtnState()` 检查类字段 `psword` 但 TextWatcher 从未更新该字段，psword 永远为空 | `refreshBtnState()` 里先从 EditText 读取最新值再判断 |
| 2 | 所有网络请求回调不触发 | `Net.doRequest()` 调 `exec()` 时传了空 lambda `{ err, res -> }` 吞掉 onResult | `doRequest` 增加 onResult 参数，所有调用方传入 onResult |
| 3 | BaseActivity toolbar 永远不生效 | `onCreate` 里 `findViewById(R.id.toolbar)` 在子类 `setContentView` 之前执行，视图树为空 | 重载 `setContentView()` 方法，布局加载完再 `installToolbar()` |
| 4 | 登录 parentcorp 传空串导致后端校验失败 | 原代码 `parentcorp=""` | 改为 `"mf"`（与小程序 login.js 一致） |
| 5 | 登录用 Net.req() 带空认证头 | 登录时无 CuserInfo 缓存，req() 会传空的 logincode/usercode 头 | 改用 `Net.req2()`（裸 GET，与小程序首次登录时 https.js 走无 token 分支一致） |

### 5.2 v2026-06-11 功能修复（Bug#6-10）

| # | Bug | 根因 | 修复 |
|---|-----|------|------|
| 6 | 菜单主页点"新增出库单"后"查询收货单出库"按钮不显示 | `MimeActivity` 跳转 `OrderConfirmActivity` 时未传 `type` 参数，小程序传了 `?type=1` | 加 `.putExtra("type", "1")` |
| 7 | 收货单列表不能滑动，从出库页返回后列表被清空 | `onResume` 每次执行 `data.clear(); pageNo=1; load()`，从 OrderConfirm 返回触发 onResume 重建列表 | 改为 `firstLoad` 标记仅首次加载 |
| 8 | 全部客户点编辑客户信息，数据未带过来 | 后端 `getcustom` 返回 `data` 是对象（`Custom.get(0)`），PDA 用 `dataJson.optJSONObject(0)` 按数组解析失败 | 改为优先用 `dataObject`，兼容 `dataJson` |
| 9 | 收货单列表加载时无加载动画 | `load()` 未显示/隐藏 loading | 布局加 `include_loading`，`load()` 请求前显示、回调后隐藏 |
| 10 | 新增出库单产品列表无法滚动，只能看4个左右 | RecyclerView 放在 ScrollView 内，`nestedScrollingEnabled=false` + `wrap_content`，无法独立滚动 | 去掉 ScrollView，改为三段式：顶部表单固定 + 中间 RecyclerView(`weight=1`) 独立滚动 + 底部按钮固定 |

### 5.3 v2026-06-11 下午修复（Bug#11-19）

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

### 5.4 v2026-06-11 下午修复续（Bug#20-27）

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

### 5.5 v2026-06-12 上午修复（Bug#28-30）

| # | 模块 | Bug | 根因 | 修复 |
|---|------|-----|------|------|
| 28 | 全部出库单/收货单列表 | 状态文字颜色不生效，始终为黑色 | Material Components 主题全局 `android:textColor` 覆盖代码 `setTextColor()`，无法动态改文字颜色 | 放弃文字颜色方案，改为**卡片背景色**区分状态：制单/未出库→浅红(`#FFE8E8`)、已出库/完全出库/已收货/已退库→浅绿(`#E8F5E8`)、部分出库→浅蓝(`#E8E8FF`)、退库单→橙色不变 |
| 29 | 退库单提交 | 选择错误退货客户时提示"提交失败"而非后端具体原因 | 后端返回大写 `Msg` 字段，Android `Net.kt` 只读小写 `msg` 读不到，回退显示"提交失败" | `Net.kt` 改为 `json.optString("Msg", json.optString("msg", ""))`，优先读大写兼容小写 |
| 30 | APP 图标 | 原图标为占位 M 字绿底矢量图 | — | 替换为美丰客户中心正式图标 PNG，放入 `mipmap-*dpi/` 各分辨率目录，删除 `mipmap-anydpi-v26/` 和 `mipmap/` 下的 XML 包装层 |

### 5.6 v2026-06-12 下午修复（客户模块 Bug#31-36）

| # | 模块 | Bug | 根因 | 修复 |
|---|------|-----|------|------|
| 31 | 新建客户 | 保存必失败 | PDA 只传 6 个参数，后端 `savCustom()` 强制读 14 个参数，8 个缺失导致 NPE 崩溃 | 补传 Comment/BizManager/LegalPersonIDNumber/ZipCode/AccountName/DepositBank/AccountNumber/CorpCategoryID，值与小程序初始值一致（空字符串或默认 GUID） |
| 32 | 新建客户 | 保存必失败（第二层原因） | 新增时 `code=""` 空字符串，后端 `!StrKit.isBlank(Code)` 验证不通过直接返回 result=0 | `code` 初始值从 `""` 改为 `"系统自动生成"`，与小程序 `Code:'系统自动生成'` 一致 |
| 33 | 编辑客户 | 保存必失败 | 同 Bug#31，缺失 8 个参数导致 NPE；且 `getcustom` 加载时只读了 5 个可见字段，隐藏字段值丢失 | `loadCustom()` 读取全部字段存入成员变量，保存时一并回传 |
| 34 | 客户列表 | 加载用弹窗式 Loading，数据少时一闪而过 | 用 `MfUi.showLoading/hideLoading` 遮挡页面 | 改为内联 Loading（`include_loading`），与收货单列表一致 |
| 35 | 客户列表 | 分页竞态，快速切换 Tab/查询旧响应混入 | 无 `loadVersion` 保护，OkHttp 异步回调旧响应追加到新列表 | 加 `loadVersion` 版本号，与出库单/收货单方案一致 |
| 36 | 客户列表 | 删除客户后整个列表变空 | 用 `removeAt + notifyItemRemoved` 本地移除，删除的是禁用 Tab 的项但列表可能处于启用 Tab，导致索引错乱 | 改为删除/启用成功后 `data.clear(); pageNo=1; loadVersion++; load()` 从服务端重新加载 |

### 5.7 v2026-06-12 下午 UI 重构（启动页 + 登录页 #37-43）

| # | 模块 | 改动 | 说明 |
|---|------|------|------|
| 37 | 启动展示页 | 新增 `SplashActivity` | 全屏展示品牌图 1.5 秒后跳转登录页，`Theme.Splash` 的 `windowBackground` 直接设为展示图，消除冷启动白屏 |
| 38 | 登录页 | 背景改为品牌图 | `Theme.Login` 的 `windowBackground` 设为登录背景图，去掉纯色背景 |
| 39 | 登录页 | Logo 从 M 圆圈改为 "Mefront" 文字 | `sans-serif-light` 36sp + letterSpacing + 阴影，副标题半透明白 |
| 40 | 登录页 | 输入框/卡片 iOS 毛玻璃风格 | 半透明白色底 + 细边框 + 圆角，卡片 `#3AFFFFFF`，输入框 `#30FFFFFF` |
| 41 | 登录页 | 登录按钮玻璃态 | disabled=玻璃感透明 `#28FFFFFF`，enabled=玻璃感灰色 `#68FFFFFF`，文字颜色动态切换 |
| 42 | 登录页 | 错误提示改为毛玻璃 HUD Toast | 居中弹出，深色半透明底 `#B3000000` + 白色文字 + 圆角，替代红色行内文字 |
| 43 | 登录页 | 密码框 hint 改为"密码" | 与"客户代码"风格一致，不再显示"请输入密码" |

### 5.8 已完成的 UI 优化

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

### 5.9 代码来源

2026-06-11 用户提供了本地修改后的完整项目 `.wxxxc/pad-newApp/`，已整体替换到主项目：
- 替换了 `app/src/main/` 全部源码（22 kt + 58 xml）
- 替换了项目级配置（build.gradle / settings.gradle / gradle.properties / gradle/wrapper/）
- 替换了 app/build.gradle / proguard-rules.pro / README.md
- 保留了 `.dev-env/` 构建环境和 `.gitignore`
- 旧代码备份在 `app/src/main.bak/`（.gitignore 已排除）

### 5.10 功能验证状态

| 功能 | 状态 | 备注 |
|------|------|------|
| 启动展示页 | ✅ 已完成 | 毛玻璃风格，冷启动无白屏 |
| 登录 | ✅ 已验证通过 | 用户确认登录成功 |
| 菜单主页 | ✅ 正常 | |
| 全部出库单 | ✅ 已修复 | 客户筛选/分页竞态/空数据提示/日期选择器/按钮尺寸/状态颜色已修复 |
| 新增出库 | ⚠️ 有未解决 Bug | **闪退**：进入新增出库页面时闪退，原因待排查。扫码软按钮/键盘 Enter 防击穿已修复 |
| 收货单列表 | ✅ 已修复 | 滑动问题+按钮显示+分页竞态+状态颜色已修复 |
| 全部客户 | ✅ 已修复 | 新建/编辑保存/列表Loading/分页竞态/删除后重载已修复 |
| 个人中心 | ✅ 已修复 | 修改密码失败提示已修正 |
| 修改密码 | ✅ 已修复 | 失败提示从"网络错误"改为"修改失败，请重试" |
| 解除绑定 | 待验证 | |
| 扫码功能（商米 AIDL） | ⚠️ 有未解决 Bug | `initScanner`+`setScanMode`+防击穿已修复。但进入新增出库页面闪退，可能与扫码相关改动有关，需真机 logcat 排查 |

### 5.11 v2026-06-12 Bug 修复（#45）

| # | 模块 | Bug | 根因 | 修复 |
|---|------|-----|------|------|
| 45 | 全部出库单 | 新增出库保存后跳转列表页，客户筛选条件丢失，只能看到全部数据 | `onResume` 中 `SpCache.getCustom2()` 为 null 时把 `currentCustCode` 清空为 ""，从 OrderConfirmActivity 返回时 custom2 缓存为空导致筛选条件被重置 | `onResume` 中 getCustom2() 为 null 时不再重置 `currentCustCode`，保持原有筛选条件不变 |

### 5.12 v2026-06-15 扫码功能实现（#46-48）

**功能**：OrderConfirmActivity 接入商米 V3PLUS 扫码头引擎 AIDL，实现连续扫码模式。

| # | 模块 | 改动 | 说明 |
|---|------|------|------|
| 46 | AIDL 接口 | 新增 `app/src/main/aidl/com/sunmi/scanner/IScanInterface.aidl` | 商米扫码头引擎 AIDL 接口（scan/stopScan/initScanner/unInitScanner/setScanMode/setScanSound/setScanVibrate） |
| 47 | OrderConfirmActivity | 全面接入扫码逻辑 | AIDL 服务绑定/解绑、BroadcastReceiver 接收扫码结果、连续扫码模式、按钮状态切换、输入框保护、重复码/无效码选择框 |
| 48 | AndroidManifest.xml | 新增 `<queries>` 声明 + 扫码权限 | Android targetSdk >= 30 必须声明 `com.sunmi.scanner` 包可见性，否则 AIDL 绑定失败；部分设备需声明扫码权限 |

**编译问题及解决方案**（供后续开发参考）：

| 问题 | 错误信息 | 根因 | 解决方案 |
|------|----------|------|----------|
| AIDL 不编译 | `Unresolved reference: sunmi` | AGP 8.x 默认 `buildFeatures.aidl = false`，AIDL 文件被忽略 | `app/build.gradle` 的 `buildFeatures` 中加 `aidl true` |
| 缺少 import | `Unresolved reference: BroadcastReceiver` | 手写代码漏了 import | 添加 `import android.content.BroadcastReceiver` |
| AGP 8.2 + JDK 21 不兼容 | `Execution failed for task ':app:minifyDebugWithR8'. A problem occurred starting process 'jlink'` | AGP 8.2.0 的 R8/shader 编译器与 JDK 21 的模块系统不兼容 | 升级 AGP 到 8.3.2 + Gradle 到 8.4（AGP 8.3+ 修复了 JDK 21 兼容性）。若不想升级 AGP，必须用 JDK 17 编译 |
| Gradle wrapper jar 缺失 | `Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain` | `gradle-wrapper.jar` 文件损坏或不存在 | 用系统安装的 Gradle 执行 `gradle wrapper --gradle-version 8.4` 重新生成 |

**扫码交互逻辑**：

| 场景 | 行为 |
|------|------|
| 点击"扫码" | 绑定 AIDL 服务 → `initScanner(packageName)` 初始化 → `setScanMode(0)` 切广播模式 → `scan()` 启动红色激光 → 按钮变"停止扫码"+红色背景 → 输入框/确认/粘贴/保存全部禁用 |
| 扫到一个码 | `BroadcastReceiver` 收到结果 → 调 `addByCode(fromScan=true)` → 成功则添加到列表 |
| 重复码 | 弹选择框："继续扫描"/"停止扫描"。选继续则激光不停继续扫；选停止则 `stopScan()` 恢复所有按钮 |
| 无效码（条码不存在） | 弹选择框："继续扫描"/"停止扫描"，逻辑同上 |
| 扫码期间手动点输入框 | `clearFocus` + Toast "请停止扫码再使用录入功能" |
| 点击"停止扫码" | `stopScan()` 停激光 → 所有按钮恢复初始状态 |
| 退出页面 | `onDestroy` 中 `unInitScanner()` → `unbindService` → `unregisterReceiver` |

### 5.13 v2026-06-15 扫码 Bug 修复（#49-53）

**用户反馈两个 bug**：1) 软按钮没用、没有激光；2) 扫码二维码后跳到全部客户页面。

| # | 模块 | Bug | 根因 | 修复 |
|---|------|-----|------|------|
| 49 | 扫码软按钮 | 点击软按钮没有激光，官方 APP 可以 | AIDL 文件注释明确要求 `initScanner(packageName)` 必须先调用（返回 0=成功），否则 `scan()` 是空操作。之前按 PDF 旧版文档删除了 `initScanner`，但 AIDL 才是设备真实接口 | `onServiceConnected` 中恢复 `initScanner(packageName)` 调用，检查返回值设 `isScannerReady`；成功后调 `setScanMode(0)` 切到纯广播输出模式（关闭键盘输出，从源头消除 Bug#50） |
| 50 | 扫码跳客户页 | 扫箱码后直接跳到全部客户页面 | 商米扫码头默认**双通道输出**：键盘输出（条码文本+Enter）+ 广播。键盘输出的 Enter 误触 `onConfirmOrCancel()` → API 返回错误 → 弹窗出现 → Enter 键击穿弹窗 → 焦点意外落到客户选择行 → 触发 `goPickCustomer()` → 跳到全部客户页。同时键盘输出和广播同时处理同一个码导致双重 API 调用竞态 | 5 层防护：① `setScanMode(0)` 关闭键盘输出（源头）② `processingCodes` Set 防同一码双重处理 ③ `lastScanBroadcastTime` 时间窗拦截 Enter ④ `dispatchKeyEvent` override 全局拦截 Enter/DPAD/NUMPAD_ENTER ⑤ Dialog 按钮 `isFocusable=false` + view `requestFocus` + `onKeyListener` 拦截 Enter |
| 51 | startScan 竞态 | 首次点击软按钮时 `scanInterface` 可能还没连接好（bindService 异步），无意义地设 `isScanning=true` 导致 UI 状态异常 | 加 `isScannerReady` 检查，服务未就绪时 return 并 Toast 提示 |
| 52 | ScanReceiver 重复 | 键盘+广播双重处理同一个码，API 被调两次 | `processingCodes.add(code)` 互斥检查，已处理过的码直接跳过 |
| 53 | Dialog Enter 击穿 | 扫码键盘输出的 Enter 键误触对话框按钮/IME action | Dialog 所有按钮设 `isFocusable=false`，root view `requestFocus` + `onKeyListener` 拦截 Enter；`dispatchKeyEvent` override 在广播收到结果后 500ms 内全局拦截 Enter |

**V3PLUS 扫码服务排查要点**（AIDL 绑定失败时参考）：

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| `bindService` 返回 false | Android 12+ 必须用显式 Intent | 用 `ComponentName(pkg, className)` 显式绑定，不能用纯隐式 Intent |
| Service 类名不确定 | 不同固件版本 Service 类名不同 | 依次尝试 `ScanService`/`ScannerService`/`service.ScanService` |
| Action 不确定 | 不同固件 Action 字符串不同 | 依次尝试 `SCAN_SERVICE`/`action.SCAN_SERVICE`/`com.sunmi.scan` |
| 广播收不到 | 广播 Action 不匹配 | 同时注册 `ACTION_DATA_CODE_RECEIVED`/`ScannerService.decode`/`SCAN_RESULT` |
| data 字段取不到值 | 部分固件 data 是 `byte[]` 非 `String` | 先 `getStringExtra("data")`，为空再 `getByteArrayExtra("data")` → `String(bytes)` |
| 物理键扫码正常但 AIDL 软触发不工作 | AIDL 接口不匹配或服务未绑定 | 降级为物理键扫码模式（广播接收器仍工作），Toast 提示用户 |
| Android 14 注册广播闪退 | `registerReceiver` 必须指定 `RECEIVER_EXPORTED` | API 33+ 用 `registerReceiver(receiver, filter, RECEIVER_EXPORTED)` |
| `com.sunmi.scanner` 包可见性 | targetSdk >= 30 需声明 | AndroidManifest 加 `<queries><package android:name="com.sunmi.scanner"/></queries>` |
| 缺少权限 | 部分设备需要声明扫码权限 | AndroidManifest 加 `<uses-permission android:name="com.sunmi.scanner.permission.SCANNER"/>` |

**V3PLUS 扫码交互问题排查**（物理键/软触发均适用）：

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 物理扫码弹"条码不可用"选择框后立即消失 | 商米扫码默认"键盘输出模式"，扫到码后发 Enter 键，误触对话框按钮 | 选择框设 `setCancelable(false)` + `setCanceledOnTouchOutside(false)` 拦截按键；输入框拦截 Enter/DPAD 键 |
| 扫码期间输入框出现条码文本 | 商米键盘输出模式向焦点输入框键入条码内容 | `afterTextChanged` 中检测 `isScanning`，自动清空输入框 |
| 同一个码被处理两次 | 广播+键盘输出同时送达，广播调 `addByCode(fromScan=true)`，键盘触发 `onConfirmOrCancel` → `addByCode(fromScan=false)` | 扫码期间清空输入框文本 + 拦截 Enter 键 |
| `scan()` 只扫一次不连续 | 商米 `scan()` 是单次软触发，扫到码后激光自动关闭 | `ScanReceiver.onReceive` 中再次调用 `scan()` 重新启动激光 |

> **排查命令**：在设备上执行 `adb shell dumpsys package com.sunmi.scanner | grep -A5 Service` 可查看实际注册的 Service 和 Action。
> **降级方案**：AIDL 软触发失败时，仍可通过物理扫码键 + 广播接收器完成扫码，只是用户需要按物理键而非 App 内按钮。

### 5.13 v2026-06-16 项目清理（文件/资源去重）

| # | 模块 | 改动 | 说明 |
|---|------|------|------|
| 46 | 根目录清理 | 删除 14 个冗余文件 + 2 个目录 | `gitattributes.txt`/`gitignore.txt`/`gradlew.txt`（txt 副本）、`proguard-rules.pro`（重复）、`scan_official_*`/`boxcode_qr*`（无关文档）、`package.json`/`package-lock.json`/`node_modules/`（npm 产物）、`local.properties`（错误 SDK 路径）、`push.bat`（含明文 token）、`mipmap-anydpi-v26/`（图标 XML 包装层，Bug#30 遗留） |
| 47 | drawable/ | 删除 14 个未引用资源文件 | `bg_login_circle.xml`（旧 Logo 圆圈）、`dialog_bg.xml`（旧对话框背景）、`divider_10.xml`/`divider_h.xml`（未引用分隔线）、`ic_add.xml`/`ic_close.xml`/`ic_del.xml`（未引用图标）、`ic_launcher_foreground.xml`/`ic_launcher_legacy.xml`（旧矢量图标）、`ic_mime_my.xml`（个人中心菜单已删）、`ic_progress.xml`/`ic_qrcode.xml`/`progressbar.xml`/`tab_top.xml`（未引用） |
| 48 | strings.xml | 删除 8 条未引用字串 | `login_phone_bind_fail`/`mime_loading`/`rl_salestatus_*`/`oc_scan_placeholder`/`aa_data_err`/`menu_my_center` |
| 49 | colors.xml | 删除 1 条未引用颜色 | `bg_divider` |
| 50 | mipmap/ | 删除 2 个 XML 图标包装文件 | `ic_launcher.xml`/`ic_launcher_round.xml` 引用了已删除的 `ic_launcher_legacy`，导致编译失败；删除后直接使用密度目录下的 PNG |

### 5.14 v2026-06-16 扫码 Bug 修复

| # | 模块 | Bug | 根因 | 修复 |
|---|------|-----|------|------|
| 51 | 扫码 | 扫描结果不处理（致命） | `ScanReceiver.onReceive` 中 `processingCodes.add(code)` 加入成功，`addByCode` 内部再次 `processingCodes.add(code)` 返回 false（已存在），直接 return 不执行任何业务逻辑 | 去掉 ScanReceiver 中的 `processingCodes.add/remove`，去重统一由 `addByCode` 处理 |
| 52 | 扫码 | AIDL 接口与官方严重不匹配导致闪退 | 项目自创 AIDL（8 方法含 `initScanner`/`stopScan`/`setScanMode` 等），与官方扫码头引擎接口（4 方法：`sendKeyEvent`/`scan`/`stop`/`getScannerModel`）完全错位。方法交易码全乱，`scan()` 实际调用了服务端的 `stop()`，一点扫码就闪退 | 按商米官方 `扫码头开发及使用手册文档` 修正 AIDL 为标准的 4 个方法；代码中 `stopScan()` 改为 `stop()`；同时修正服务 Action 为 `IScanInterface` |

#### ⚠️ 重要知识点：商米两侧物理扫码键的按键事件拦截

商米 V3PLUS 机身两侧各有一个物理扫码按键。按下按键触发扫码（红色激光亮），按住不放会持续发送 DPAD_CENTER 按键事件。

扫到条码后，如果码重复/无效，会弹出一个选择框。此时如果用户还按着物理扫码键没松手，DPAD_CENTER 事件会持续发送到屏幕，自动选中并点击弹框按钮，导致弹框瞬间消失，用户根本看不到提示。

因此，扫码期间必须拦截 DPAD_CENTER/DPAD_DOWN/DPAD_UP 等按键事件，防止物理扫码键长按导致的弹框自动关闭。dispatchKeyEvent 也是同理。这不是 Bug，是特意适配商米物理按键的防御代码。

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
6. **列表加载统一用内联 Loading（`include_loading`）**。不用 `MfUi.showLoading/hideLoading`（弹窗式 Loading 会遮挡页面，数据少时一闪而过体验差）。所有列表页布局必须包含 `<include android:id="@+id/loading_box" layout="@layout/include_loading" />`，代码中用 `b.loadingBox.root.visibility = View.VISIBLE/GONE` 控制。
7. **分页列表必须防竞态**。OkHttp 异步回调可能导致旧请求响应混入新列表。所有分页列表 Activity 必须加 `loadVersion` 成员变量，切换条件（Tab/查询/刷新）时 `loadVersion++`，`load()` 捕获当前 version 传入回调，`handle()` 比较版本号丢弃旧响应。
8. **删除/启用/禁用操作成功后，重新加载列表**。不使用本地移除单条数据的方式（容易导致列表状态异常），统一 `data.clear(); pageNo = 1; loadVersion++; load()` 从服务端重新加载，与小程序行为一致。

## 七、已知限制

1. ~~第一步不接扫码硬件~~。**已实现**，见 5.12。
2. **第二步未接打印**。
3. **App 图标已更换**为美丰客户中心正式图标。原图源文件在 `wxxxc/美丰客户中心图标修改.png`，生成到 `mipmap-*dpi/` 各分辨率。
4. **t_wxLoginInfo 表里 PDA 这条记录**没有——后端 `t_logInfo` 写日志时 `wxTableId` 字段会为空串。不影响功能。

## 八、第二步需求（商米 V3PLUS 硬件接入）

> 接入机型：商米V3PLUS，接入app当前项目app
> 技术方案：扫码头引擎 AIDL（红外线扫码头）+ lineApi 打印，详见 `doc/sunmi/商米接口开发文档.md`

### <1> 扫码功能 ✅ 已实现

**页面**：菜单页 → 新增出库单 → 扫码按钮

**技术方案**：商米扫码头引擎 AIDL（`com.sunmi.scanner.IScanInterface`），通过软触发 `scan()` 启动红色激光连续扫描，通过 `BroadcastReceiver` 接收扫码结果广播。不做摄像头扫码降级。

**交互细节**（2026-06-15 确认）：

| 项目 | 说明 |
|------|------|
| 扫码模式 | **连续模式**：点击"扫码"→ 红色激光持续扫描，扫到一个码立即处理，然后继续扫描下一个，直到用户点击"停止扫码" |
| 扫码期间手动输入 | **禁用**：扫码期间输入框获取焦点时提示"请停止扫码再使用录入功能"；确认/取消/粘贴导入按钮均禁用 |
| 扫码按钮状态变化 | 点击"扫码"后 → 按钮变为"停止扫码"，颜色变红；点击"停止扫码"后 → 所有按钮恢复初始状态 |
| 重复码提示 | **选择框**：扫到重复码弹窗，提供"继续扫描"/"停止扫描"两个选项，选择继续则激光继续扫 |
| 提示音 | 商米扫码头引擎自带提示音，如接口支持成功/失败不同提示音则区分，否则不额外处理 |
| 产品计数 | **暂不需要**：按产品区分扫码数量和扫码总数的计数功能暂不实现 |
| 扫码灯 | V3PLUS 扫码头引擎为红色激光线 + 中心瞄准点，无需单独控制扫码灯（非摄像头扫码方案） |
| 条码无效/不存在 | **选择框**：弹出"继续扫描"/"停止扫描"，与重复码逻辑一致 |

**逻辑一致性**：扫码添加条码的后端逻辑（`getcode` / `getALLCode` / `addByCode`）与小程序一致，按照 `wxxxc/qd/` 前端和 `wxxxc/hd/` 后端代码执行，后端 0 改动。

### <2> 打印功能

**页面**：菜单页 → 全部出库单 → 出库单/退库单 → 点击查看 → 出库单明细页（GoodlistActivity）

**技术方案**：商米 PrinterSdk lineApi（行式打印接口），详见 `doc/sunmi/商米接口开发文档.md`

**交互细节**（2026-06-15 确认）：

| 项目 | 说明 |
|------|------|
| 打印按钮位置 | **头部右侧**：和出库/删除按钮同一行 |
| 打印按钮显示条件 | **仅已出库、已退库状态显示**（此时出库/删除按钮已隐藏，打印按钮替代显示）。具体判断：`status == "已收货" \|\| status == "已退库"`（注：后端返回"已收货"，前端显示为"已出库"） |
| 打印内容 | **仅文字明细**：表头（单据编号、客户、日期、类型、备注）+ 列名行 + 明细列表（序号、产品名称、条码、单位），不打印二维码，不打印操作人/审核人 |
| 排版要求 | 排版清晰好看，明细一行一个序号，可换行 |
| 打印按钮样式 | 颜色和样式自定义，与页面风格协调 |
| 纸宽适配 | V3PLUS 支持 58mm/80mm 纸卷，lineApi 比例排版自动适配 |

### <3> 开发顺序

1. **先做扫码**：实现扫码头引擎接入 → 测试通过
2. **再做打印**：实现 lineApi 打印 → 测试通过

### 其他待验证项

- [ ] 如果后端补 `loginapi/getPdaInfo` 接口，去掉 mime 页面的本地缓存兜底
- [ ] "查询收货单出库"功能排查（按钮显示已修复，功能逻辑待验证）
- [ ] 全部功能模块与小程序逐一对比验证

## 九、开发协作规则

- **提交代码时**（用户说"提交到仓库"、"提交代码"等）：必须同步更新 README.md 写清本次版本修改内容，README.md 随代码一起 git add + commit + push。
- **提交前必须经用户确认**：任何 git commit / push 操作，必须先向用户汇报改动内容，用户明确说"提交"后才执行，不得擅自提交。
- **每次代码改动后必须编译 APK**：任何代码修改完成后，必须执行 `assembleDebug` 编译，并告知用户 APK 产出路径（`app/build/outputs/apk/debug/app-debug.apk`），不得遗漏。

## 十、版本

- App versionName: `1.0.0`
- App versionCode: `1`
- minSdk: 24（Android 7.0+）
- targetSdk: 34（Android 14）
- compileSdk: 34
- Kotlin: 1.9.22
- AGP: 8.3.2（原 8.2.0，因 JDK 21 兼容性升级，见 5.12）
- Gradle: 8.4（原 8.2，随 AGP 升级）

---

## 十一、里程碑

**✅ 第一步（微信小程序移植）已完成**（2026-06-12）

5 个功能模块（登录、个人主页、全部出库单、新增出库、全部客户）已完整移植并修复 43 个 Bug，后端 0 改动。UI 已适配商米 V3PLUS 触屏，启动页+登录页采用毛玻璃风格设计。

**🔜 第二步：接入商米 V3PLUS 硬件接口**（2026-06-16 进行中）

1. ✅ 扫码功能（已修复）：AIDL 接口按官方文档修正后，**扫码按钮可正常工作**。连续模式扫描，广播输出。
2. 🔜 打印功能：GoodlistActivity 出库单明细页新增打印按钮（仅已出库/已退库显示），接入商米 PrinterSdk lineApi，打印表头+明细列表（纯文字，无二维码）
3. 后端 0 改动（打印纯本地排版，扫码逻辑与小程序一致）

### 已知问题

- **物理扫码键长按跳到客户管理页**：物理扫码键发送 DPAD_CENTER 事件，会触发 `tvCustRow` 点击跳转到 AddressManagerActivity。当前 `dispatchKeyEvent` 在 `isScanning=true` 时拦截，但物理扫码不经过软件按钮所以 `isScanning` 为 false。待后续修复。
