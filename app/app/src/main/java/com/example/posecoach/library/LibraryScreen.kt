package com.example.posecoach.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posecoach.media.UprightBitmap
import com.example.posecoach.media.ResultLibrary
import com.example.posecoach.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * THƯ VIỆN — mọi ảnh đã lọc, xem lại bất cứ lúc nào.
 *
 * ⚠️ Ở đây chỉ có **ảnh kết quả**. Video thô và loạt ảnh gốc đã bị xoá ngay sau
 * khi lọc xong (xem `ResultLibrary.saveSession`) — đó là luật cứng, vì một đoạn
 * 30 giây Full HD nặng 60-100 MB và chỉ cần quên vài lần là máy người dùng đầy.
 */
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    var albums by remember { mutableStateOf<List<ResultLibrary.Album>>(emptyList()) }
    var thumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var open by remember { mutableStateOf<ResultLibrary.Album?>(null) }
    var confirmDelete by remember { mutableStateOf<ResultLibrary.Album?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        val list = withContext(Dispatchers.IO) { ResultLibrary.albums(context) }
        albums = list
        thumbs = withContext(Dispatchers.IO) {
            list.mapNotNull { a ->
                val f = a.photos.firstOrNull() ?: return@mapNotNull null
                decodeThumb(f)?.let { a.dir.name to it }
            }.toMap()
        }
    }

    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Thư viện còn trống", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Chụp xong một lần là ảnh đã lọc sẽ nằm ở đây",
                    fontSize = 13.sp, color = Ds.textMuted,
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.dir.name }) { album ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(Ds.rSmall))
                    .background(Ds.surface)
                    .clickable { open = album },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .background(Color.Black),
                ) {
                    thumbs[album.dir.name]?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        "${album.count} ảnh",
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(Ds.rPill))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        color = Color.White, fontSize = 10.sp,
                    )
                }
                Text(
                    formatTime(album.createdAtMs),
                    Modifier.padding(8.dp),
                    fontSize = 11.sp, color = Ds.textMuted,
                )
            }
        }
    }

    open?.let { album ->
        AlbumViewer(
            album = album,
            onDelete = { confirmDelete = album; open = null },
            onClose = { open = null },
        )
    }

    confirmDelete?.let { album ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Xoá cả buổi chụp này?") },
            text = { Text("${album.count} ảnh sẽ bị xoá hẳn, không lấy lại được.") },
            confirmButton = {
                TextButton(onClick = {
                    ResultLibrary.delete(album)
                    confirmDelete = null
                    reload++
                }) { Text("Xoá") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Giữ lại") }
            },
        )
    }
}

@Composable
private fun AlbumViewer(
    album: ResultLibrary.Album,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var photos by remember(album.dir) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    LaunchedEffect(album.dir) {
        photos = withContext(Dispatchers.IO) { album.photos.mapNotNull { decodeThumb(it, 1080) } }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${album.count} ảnh — ${formatTime(album.createdAtMs)}") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(420.dp),
            ) {
                items(photos) { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(Ds.rSmall)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Đóng") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Xoá buổi này") } },
    )
}

/** Giải mã thu nhỏ — lưới hiện hàng chục ảnh, giải mã full là hết bộ nhớ. */
private fun decodeThumb(file: File, maxSide: Int = 400): ImageBitmap? =
    // ⚠️ Qua UprightBitmap, KHÔNG gọi thẳng BitmapFactory: ảnh do CameraX ghi ra
    // cũng mang cờ xoay EXIF, bỏ qua là ảnh kết quả hiện nằm ngang.
    UprightBitmap.decode(file, shortSide = maxSide)?.asImageBitmap()

private fun formatTime(ms: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(ms))
