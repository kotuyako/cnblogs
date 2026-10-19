package dev.garden.reader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private object ImageStore {
    val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    val slots = Semaphore(4)
    suspend fun load(url: String): Bitmap = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        slots.withPermit {
            var target = url
            repeat(5) {
                val uri = URI(target)
                require(uri.scheme == "https" && uri.userInfo == null && !uri.host.isNullOrBlank())
                val connection = uri.toURL().openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 12_000; connection.readTimeout = 15_000
                    // Image hosts never receive account credentials.
                    connection.setRequestProperty("User-Agent", "GardenReader/1.2 Android")
                    connection.setRequestProperty("Referer", "https://www.cnblogs.com/")
                    if (connection.responseCode in listOf(301,302,303,307,308)) {
                        target = uri.resolve(connection.getHeaderField("Location") ?: error("图片地址缺失")).toString()
                    } else {
                        check(connection.responseCode == 200)
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer); if (count < 0) break
                                check(output.size() + count <= 12 * 1024 * 1024)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        require(bounds.outWidth > 0 && bounds.outHeight > 0)
                        var sample = 1
                        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 6000 ||
                            (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > 4_000_000) sample *= 2
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                            BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("图片解码失败")
                        cache.put(url, bitmap)
                        return@withPermit bitmap
                    }
                } finally { connection.disconnect() }
            }
            error("图片重定向过多")
        }
    }
}

@Composable
fun NativeAvatar(url: String?, name: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = try { url?.let { ImageStore.load(it) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
    }
    GlassPanel(modifier, shape = CircleShape, onClick = onClick) {
        val photo = bitmap
        if (photo != null) Image(photo.asImageBitmap(), "$name 的头像",
            Modifier.fillMaxSize().padding(3.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        else Text(name.take(1).ifBlank { "园" }, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun NativeImage(url: String, description: String?, modifier: Modifier = Modifier, crop: Boolean = false) {
    var retry by remember(url) { mutableIntStateOf(0) }
    val loaded by produceState<Result<Bitmap>?>(null, url, retry) {
        value = try { Result.success(ImageStore.load(url)) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { Result.failure(error) }
    }
    val bitmap = loaded?.getOrNull()
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), description,
            modifier.then(if (crop) Modifier else Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)),
            contentScale = if (crop) ContentScale.Crop else ContentScale.FillWidth)
    } else Box(modifier.then(if (crop) Modifier else Modifier.fillMaxWidth().height(150.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable { retry++ }, contentAlignment = Alignment.Center) {
        if (loaded == null) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.BrokenImage, null, Modifier.size(20.dp))
            Text(" 图片暂不可用 · 轻点重试", style = MaterialTheme.typography.labelSmall)
        }
    }
}
