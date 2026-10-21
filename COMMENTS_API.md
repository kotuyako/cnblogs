# 官方博文评论功能与验证记录

## 实测接口（北京时间 2026-10-21）

官方文档：https://api.cnblogs.com/help

对两个公开文章地址的官方评论 GET（`pageIndex=1&pageSize=10`）进行匿名实测：

- `/api/blogs/Tomorrowland/posts/23347321/comments`：HTTP 401。
- `/api/blogs/Tomorrowland/posts/23223896/comments`：HTTP 401。

两次响应均为 `application/problem+json`，`WWW-Authenticate: Bearer`；实际 JSON 字段为 `type`、`title: Unauthorized`、`status: 401`、`traceId`。这只验证到授权层，不能据此宣称成功业务接口当前可用，也不能误判文章不存在或暂无评论。

没有读取真实账号 PAT，也没有公开发送测试评论。授权成功后的业务 JSON、POST 成功写入以及账号权限**未做线上验证**。已核对文档 GET 为数组，字段 `Id`、`Body`、`Author`、`AuthorUrl`、`FaceUrl`、`Floor`、`DateAdded`；本地解析器测试使用明确标识的文档形状测试数据，不是服务器真实返回。POST 使用 `{ "body": "评论内容" }`；没有擅自假设额外的成功响应结构，依赖 HTTP 2xx，并拒绝已知显式 false 标记。

## 复用架构

工程没有 Retrofit/OkHttp，继续使用既有 `GardenAccountSession.apiRequest`（HttpURLConnection、Keystore 加密 PAT、精确 HTTPS API 域名、禁止重定向、超时与响应大小限制）。未硬编码 Token/Client Secret；认证协议和账号校验不变。

文章已有 `blogApp`，优先使用；postId 从受验证博文 URL 的 `/p/{id}.html` 或已有传统路径提取。不从昵称猜测 blogApp，不接受任意外部地址。旧缓存缺少 blogApp 时兼容既有合法博文 URL。

博文评论现在只调用官方 GET/POST。新闻分支维持既有读取逻辑，不扩展或新加网页爬取。接口错误、未知 JSON 对象不会转换成空列表。

## 修改文件

- `features/CommentTarget.kt`：从文章数据优先取 blogApp，复用既有 URL 校验取得文章 ID。
- `features/Comments.kt`：博文只走官方 API，每页 10 条；复用合法认证和原有新闻分支。
- `features/OfficialCommentParser.kt`：新增官方 JSON 解析，保留作者、时间、正文、头像与 AuthorUrl，错误结构不伪装为空。
- `CommentsViewModel.kt`：新增分页/刷新/同页重试、去重、草稿、发布与不确定状态管理；不自动重试 POST。
- `ui/CommentsSheet.kt`：复用同一 ViewModel 的原生完整列表和正文预览；加载、空状态、错误重试、原生授权、输入及发布后刷新。
- `ui/ReaderScreen.kt`：正文末尾加入原生评论区域，保留原有底栏评论入口和阅读功能。
- `README.md`：更新当前主分支与已发布版本的行为差异。
- 本文：实测限制与验收记录。

仓库同时提供 `verification/CommentsApiTest.kt`、`verification/comment-tests.gradle` 和说明，可选运行评论专用测试，不增加生产依赖。与本机外部执行的 CommentsApiTest 内容一致（Robolectric + 假数据源，无真实发布）。匿名 GET 验证脚本为工作区 `work/probe-comments-api.cjs`。

## APP 内验收

1. 打开博客文章，滑到正文底部查看评论区域；或点击底栏评论图标打开完整列表。
2. 未登录时应提示原生登录授权；不能因 401 显示暂无评论。到「我的」使用官方个人访问令牌授权，返回后自动重新读取。
3. 有相应权限的 PAT 下核对作者、内容和时间；下一页每次请求 10 条，失败后重试同一页，不重复插入。
4. 输入评论并确认公开发布：发送期间禁用重复操作；HTTP 成功后清空草稿并自动刷新第一页，审核/缓存可能延迟可见。
5. 若网络中断导致发布不确定，草稿保留，再发按钮锁定；先点击刷新核对是否已经出现，避免重复发布。刷新失败不会解锁。
6. 测试空数组、401/403、网络失败、未知 JSON、分页重复页、返回正文与登录流程。新闻功能应维持原有行为。

不要把 GitHub 授权当作博客园 PAT；两者是完全不同的认证系统。本机编译与模拟数据测试不等于线上评论发布成功。

## 本次构建结果

2026-10-21：`:app:assembleDebug`、`:app:testDebugUnitTest` 完成，Gradle 输出 `BUILD SUCCESSFUL`。共 68 项测试，0 失败、0 错误：48 项项目内单元测试、12 项本机 Robolectric 协议/状态测试（其中官方评论 7 项）、8 项原有 UI/网页 fixture 回归。评论专用测试使用可控假数据源，没有线上 POST。

实际本机命令：

```text
node work/build-v11.cjs --offline -I C:/Users/kirara/Documents/Codex/2026-08-02/yo/work/native-preview.init.gradle -Pandroid.aapt2FromMavenOverride=C:/Users/kirara/AppData/Local/Android/Sdk/build-tools/35.0.0/aapt2.exe -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false :app:assembleDebug :app:testDebugUnitTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。此前的生成 APK 已保留为工作区备份，没有覆盖旧发布包。本次没有提高版本号或替换 GitHub 已发布的 1.5.0 APK；源码主分支包含当前作者主页和官方评论改进。
