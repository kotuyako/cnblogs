# 作者资料、文章数量与评论修复记录（2026-10-22）

## 排查与实测

作者页继续使用 `GET https://api.cnblogs.com/api/blogs/{blogApp}`；列表使用 `/api/blogs/{blogApp}/posts?pageIndex=1`。评论为 `/api/blogs/{blogApp}/posts/{postId}/comments?pageIndex=1&pageSize=10`。

本次只读实测如下，响应内容已按白名单脱敏：

| 作者 blogApp | 博客资料 | 文章列表 | 评论样本 |
| --- | --- | --- | --- |
| Tomorrowland | 401 | 401 | postId=23347321，401 |
| cmt | 401 | 401 | postId=19246558，401 |

请求显式头只有 `Accept: application/json`；均返回 `Content-Type: application/problem+json; charset=utf-8`、`WWW-Authenticate: Bearer`，错误字段 `title=Unauthorized`、`status=401`，type 指向 RFC 9110 的 401 说明。

本机不存在可读取的博客园 PAT 环境变量，未导出 Android Keystore 中的令牌，**PAT 对照未完成**。现有认证请求代码已有 Bearer 和 `Authorization-Type: pat`；不代表旧版 API 一定支持个人 PAT。未提交 Client Credentials、未假定 API 申请已通过、未创建后端、未放入任何 Client Secret。

官方协议依据：https://api.cnblogs.com/help 。文档提供 `title`（博客标题）、`subtitle`（博客副标题）、`postCount`、`blogId`、`pageSize`，不提供任意作者的用户个人简介。匿名 401 仅验证授权层，不能证明成功业务响应、具体权限或接口已废弃。

## 参数传递检查

- 官方文章 JSON 的 `BlogApp` 映射到 `Article.blogApp`；网页列表从可靠作者博客根链接保留标识。
- 收藏/缓存保留 blogApp，老缓存缺字段时兼容合法文章 URL。
- `AuthorRef.from(article)` 优先使用 blogApp，不用昵称拼接口。
- `GardenApp` 将 AuthorRef 传入 AuthorScreen；AuthorRepository 使用同一 ref 构造路径。
- 作者列表解析保留每篇文章 BlogApp，且检查它与目标作者匹配；打开文章再进入作者页仍复用该标识。
- 新测试覆盖 Tomorrowland、cmt 两条数据链，故意使用不同昵称和旧 URL 路径，确认优先采用 BlogApp；这些是明确标识的模拟测试，不是线上授权成功。

## 原因区分

1. 评论与博客资料都复用 `GardenAccountSession.apiRequest`。匿名均 401，失败在 JSON 解析之前；共同障碍是接口授权，而非三个 UI 同时渲染失败。
2. `postCount` 原来就按文档的小写键读取。请求失败自然拿不到数值，本次没有拿已加载列表长度、参考图数字或 0 作为兜底总数。
3. 原 UI 把 subtitle 放在“简介”位置，这是独立的语义错误。博客副标题不能当作用户个人简介。
4. 旧解析器可能将 `{"title":"Unauthorized","status":401}` 错误对象的 title 识别成博客标题，本次显式拒绝该结构。
5. 原来只有 loggedIn 布尔值触发刷新，更换令牌后布尔值不变可能保留旧错误。本次使用不包含凭据的会话版本触发作者与评论重新读取。

## 本次修复

- 明确展示“博客副标题”；个人简介显示“该博客信息接口未提供用户个人简介”，不混用任何用户数据。
- 文章总数只来自 AuthorProfile.postCount：加载中、请求失败、缺字段和真实 0 分开；成功后再失败的旧资料明确标注为上次成功结果。
- 新增 blogId 映射；拒绝负数、小数、溢出或未知类型的数量，不截断出伪造数值。
- 非 2xx 请求使用安全异常保留 HTTP 状态，仅从限长 4096 字节错误体提取固定白名单错误标识，不记录原始响应、Token、Cookie 或任意服务器文本。
- 401 显示“接口未接受当前授权”，而非武断认定 PAT 已过期；403 显示接口权限问题。认证成功不代表每个旧版 API 可用。
- 会话版本只在建立新授权/退出时变化；普通成功刷新不增加版本，避免反复请求。
- 保留 Compose、Material 3、玻璃与沉浸式系统栏、现有分页和返回机制，无后台爬取或权限绕过。

## 修改文件

- `app/src/main/java/dev/garden/reader/account/ApiRequestFailure.kt`（新增）
- `app/src/main/java/dev/garden/reader/account/GardenAccountSession.kt`
- `app/src/main/java/dev/garden/reader/features/AuthorRepository.kt`
- `app/src/main/java/dev/garden/reader/features/AuthorProfilePresentation.kt`（新增）
- `app/src/main/java/dev/garden/reader/AuthorViewModel.kt`
- `app/src/main/java/dev/garden/reader/CommentsViewModel.kt`
- `app/src/main/java/dev/garden/reader/ui/AuthorScreen.kt`
- `app/src/main/java/dev/garden/reader/ui/CommentsSheet.kt`
- `verification/AuthorProfileRepairTest.kt`（新增可选 Robolectric 测试）
- `verification/README.md`（补充测试运行方法）
- 本记录。

## 编译与自动化验证

本轮 `:app:assembleDebug :app:testDebugUnitTest` 执行成功，Gradle 输出 `BUILD SUCCESSFUL`。共 78 项测试，失败 0、错误 0：48 项项目单元测试、20 项协议/状态测试、10 项原生页面与正文测试。其中新增 8 项作者资料修复测试和 2 项作者未授权页面渲染测试。

实际执行命令（在工作区根目录）：

```text
node work/build-v11.cjs --offline -I C:/Users/kirara/Documents/Codex/2026-08-02/yo/work/native-preview.init.gradle -Pandroid.aapt2FromMavenOverride=C:/Users/kirara/AppData/Local/Android/Sdk/build-tools/35.0.0/aapt2.exe -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false :app:assembleDebug :app:testDebugUnitTest
```

自动化测试中的数量 7、13 是显式模拟的协议样本，不是两位作者的真实文章数。页面渲染测试验证本地无授权状态；线上匿名 HTTP 401 由独立只读探测确认。未完成有效 PAT 或官方 OAuth 授权下的端到端真机验收。

## 验收与限制

1. 两位作者分别进入主页，检查地址标识不被昵称替代。
2. 无授权时资料及数量应明确失败，不显示 0 或暂无文章；查看错误应能看到 HTTP 状态（未发请求而缺本地授权时说明本地授权缺失）。
3. 具备官方认可的有效授权后，核对真实 title/subtitle/blogId/postCount；本轮没有取得成功线上业务 JSON，不能断言实际文章数量。
4. 测试真实返回 0、缺 postCount、null、错误 JSON；个人简介永远不能由 subtitle 替代。
5. 已登录时更换授权，页面应重新读取；失败保留手动重试，不无限尝试。
6. 作者页打开文章后返回，原有滚动与导航状态仍保留。

仍需官方认可授权才能验收：旧版评论读写、作者博客资料与总数、作者文章列表。用户个人简介不是上述博客资料接口所提供的数据，需要另有明确支持的官方用户资料接口；本轮没有凭空添加接口。
