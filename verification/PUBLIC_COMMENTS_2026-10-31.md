# 网页评论验证（2026-10-31）

结论：本次没有恢复匿名评论读取。网站现在明确要求网页会话，不能通过网页返回的评论数推断正文已公开，也不能把 HTTP 200 空片段显示成“暂无评论”。未登录网页、未抓取受限评论、未绕过验证、未发送 PAT/Cookie 至网页域名。

## 实际请求

全部为 GET；请求仅包含 `User-Agent`、`Accept`，部分诊断使用网页脚本原本的 `X-Requested-With`、`Referer`。不含 `Authorization`、`Cookie` 或客户端密钥。

| URL | HTTP | 脱敏结果 |
| --- | --- | --- |
| `https://www.cnblogs.com/cmt/ajax/comments-block?postId=19316348&isDesc=false&order=0&loadCommentBox=true` | 200 | `application/json; charset=utf-8`；`commentCount:45`，`comments:""`；`commentForm` 明确为“登录后才能查看或发表评论” |
| `https://www.cnblogs.com/sdcb/ajax/comments-block?postId=23486182&isDesc=false&order=0&loadCommentBox=true` | 200 | `commentCount:16`，`comments:""`；相同网页登录门槛 |
| `https://www.cnblogs.com/yuyisi/ajax/comments-block?postId=23179390&isDesc=false&order=0&loadCommentBox=true` | 200 | `commentCount:6`，`comments:""`；相同网页登录门槛 |
| `https://www.cnblogs.com/Tomorrowland/ajax/comments-block?postId=23223896&loadCommentBox=false` | 200 | `commentCount:2`，`comments:""`，`commentForm:""`；原请求隐藏了门槛提示 |
| `https://news.cnblogs.com/CommentAjax/GetComments?contentId=841663` | 200 | `text/html; charset=utf-8`，仅一个换行；不能据此断定无评论 |
| `https://news.cnblogs.com/NewsAjax/LoadDetailommentInfo?newsId=843338` | 200 | `text/plain; charset=utf-8`；“查看或发表评论，请先…登录” |
| `https://api.cnblogs.com/api/blogs/cmt/posts/19316348/comments?pageIndex=1&pageSize=10` | 401 | `application/problem+json; charset=utf-8`；`WWW-Authenticate: Bearer`；`title:Unauthorized,status:401` |
| `https://api.cnblogs.com/api/blogs/sdcb/posts/23486182/comments?pageIndex=1&pageSize=10` | 401 | 同上 |

站点自身的公开博客 JS 使用 `/{blogApp}/ajax/comments-block`，参数包含 `postId`、`pageIndex`、`anchorCommentId`、`isDesc`、`order`、`loadCommentBox`。新闻 JS 使用 `/CommentAjax/GetComments?contentId=...`，没有分页参数。只检查了这些网站本身使用的请求，没有猜测隐藏接口。

## 本次修改

- `app/src/main/java/dev/garden/reader/features/Comments.kt`：保留官方接口及发表评论；PAT 已登录时读取 API，只有 401/403 尝试无凭证网页读取；网页门槛不被 API 的泛化错误覆盖。未授权时直接验证公开网页响应，不承诺 PAT 能建立网页会话。
- `app/src/main/java/dev/garden/reader/features/PublicCommentProtocol.kt`：明确识别 HTTP 200 中的登录门槛；查询启用 `loadCommentBox=true`；仅真正返回合法正文时交给现有原生解析器；仅明确且未受限的 `commentCount:0` 可作为空列表；正数而缺正文、未知 JSON、空新闻片段均显示错误；博客下一页仅依据服务器明确分页链接。
- `verification/PublicCommentProtocolTest.kt`：实际受限响应回归测试及明确标注为模拟的合法 HTML、作者链接、分页、零评论、错误模型测试。

现有 `PublicCommentParser`、Compose 展示及官方发布协议未被替换。没有加入 WebView、后端、Cookie 登录或硬编码密钥。

## 验证边界

- 两个官方 API 匿名请求确认为 401；网页至少三位不同作者均返回明确登录门槛。
- 未取得合法网页会话，因此没有非空真实评论正文样本，也没有宣称匿名读取成功。
- 成功 HTML 测试仅验证本地解析器及原生数据结构，不能作为线上访问成功的证据。
- PAT 与网页登录是两套认证。当前客户端只有官方 PAT 认证；不能将它伪装为网页 Cookie，也不能假定待审核的 Open API 凭证已可用。
