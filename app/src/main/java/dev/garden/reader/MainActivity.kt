package dev.garden.reader

import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.garden.reader.ui.*
import dev.garden.reader.data.Article
import dev.garden.reader.data.ContentParser
import dev.garden.reader.features.AuthorRef

class MainActivity : ComponentActivity() {
    private var notificationArticle by mutableStateOf<Article?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readNotification(intent)
        setContent { GardenApp(notificationArticle = notificationArticle) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readNotification(intent) }
    private fun readNotification(intent: Intent) {
        val url = intent.getStringExtra("article_url") ?: return
        if (!ContentParser.isCnblogsUrl(url) || !url.startsWith("https://")) return
        notificationArticle = Article(url, intent.getStringExtra("article_title")?.take(500) ?: "博客园文章", "博客园", "", "")
    }
}

private data class AuthorVisit(val id: Int, val author: AuthorRef, val origin: GardenState,
    val detail: Boolean = false, val frozenDetail: GardenState? = null)

@Composable
fun GardenApp(model: GardenViewModel = viewModel(), notificationArticle: Article? = null) {
    LaunchedEffect(notificationArticle) { notificationArticle?.let(model::open) }
    val state by model.state.collectAsStateWithLifecycle()
    val account by model.account.state.collectAsStateWithLifecycle()
    val dark = when (state.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val activity = LocalContext.current as? ComponentActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refreshAccount()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    SideEffect {
        val transparent = android.graphics.Color.TRANSPARENT
        val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        if (Build.VERSION.SDK_INT >= 29) {
            activity?.window?.isNavigationBarContrastEnforced = false
        }
    }
    GardenTheme(theme = state.theme, dynamicColor = state.dynamicColor) {
        var launched by rememberSaveable { mutableStateOf(false) }
        var showLogin by rememberSaveable { mutableStateOf(false) }
        var visits by remember { mutableStateOf(emptyList<AuthorVisit>()) }
        var nextVisit by remember { mutableIntStateOf(0) }
        val openAuthorRef: (AuthorRef) -> Unit = {
            val frozen = visits.mapIndexed { index, visit ->
                if (index == visits.lastIndex && visit.detail) visit.copy(frozenDetail = state) else visit
            }
            visits = frozen + AuthorVisit(++nextVisit, it, state)
        }
        val openAuthor: (Article) -> Unit = { article -> AuthorRef.from(article)?.let(openAuthorRef) }
        val baseState = visits.firstOrNull()?.origin ?: state
        LaunchedEffect(account.loggedIn) {
            if (account.loggedIn && showLogin) {
                showLogin = false
                if (visits.isEmpty()) state.selected?.let(model::open)
            }
        }
        Box(Modifier.fillMaxSize()) {
            GardenHome(state, model, account, onLogin = { showLogin = true },
                modifier = if (state.selected != null || visits.isNotEmpty() || showLogin) Modifier.clearAndSetSemantics { } else Modifier,
                onAuthor = openAuthor)
            baseState.selected?.let { article ->
                key(article.url) {
                    PredictiveGlassLayer(onDismiss = model::close, enabled = !showLogin && visits.isEmpty()) { back ->
                        Box(if (showLogin) Modifier.clearAndSetSemantics { } else Modifier) {
                            ReaderScreen(baseState, model, back, onLogin = { showLogin = true }, onAuthor = openAuthor,
                                visible = visits.isEmpty() && !showLogin && launched, onCommentAuthor = openAuthorRef)
                        }
                    }
                }
            }
            visits.forEachIndexed { index, visit -> key(visit.id) {
                val top = index == visits.lastIndex
                PredictiveGlassLayer(onDismiss = {
                    if (top) {
                        model.restoreReader(visit.origin)
                        visits = visits.dropLast(1)
                    }
                }, enabled = top && !showLogin && !visit.detail) { back ->
                    AuthorScreen(visit.author, model.account, {
                        model.open(it)
                        visits = visits.map { entry -> if (entry.id == visit.id) entry.copy(detail = true, frozenDetail = null) else entry }
                    }, back, { showLogin = true })
                }
                val detailState = if (top) state else visit.frozenDetail
                if (visit.detail && detailState?.selected != null) key(detailState.selected!!.url) {
                    PredictiveGlassLayer(onDismiss = {
                        if (top) {
                            model.restoreReader(visit.origin)
                            visits = visits.map { entry -> if (entry.id == visit.id) entry.copy(detail = false, frozenDetail = null) else entry }
                        }
                    }, enabled = top && !showLogin) { back ->
                        ReaderScreen(detailState, model, back, { showLogin = true }, openAuthor,
                            top && !showLogin && launched, openAuthorRef)
                    }
                }
            } }
            if (showLogin) {
                PredictiveGlassLayer(onDismiss = { showLogin = false }) { back ->
                    AccountLoginScreen(model.account, onBack = back)
                }
            }
            if (!launched) GardenLaunch { launched = true }
        }
    }
}
