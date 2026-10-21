# 官方评论状态与协议测试

在项目根目录运行：

```powershell
.\gradlew.bat -I verification/comment-tests.gradle :app:testDebugUnitTest --tests dev.garden.reader.CommentsApiTest
```

此可选测试脚本仅给单元测试增加 Robolectric，不增加应用运行依赖。首次运行需要网络下载测试依赖与 Robolectric Android SDK。没有硬编码本机缓存目录、SDK 路径或凭据。

测试使用文档形状 JSON 与假 Repository，覆盖字段解析、错误格式、分页同页重试、去重、发布成功后刷新、不确定写入防重复和未登录不发布；不连接真实 POST 接口，不代表线上发布成功。
