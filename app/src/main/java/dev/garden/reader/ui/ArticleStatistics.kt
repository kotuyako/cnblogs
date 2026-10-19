package dev.garden.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garden.reader.R
import dev.garden.reader.data.Article

/** The exact comment/digg/views paths used in the public website SVG sprite. */
@Composable
internal fun ArticleStatistics(article: Article, modifier: Modifier = Modifier, onComments: (() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(R.drawable.web_comment, article.comments, "评论"),
            Triple(R.drawable.web_digg, article.likes, "点赞"), Triple(R.drawable.web_views, article.views, "阅读"))
            .forEachIndexed { index, (icon, value, label) ->
                Row(Modifier.semantics { contentDescription = "$label ${value ?: "未知"}" }
                    .then(if (index == 0 && onComments != null) Modifier.clickable(onClick = onComments) else Modifier),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value?.toString() ?: "—", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
    }
}
