package com.focuslist.app.ui.note

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL

/** Markdown 行内链接：[文字](地址) */
private val linkRegex = Regex("""\[(.+?)]\((.+?)\)""")

/** Markdown 行内加粗：**文字** */
private val boldRegex = Regex("""\*\*(.+?)\*\*""")

/** Markdown 行内斜体：*文字* */
private val italicRegex = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")

/** Markdown 图片：![描述](地址)，支持 http(s) 在线图片与 content:// 本地图 */
private val imageRegex = Regex("""^!\[(.*?)]\((.+?)\s*\)$""")

/** 点击链接的注解标记 */
private const val LINK_TAG = "URL"

/**
 * 轻量 Markdown 渲染：支持 #/##/### 标题、**加粗**、*斜体*、- 列表、> 引用、
 * [链接](url) 与 ![图片](url)（在线图片或本地图 URI）。
 *
 * @param markdown Markdown 文本
 * @param modifier 布局修饰符
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        markdown.lines().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isBlank() -> Unit
                imageRegex.matches(line) -> {
                    val m = imageRegex.find(line)!!
                    NoteImage(uri = m.groupValues[2], alt = m.groupValues[1])
                }
                line.startsWith("### ") -> InlineMarkdownText(
                    text = line.removePrefix("### "),
                    style = MaterialTheme.typography.titleSmall
                )
                line.startsWith("## ") -> InlineMarkdownText(
                    text = line.removePrefix("## "),
                    style = MaterialTheme.typography.titleMedium
                )
                line.startsWith("# ") -> InlineMarkdownText(
                    text = line.removePrefix("# "),
                    style = MaterialTheme.typography.titleLarge
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text(text = "•  ", style = MaterialTheme.typography.bodyMedium)
                    InlineMarkdownText(text = line.substring(2), modifier = Modifier.weight(1f))
                }
                line.startsWith("> ") -> InlineMarkdownText(
                    text = line.removePrefix("> "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> InlineMarkdownText(text = line)
            }
        }
    }
}

/**
 * 行内 Markdown 文本：链接可点击，加粗/斜体加样式。
 *
 * @param text 单行文本
 * @param style 基础样式
 * @param color 文字颜色
 * @param modifier 布局修饰符
 */
@Composable
private fun InlineMarkdownText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, color) { buildInlineMarkdown(text, color) }
    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = LINK_TAG, start = offset, end = offset)
                .firstOrNull()?.let { uriHandler.openUri(it.item) }
        }
    )
}

/**
 * 构建行内 Markdown 富文本：先切出链接，再对普通片段应用加粗、斜体。
 *
 * @param text 原文本
 * @param linkColor 链接颜色
 * @return 富文本
 */
private fun buildInlineMarkdown(text: String, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var rest = text
        while (true) {
            val match = linkRegex.find(rest) ?: break
            appendStyled(rest.substring(0, match.range.first))
            val linkText = match.groupValues[1]
            val linkUrl = match.groupValues[2]
            pushStringAnnotation(tag = LINK_TAG, annotation = linkUrl)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                appendStyled(linkText)
            }
            pop()
            rest = rest.substring(match.range.last + 1)
        }
        appendStyled(rest)
    }
}

/** 追加文本并应用加粗/斜体样式。 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStyled(text: String) {
    var rest = text
    while (true) {
        val match = boldRegex.find(rest) ?: run {
            appendItalicStyled(rest)
            return
        }
        appendItalicStyled(rest.substring(0, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendItalicStyled(match.groupValues[1])
        }
        rest = rest.substring(match.range.last + 1)
    }
}

/** 追加文本并应用斜体样式。 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendItalicStyled(text: String) {
    var rest = text
    while (true) {
        val match = italicRegex.find(rest) ?: run {
            append(rest)
            return
        }
        append(rest.substring(0, match.range.first))
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(match.groupValues[1])
        }
        rest = rest.substring(match.range.last + 1)
    }
}

/**
 * 备注图片：支持 http(s) 在线图片与 content:// 本地图，自动按 2048px 采样降载，
 * 加载中显示进度圈，失败显示占位提示。
 *
 * @param uri 图片地址（http(s) 或 content://）
 * @param alt 图片描述（无障碍）
 * @param modifier 布局修饰符
 */
@Composable
fun NoteImage(uri: String, alt: String = "", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        failed = false
        bitmap = null
        val loaded = withContext(Dispatchers.IO) { decodeSampledBitmap(context, uri) }
        if (loaded != null) {
            bitmap = loaded.asImageBitmap()
        } else {
            failed = true
        }
    }

    val ratio = bitmap?.let { it.width.toFloat() / it.height.toFloat() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.padding(vertical = 24.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!,
                contentDescription = alt.ifBlank { "备注图片" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            failed -> Text(
                text = "🖼 图片加载失败",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
            else -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }
    }
}

/**
 * 解码图片（按 2048px 上限采样，避免大图 OOM）。
 *
 * @param context 上下文
 * @param uri 图片地址
 * @return 位图；失败返回 null
 */
private fun decodeSampledBitmap(context: Context, uri: String): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }
}

/** 打开图片流：在线地址走网络，其余走内容解析器。 */
private fun openStream(context: Context, uri: String): InputStream? {
    return if (uri.startsWith("http")) {
        URL(uri).openStream()
    } else {
        context.contentResolver.openInputStream(Uri.parse(uri))
    }
}

/**
 * 用系统应用打开备注附件（图片查看器 / 视频播放器）。
 *
 * @param context 上下文
 * @param uri 附件 URI
 * @param type 附件类型（IMAGE / VIDEO）
 */
fun openAttachment(context: Context, uri: String, type: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), if (type == "VIDEO") "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // 无可打开的应用时静默忽略
    }
}

/** 备注 Markdown 语法剥离（列表预览用）：去掉标题井号、加粗星号、图片/链接语法。
 *
 * @param markdown 原文
 * @return 纯文本预览
 */
fun markdownPreview(markdown: String): String {
    return markdown
        .replace(Regex("""^#{1,6}\s+"""), "")
        .replace(boldRegex, "$1")
        .replace(italicRegex, "$1")
        .replace(imageRegex, "[图片]")
        .replace(linkRegex, "$1")
        .replace(Regex("""^>\s+"""), "")
        .trim()
}
