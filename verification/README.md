# 官方评论状态与协议测试

在项目根目录运行：

```powershell
.\gradlew.bat -I verification/comment-tests.gradle :app:testDebugUnitTest --tests dev.garden.reader.CommentsApiTest
.\gradlew.bat -I verification/comment-tests.gradle :app:testDebugUnitTest --tests dev.garden.reader.AuthorProfileRepairTest
.\gradlew.bat -I verification/comment-tests.gradle :app:testDebugUnitTest --tests dev.garden.reader.PublicCommentProtocolTest --tests dev.garden.reader.SearchApiRepairTest --tests dev.garden.reader.features.NotificationHistoryTest
```

此可选测试脚本仅给单元测试增加 Robolectric，不增加应用运行依赖。首次运行需要网络下载测试依赖与 Robolectric Android SDK。没有硬编码本机缓存目录、SDK 路径或凭据。

测试使用文档形状 JSON 与假 Repository，覆盖字段解析、错误格式、分页同页重试、去重、发布成功后刷新、不确定写入防重复和未登录不发布；不连接真实 POST 接口，不代表线上发布成功。

作者资料修复测试另覆盖两位作者的 BlogApp 传递、subtitle 与个人简介区分、postCount 的 0/缺失/错误状态、认证会话版本刷新及脱敏错误。测试数字不是实际作者的线上文章数量。

1.6.0 增加公开评论 HTTP-200 登录门槛与分页协议、搜索字段/错误模型，以及新文章通知基线、记录持久化、未读/已读、去重和禁用后旧响应防护。公开评论成功片段是明确标注的模拟样本；实际网站受限响应见 [验证记录](PUBLIC_COMMENTS_2026-10-31.md)。测试不会公开发表评论或生成用户通知记录。
