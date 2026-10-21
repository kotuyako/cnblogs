# 原生作者主页实现与验收

## 接口核实（北京时间 2026-10-21）

文档：https://api.cnblogs.com/help

对公开测试作者 `Tomorrowland` 发送不带凭据的 `Accept: application/json` GET：

| 接口 | 实测状态 | 响应 |
| --- | --- | --- |
| `/api/blogs/Tomorrowland` | 401 | `application/problem+json`，`WWW-Authenticate: Bearer` |
| `/api/blogs/Tomorrowland/posts?pageIndex=1` | 401 | 同上 |

实际错误 JSON 的字段为 `type`、`title`（Unauthorized）、`status`（401）、`traceId`。这确认请求到达授权层，但不能单凭 401 确认授权后的业务处理正常，也不能将其解释为作者不存在或文章为空。

没有读取设备中用户的 PAT，也没有有效测试账号令牌，所以**未实测授权后的成功业务 JSON**。以下是核对官方文档得到的协议，不是伪装成实测响应：

- 博客资料为对象：`blogId`、`title`、`subtitle`、`postCount`、`pageSize`、`enableScript`。文档未提供昵称或头像字段。
- 文章为数组，字段区分大小写：`Id`、`Title`、`Url`、`Description`、`Author`、`BlogApp`、`Avatar`、`PostDate`、`ViewCount`、`CommentCount`、`DiggCount`。
- 评论文档提供 `AuthorUrl`。仅接受 `cnblogs.com` / `www.cnblogs.com` 的可靠博客根链接；不从昵称、用户空间编号或任意外部网址推断 blogApp。

运行时对未知 JSON 结构显式报错，只有真实空数组才展示无文章。授权层可达，不代表任意 PAT 均有访问权限或业务接口已验证可用；401/403 仍由原有认证层报告。

## 实现范围

没有新增 Retrofit、OkHttp 或另一套 Navigation：现有工程没有这些依赖。`AuthorRepository` 复用 `GardenAccountSession.apiRequest` 的 PAT、精确域名限制、禁重定向、超时和响应大小上限；JSON 解析移至后台线程。

- `AuthorViewModel` 管理资料、首页、后续页、刷新、错误和重试。刷新取消旧请求并使用代次号隔离过期结果；分页去重、重复页停止，失败不推进页码；资料失败不丢掉成功的文章。
- `Article.blogApp` 优先于文章 URL，旧收藏缺字段时兼容从受验证博文 URL 提取；缓存/收藏保留新增字段。
- 昵称、头像优先采用 API 文章真实字段，缺失时复用入口文章/评论，不以博客标题冒充昵称，不伪造简介或文章总数。
- 作者页使用 Material 3 下拉刷新及已有玻璃、沉浸式系统栏和返回层。原页面仍保留组合及 LazyListState；打开作者文章时作者列表仍留在下层，返回不重新定位到顶部。
- 首页与详情的头像/昵称、评论的头像/昵称均接入作者导航。评论缺少可靠 AuthorUrl 时显示原因，不猜测跳转。
- 没有改动正文获取、评论发布协议或令牌存储，只将共享 GET 错误提示与评论 POST 提示区分。

## 修改文件清单

- `app/src/main/java/dev/garden/reader/AuthorViewModel.kt`：新增作者页状态管理。
- `app/src/main/java/dev/garden/reader/features/AuthorRepository.kt`：新增官方 API Repository、资料模型、文档协议解析及响应结构校验。
- `app/src/main/java/dev/garden/reader/features/Authors.kt`：可靠作者标识解析，移除不再使用的作者网页网络请求实现；保留旧网页纯解析器以兼容已有回归测试。
- `app/src/main/java/dev/garden/reader/ui/AuthorScreen.kt`：原生作者主页、资料、刷新、分页、加载/错误/空状态。
- `app/src/main/java/dev/garden/reader/MainActivity.kt`：复用现有返回动画层，支持多层作者与正文导航，保留前页组合。
- `app/src/main/java/dev/garden/reader/GardenViewModel.kt`：恢复已加载正文快照，避免返回时清空列表导致阅读位置归零。
- `app/src/main/java/dev/garden/reader/data/Article.kt`：新增可空 blogApp。
- `app/src/main/java/dev/garden/reader/data/GardenRepository.kt`：收藏/缓存兼容保存 blogApp。
- `app/src/main/java/dev/garden/reader/data/WebContentParser.kt`：保留网页文章作者链接中的可靠博客标识。
- `app/src/main/java/dev/garden/reader/features/SiteSearch.kt`：保留搜索数据提供的 BlogApp。
- `app/src/main/java/dev/garden/reader/features/Comments.kt`：保留官方评论 AuthorUrl。
- `app/src/main/java/dev/garden/reader/features/PublicCommentParser.kt`：保留公开评论作者链接及可靠性判断。
- `app/src/main/java/dev/garden/reader/ui/GardenScreens.kt`：列表昵称可点击。
- `app/src/main/java/dev/garden/reader/ui/ReaderScreen.kt`：正文昵称与评论作者导航接入。
- `app/src/main/java/dev/garden/reader/ui/CommentsSheet.kt`：评论头像/昵称跳转及无法识别时说明。
- `app/src/main/java/dev/garden/reader/account/GardenAccountSession.kt`：共享 GET 错误文案不再错误提示评论发布状态，认证协议未变。
- `app/src/test/java/dev/garden/reader/features/AuthorIdentityTest.kt`：新增作者标识与公开评论链接测试。
- `AUTHOR_PROFILE.md`：接口验证记录、变更清单和验收方法。

额外本机验证文件（不作为 Android 运行依赖）：工作区 `work/probe-author-api.cjs`、`work/native-screenshot-src/dev/garden/reader/AuthorApiTest.kt`。

## 测试方法与手工验收

1. 未授权打开作者页：应显示授权说明和可重试错误，不能显示虚假空列表；完成原生授权后自动刷新。
2. 首页滚动到中间，分别点击作者头像及昵称；返回应保留原滚动位置。
3. 在正文/评论区同样操作；评论仅可靠 AuthorUrl 可跳转。无博客标识的用户不能按昵称跳错人。
4. 有效 PAT 下检查真实作者的标题、简介、文章总数和文章；缺头像复用入口数据或默认人像。
5. 下拉刷新，加载下一页；加载中重复点击不应重复请求。断网后重试应请求失败的同一页，恢复网络后不得重复插入文章。
6. 打开作者列表中的文章再返回：作者列表滚动位置保留；继续返回原页面。
7. 测试空数组、接口 401/403/404、错误 JSON、资料失败但文章成功，以及深浅色和系统手势返回。

仓库内 `AuthorIdentityTest` 覆盖 blogApp 优先级、URL 安全校验及公开评论作者链接。工作区的 `AuthorApiTest` 使用 Robolectric 和**文档形状的测试数据**验证 JSON 映射、未知格式、分页失败重试、去重及刷新；它不是线上授权集成测试。

常规编译：`gradlew.bat :app:assembleDebug :app:testDebugUnitTest`。本机有 Maven AAPT2 缓存问题，验证时使用 SDK Build Tools 35.0.0 的 `aapt2.exe` 覆盖该可执行文件，不需要修改项目配置。

## 本次执行结果

2026-10-21：Gradle 输出 `BUILD SUCCESSFUL`，`:app:assembleDebug` 与 `:app:testDebugUnitTest` 均完成。共 61 项测试，0 失败、0 错误：48 项仓库内单元测试、5 项本机 Robolectric 作者协议/状态测试、8 项原有原生 UI 与网页 fixture 回归测试。UI 回归不等于作者主页已经在真实账号下验收。

实际执行命令（在当前工作区根目录）：

```text
node work/build-v11.cjs --offline -I C:/Users/kirara/Documents/Codex/2026-08-02/yo/work/native-preview.init.gradle -Pandroid.aapt2FromMavenOverride=C:/Users/kirara/AppData/Local/Android/Sdk/build-tools/35.0.0/aapt2.exe -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false :app:assembleDebug :app:testDebugUnitTest
```

本机 APK：`app/build/outputs/apk/debug/app-debug.apk`；测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。

首次构建发现旧 APK 文件不可覆盖，已将该生成文件保留为工作区备份后重新构建成功，没有删除源码或既有发布包。本次没有修改应用版本号，没有上传 GitHub 或新建 Release。仍需有效 PAT 实测成功业务 JSON、接口权限和手机上的作者导航/滚动/系统手势；不把文档样例和模拟测试标记为线上成功。
