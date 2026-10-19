package dev.garden.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.garden.reader.account.GardenAccountSession
import dev.garden.reader.data.Article
import dev.garden.reader.data.Channel
import dev.garden.reader.data.GardenRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GardenState(
    val channel: Channel = Channel.HOME,
    val articles: List<Article> = emptyList(),
    val bookmarks: List<Article> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val theme: String = "system",
    val dynamicColor: Boolean = true,
    val fontSize: Float = 18f,
    val selected: Article? = null,
    val body: String? = null,
    val bodyLoading: Boolean = false,
    val bodyError: String? = null,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

class GardenViewModel(application: Application) : AndroidViewModel(application) {
    val account = GardenAccountSession(application)
    private val repository = GardenRepository(application)
    private val mutable = MutableStateFlow(GardenState(
        articles = repository.cached(Channel.HOME), bookmarks = repository.bookmarks(),
        theme = repository.theme, fontSize = repository.fontSize, dynamicColor = repository.dynamicColor,
    ))
    val state = mutable.asStateFlow()
    private var feedJob: Job? = null
    private var bodyJob: Job? = null

    init { refresh(); refreshAccount() }

    fun refreshAccount() { viewModelScope.launch { account.refresh() } }

    fun logout() {
        viewModelScope.launch {
            bodyJob?.cancel()
            close()
            account.logout()
            repository.clearBodyCache()
        }
    }

    fun selectChannel(channel: Channel) {
        if (channel == state.value.channel) return
        mutable.update { it.copy(channel = channel, articles = repository.cached(channel), error = null, hasMore = false, loadingMore = false) }
        refresh()
    }

    fun refresh() {
        feedJob?.cancel()
        val channel = state.value.channel
        mutable.update { it.copy(loading = true, error = null, loadingMore = false) }
        feedJob = viewModelScope.launch {
            try {
                val articles = repository.refresh(channel) { initial ->
                    mutable.update { if (it.channel == channel) it.copy(articles = initial) else it }
                }
                mutable.update { it.copy(articles = articles, loading = false, hasMore = repository.hasMore(channel)) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(loading = false, error = error.message ?: "网络连接失败") }
            }
        }
    }

    fun bookmark(article: Article) {
        val existing = state.value.bookmarks
        val updated = if (existing.any { it.url == article.url }) existing.filterNot { it.url == article.url }
            else listOf(article) + existing
        repository.saveBookmarks(updated)
        mutable.update { it.copy(bookmarks = updated) }
    }

    fun more() {
        if (state.value.loading || state.value.loadingMore || !state.value.hasMore) return
        val channel = state.value.channel
        mutable.update { it.copy(loadingMore = true) }
        feedJob = viewModelScope.launch {
            try {
                val added = repository.more(channel)
                mutable.update { it.copy(articles = (it.articles + added).distinctBy { article -> article.url },
                    loadingMore = false, hasMore = repository.hasMore(channel)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(loadingMore = false, error = error.message) } }
        }
    }

    fun theme(value: String) {
        repository.theme = value
        mutable.update { it.copy(theme = value) }
    }

    fun fontSize(value: Float) {
        repository.fontSize = value
        mutable.update { it.copy(fontSize = value) }
    }

    fun dynamicColor(value: Boolean) {
        repository.dynamicColor = value
        mutable.update { it.copy(dynamicColor = value) }
    }

    fun open(article: Article) {
        bodyJob?.cancel()
        mutable.update { it.copy(selected = article, body = null, bodyLoading = true, bodyError = null) }
        bodyJob = viewModelScope.launch {
            try {
                val body = try { repository.body(article) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { account.articleBody(article.url) ?: throw error }
                mutable.update { it.copy(body = body, bodyLoading = false) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(bodyLoading = false, bodyError = error.message ?: "正文加载失败") }
            }
        }
    }

    fun close() {
        bodyJob?.cancel()
        mutable.update { it.copy(selected = null, body = null, bodyError = null, bodyLoading = false) }
    }
}
