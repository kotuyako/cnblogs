package dev.garden.reader.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ReleaseUpdates {
    // Set only after the owner selects and authorizes the real publication repository.
    const val REPOSITORY = "kotuyako/cnblogs"
    const val VERSION = "1.4.0"
    data class Result(val message: String, val url: String? = null)
    suspend fun check(): Result = withContext(Dispatchers.IO) {
        check(REPOSITORY.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "尚未配置 GitHub 发布仓库，不能确认是否有更新。" }
        val (status, text) = PublicHttp.get("https://api.github.com/repos/$REPOSITORY/releases/latest", "api.github.com", 512 * 1024)
        check(status != 404) { "仓库还没有公开正式版本，暂时无法检查更新。" }
        check(status == 200) { "检查更新失败（HTTP $status），并不表示已是最新版。" }
        val json = JSONObject(text)
        check(!json.optBoolean("draft") && !json.optBoolean("prerelease")) { "未找到正式版本" }
        val tag = json.getString("tag_name")
        if (ReleaseVersion.newer(tag, VERSION)) Result("发现新版本 $tag", "https://github.com/$REPOSITORY/releases/latest")
        else Result("当前版本 $VERSION，最新正式版本 $tag")
    }
}
