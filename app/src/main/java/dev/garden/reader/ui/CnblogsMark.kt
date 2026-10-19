package dev.garden.reader.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.garden.reader.R

@Composable
fun CnblogsMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.cnblogs_mark_vector), "博客园标志", modifier)
}
