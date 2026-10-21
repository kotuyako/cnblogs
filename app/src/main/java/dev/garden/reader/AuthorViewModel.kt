package dev.garden.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.garden.reader.data.Article
import dev.garden.reader.features.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class AuthorState(val profile: AuthorProfile? = null, val articles: List<Article> = emptyList(),
    val refreshing: Boolean = false, val loadingMore: Boolean = false, val page: Int = 0,
    val hasMore: Boolean = false, val error: String? = null, val profileError: String? = null, val failedPage: Int? = null)

class AuthorViewModel(private val author: AuthorRef, private val repository: AuthorDataSource) : ViewModel() {
    private val mutable = MutableStateFlow(AuthorState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    init { refresh() }
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (c: CancellationException) { throw c } catch (e: Exception) { Result.failure(e) }
    fun refresh() {
        job?.cancel(); val ticket = ++generation
        mutable.update { it.copy(refreshing = true, loadingMore = false, error = null, profileError = null, failedPage = null) }
        job = viewModelScope.launch {
            supervisorScope {
                val profile = async { attempt { repository.profile(author) } }
                val posts = async { attempt { repository.posts(author, 1) } }
                val info = profile.await(); val first = posts.await()
                if (generation != ticket) return@supervisorScope
                mutable.update { old ->
                    val data = info.getOrNull() ?: old.profile
                    val items = first.getOrNull()
                    old.copy(profile = data, articles = items ?: old.articles, refreshing = false,
                        page = if (items != null) 1 else old.page,
                        hasMore = if (items != null) more(items.size, 1, data) else old.hasMore,
                        error = first.exceptionOrNull()?.let { it.message ?: "作者文章加载失败" },
                        profileError = info.exceptionOrNull()?.let { it.message ?: "作者资料加载失败" },
                        failedPage = if (first.isFailure) 1 else null)
                }
            }
        }
    }
    fun more() {
        val old = state.value
        if (old.refreshing || old.loadingMore || !old.hasMore) return
        val ticket = generation; val page = old.page + 1
        mutable.update { it.copy(loadingMore = true, error = null, failedPage = null) }
        job = viewModelScope.launch {
            val batch = attempt { repository.posts(author, page) }
            if (generation != ticket) return@launch
            mutable.update { current -> batch.fold(onSuccess = { rows ->
                val merged = (current.articles + rows).distinctBy { it.url }
                current.copy(articles = merged, loadingMore = false, page = page,
                    hasMore = merged.size > current.articles.size && more(rows.size, page, current.profile))
            }, onFailure = { current.copy(loadingMore = false, error = it.message ?: "作者文章加载失败", failedPage = page) }) }
        }
    }
    private fun more(size: Int, page: Int, profile: AuthorProfile?): Boolean = size > 0 &&
        (profile?.pageSize == null || size >= profile.pageSize) &&
        (profile?.postCount == null || profile.pageSize == null || page.toLong() * profile.pageSize < profile.postCount)
}
