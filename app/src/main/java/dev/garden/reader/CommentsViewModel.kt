package dev.garden.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.garden.reader.data.Article
import dev.garden.reader.features.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsState(val items: List<NativeComment> = emptyList(), val page: Int = 0,
    val loading: Boolean = false, val hasMore: Boolean = false, val posting: Boolean = false,
    val error: String? = null, val failedPage: Int? = null, val notice: String? = null,
    val draft: String = "", val source: String = "博客园官方评论 API", val uncertain: Boolean = false)

class CommentsViewModel(private val article: Article, private val repository: CommentDataSource) : ViewModel() {
    private val target = CommentTarget.from(article)
    private val mutable = MutableStateFlow(CommentsState())
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    private var generation = 0
    private var authorization: Boolean? = null
    fun start(loggedIn: Boolean) {
        if (authorization != loggedIn) { authorization = loggedIn; refresh() }
        else if (state.value.page == 0 && !state.value.loading && state.value.error == null) refresh()
    }
    fun draft(value: String) { if (!state.value.posting && value.length <= 4000) mutable.update { it.copy(draft = value) } }
    fun refresh() { if (!state.value.posting) load(1) }
    fun more() { if (state.value.hasMore && !state.value.loading && !state.value.posting) load(state.value.page + 1) }
    fun retry() { if (!state.value.loading && !state.value.posting) load(state.value.failedPage ?: 1) }
    private fun load(page: Int) {
        val destination = target ?: run { mutable.update { it.copy(error = "无法可靠识别博文 blogApp 和文章 ID") }; return }
        readJob?.cancel(); val ticket = ++generation
        mutable.update { it.copy(loading = true, error = null, failedPage = null) }
        readJob = viewModelScope.launch {
            try {
                val result = repository.read(article, destination, page)
                if (ticket != generation) return@launch
                mutable.update { old ->
                    val merged = (if (page == 1) result.items else old.items + result.items).distinctBy { it.id }
                    old.copy(items = merged, page = page, loading = false, source = result.source, uncertain = false,
                        hasMore = result.hasMore && (page == 1 || merged.size > old.items.size))
                }
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { if (ticket == generation) mutable.update { it.copy(loading = false,
                error = e.message ?: "评论读取失败", failedPage = page) } }
        }
    }
    fun post(loggedIn: Boolean) {
        if (state.value.posting || state.value.loading || state.value.uncertain) return
        if (!loggedIn) { mutable.update { it.copy(notice = "请先完成原生登录授权") }; return }
        val destination = target ?: return
        val submitted = state.value.draft.trim()
        if (submitted.isBlank()) return
        readJob?.cancel(); generation++
        mutable.update { it.copy(posting = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                repository.post(destination, submitted)
                mutable.update { it.copy(posting = false, draft = "", notice = "服务器已接受评论，正在刷新；审核或缓存可能延迟显示。") }
                load(1)
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { mutable.update { it.copy(posting = false, uncertain = true,
                notice = "未确认发布成功：${e.message ?: "网络异常"}。请先刷新核对，勿直接重复提交。") } }
        }
    }
}
