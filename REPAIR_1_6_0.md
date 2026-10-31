# 博客园第三方客户端 1.6.0（2026-10-31）

Kotlin + Jetpack Compose 原生体验版，包名 `dev.garden.reader`，versionCode 7。APK 使用现有本机 debug 签名，不是商店正式签名。请先尝试覆盖安装，不要为了签名错误盲目卸载而丢失收藏。

## 本次修改

1. 顶栏包含状态栏与分类标签的真实测量高度，完全移出屏幕，避免残留标签；首页刷新按钮前置。
2. 首页、新闻、收藏和我的列表根据实际消费的滚动方向收起/恢复玻璃底栏；18dp 方向阈值抑制细微抖动，回到列表顶部自动恢复。
3. 文章目录仅保留章节；正文中的目录链接基于保留的真实 heading ID/命名锚点跳转，并避让顶部返回控件。旧正文缓存使用版本化 key 重新读取，收藏搜索仍兼容旧缓存。
4. 增加保存到本机偏好的纯黑夜间开关。浅色模式不受影响，深色背景为纯黑，玻璃控件保留明暗层次及莫奈强调色。
5. 左右侧系统预测性返回使用相同的向右退出，显示左侧上一页，保留取消回弹；没有禁用系统返回。
6. 收藏搜索真实本机收藏的文章/新闻标题、作者、摘要及已缓存公开正文；多关键词、分类、标题过滤、清空和搜索按钮有效，不联网抓取未读收藏。
7. 新闻保留官方全站搜索；无有效授权时采用明确标注范围的公开近期新闻匹配，每批最多 3 页，可继续检索更早列表。修复新闻分页 `Next >` 识别。
8. 通知首次开启立即建基线；增加立即检查、错误重试、站内历史、未读/已读和原生跳转。重复检查不重复通知；关系统权限不丢站内记录，禁用后的旧请求不能回写。不是实时服务器推送，后台调度最短约 15 分钟并可能延迟。

## 真实网络结果与尚未恢复的能力

- 公开新闻首页及第 2、3 页：HTTP 200，每页真实 30 条新闻；用于近期匹配，不冒充完整历史索引。
- 官方新闻搜索：匿名 HTTP 401 `Unauthorized`。
- 网页全站新闻搜索重定向至 `zzkx.cnblogs.com` 后 HTTP 200，但内容是人机验证页面。未尝试绕过验证。
- 三位博客作者网页评论均 HTTP 200、返回非零评论数量但正文为空，`commentForm` 明确要求网页登录。官方评论匿名请求仍 HTTP 401，`WWW-Authenticate: Bearer`。
- 新闻评论片段仅返回换行，另一个网页自身请求明确要求登录。现在显示真实受限原因，不误报暂无评论。

**匿名评论读取没有恢复，完整历史新闻搜索也未在缺少有效授权时验证成功。** 没有合法网页会话或待审核的 OAuth 应用凭证，不能把 PAT 当作网页登录，也没有抓取受限评论。发表评论继续复用官方 API，不自动重试，不硬编码密钥、不创建后端。具体地址、头名称和脱敏响应见 [公开评论验证](verification/PUBLIC_COMMENTS_2026-10-31.md)。

作者资料与数量此前修复包含在此版本：区分博客副标题与个人简介，postCount 不以 0 或列表长度替代，授权失败明确显示。见 [作者资料记录](AUTHOR_PROFILE_REPAIR.md)。

## 修改文件范围

- 阅读/导航：`data/ContentParser.kt`、`data/NativeDocument.kt`、`features/ReaderNavigation.kt`、`features/ScrollChrome.kt`、`ui/ReaderScreen.kt`、`ui/PredictiveGlassLayer.kt`、`ui/GardenScreens.kt`。
- 搜索/缓存：`features/SiteSearch.kt`、`features/BookmarkSearch.kt`、`ui/SiteSearchScreen.kt`、`data/GardenRepository.kt`、`data/WebContentParser.kt`、`GardenViewModel.kt`。
- 通知：`features/ArticleNotifications.kt`、`features/NotificationPolicy.kt`、`features/NotificationHistory.kt`、`ui/NotificationInbox.kt`、`ui/FeaturePreferences.kt`、`MainActivity.kt`。
- 主题/版本：`ui/GardenTheme.kt`、`app/build.gradle.kts`、`features/ReleaseUpdates.kt`。
- 评论：`features/Comments.kt`、`features/PublicCommentProtocol.kt`；原生解析与官方 POST 协议保留。
- 回归测试：`ReaderNavigationTest.kt`、`SearchRepairTest.kt`、`NotificationPolicyTest.kt`，以及 `verification` 下的公开评论、搜索协议与通知存储测试。
- 软件说明：`README.md`、`CHANGELOG.md`、本记录与验证说明。

## 手机验收步骤

1. 首页向下浏览，确认分类完全收起、底栏滑出；向上滚动或回到顶部，确认恢复。新闻/收藏/我的分别测试底栏。
2. 外观选择深色，再开启纯黑；返回首页、正文、搜索、评论和我的，检查背景、文字及系统栏对比度；重启确认偏好保留。
3. 打开有多节标题和正文目录链接的文章，点击正文目录跳对应节；底栏目录只显示章节，无复制/字号等杂项；两侧返回和取消手势分别检查。
4. 收藏一篇文章和一条新闻，离线搜索标题/作者，切换分类；已读过正文后搜索仅正文出现的词，确认未收藏的缓存不会混进结果。
5. 新闻搜索 `AI`，核对真实新闻和范围提示，继续检索较早批次；网络关闭时出现重试，而不是假空列表。
6. 我的 → 新文章通知 → 开启，检查首次基线及成功时间；等首页出现真实新文章后立即检查，记录可打开并标已读。系统权限关闭时验证站内记录仍保留；后台通知时效必须在真实设备另验。
7. 评论受限时应显示网页登录门槛/API 认证状态，不显示假评论或无评论。

自动化使用明确的模拟成功响应及实际脱敏受限样本，不会公开发表评论或制造用户通知。真实手机手势流畅度、后台系统调度和特定设备玻璃效果仍需手机验收。

## 编译和自动化结果

最终 Gradle `:app:assembleDebug :app:testDebugUnitTest` 输出 `BUILD SUCCESSFUL`。118 项测试，失败 0、错误 0：72 项普通单元测试、33 项 Robolectric 协议/存储/状态测试、13 项原生 UI/正文测试。最后一轮完整重跑包含全部测试，不只是先前失败项。

原生测试实际验证了顶栏与底栏隐藏后消失、反向滚动恢复，收藏文章/新闻搜索提交，以及纯黑背景和精简章节弹层。新闻近期列表为真实公开 HTTP 验证；成功评论 JSON/HTML 为模拟协议样本，不能作为线上授权成功证据。

APK 使用 Android Build Tools 35.0.0 的 apksigner 验证通过（v2 签名），版本 1.6.0 / versionCode 7，沿用本机 Android Debug 公钥证书。Windows 测试环境的原生 Conscrypt 兼容问题只在 Robolectric 测试配置中处理，没有关闭 APP 的 HTTPS 验证。旧 APK 输出不可写时仅移至工作目录保留，没有删除用户数据。

公开源码可在项目根目录运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat -I verification/comment-tests.gradle :app:testDebugUnitTest
```

本轮另通过本机可选 UI 测试入口执行 13 项 Compose 原生渲染/交互测试。源码与 APK 分开上传，发布校验文件包含两者 SHA-256，不包含本地 SDK 配置、缓存、令牌或私钥。
