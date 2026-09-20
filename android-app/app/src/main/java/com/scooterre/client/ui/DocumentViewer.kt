package com.scooterre.client.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scooterre.client.protocol.DocumentStore
import com.scooterre.client.protocol.ScooterDocument
import com.scooterre.client.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PDF_RENDER_WIDTH_PX = 1600

/** Full-screen document display for showing it to someone: black background, swipe between pages,
 * pinch or double-tap to zoom. Screen-on and full brightness are handled by the caller (ScooterApp),
 * since they belong to the window rather than to this composable. */
@Composable
fun DocumentViewerScreen(state: UiState, actions: DocumentActions, onBack: () -> Unit) {
    val s = strings(state.language)
    val mac = state.documentsMac
    val doc = state.documents.firstOrNull { it.id == state.viewerDocId }
    if (mac == null || doc == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val store = remember { DocumentStore(context) }

    val pageCount by produceState(if (doc.isPdf) 0 else doc.pages.size, doc) {
        value = if (doc.isPdf) withContext(Dispatchers.IO) { pdfPageCount(store.file(mac, doc, 0)) } else doc.pages.size
    }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    var zoomed by remember { mutableStateOf(false) }
    val addPage = rememberCameraLauncher { file -> actions.onAppendPhoto(doc.id, file) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (pageCount > 0) {
            HorizontalPager(state = pagerState, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
                DocumentPage(
                    file = store.file(mac, doc, if (doc.isPdf) 0 else page),
                    pdfPage = if (doc.isPdf) page else null,
                    onZoomedChange = { zoomed = it },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0x99000000)).statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Text("←", fontSize = 22.sp, color = Color.White) }
            Text(
                doc.name,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            if (pageCount > 1) {
                Text("${pagerState.currentPage + 1}/$pageCount", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
            }
            if (!doc.isPdf) {
                TextButton(onClick = { addPage() }) { Text(s.docsAddPageShort, color = Color.White) }
            }
        }
    }
}

@Composable
private fun DocumentPage(file: File, pdfPage: Int?, onZoomedChange: (Boolean) -> Unit) {
    val bitmap by produceState<Bitmap?>(null, file, pdfPage) {
        value = withContext(Dispatchers.IO) {
            runCatching { if (pdfPage == null) BitmapFactory.decodeFile(file.absolutePath) else renderPdfPage(file, pdfPage) }.getOrNull()
        }
    }
    val loaded = bitmap
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
    } else {
        ZoomableImage(loaded, onZoomedChange)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(bitmap: Bitmap, onZoomedChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, sc: Float): Offset {
        val maxX = size.width * (sc - 1f) / 2f
        val maxY = size.height * (sc - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset = clamp(offset + panChange, scale)
    }
    LaunchedEffect(scale) { onZoomedChange(scale > 1.01f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // Panning only counts while zoomed in, so a one-finger swipe at normal size still turns the page.
            .transformable(transformState, canPan = { scale > 1.01f })
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

private fun pdfPageCount(file: File): Int = runCatching {
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        renderer.pageCount
    } finally {
        renderer.close()
        pfd.close()
    }
}.getOrDefault(0)

private fun renderPdfPage(file: File, index: Int): Bitmap {
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        val page = renderer.openPage(index)
        try {
            val height = (PDF_RENDER_WIDTH_PX.toFloat() * page.height / page.width).toInt()
            val bitmap = Bitmap.createBitmap(PDF_RENDER_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    } finally {
        renderer.close()
        pfd.close()
    }
}
