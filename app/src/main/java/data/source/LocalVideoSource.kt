package data.source

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.paging.PagingSource
import androidx.paging.PagingState
import data.model.VideoItem
import java.io.File
import java.io.FileOutputStream


class LocalVideoSource(
    private val contentResolver: ContentResolver,
    private val context: Context
) : PagingSource<Int, VideoItem>() {

    override fun getRefreshKey(state: PagingState<Int, VideoItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1) ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VideoItem> {
        val page   = params.key ?: 0
        val limit  = params.loadSize
        val offset = page * limit

        val list = mutableListOf<VideoItem>()

        //指定需要的列
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        //排序：按添加时间倒序
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        // 3. 查询（只按排序拿全部）
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            // 4. 手动跳到 offset 行
            if (cursor.moveToPosition(offset)) {
                // 5. 连续读 limit 条
                var left = limit
                do {
                    val id   = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    val dur  = cursor.getLong(durCol)
                    list += VideoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        name = name,
                        durationMs = dur,
                        thumbnailUri = getVideoThumbnail(id)
                    )
                    left--
                } while (left > 0 && cursor.moveToNext())
            }
        }

        // 6. 返回分页结果
        return LoadResult.Page(
            data = list,
            prevKey = if (page == 0) null else page - 1,
            nextKey = if (list.isEmpty()) null else page + 1
        )
    }

    private fun getVideoThumbnail(
        videoId: Long,
        width: Int = 512,
        height: Int = 512
    ): Uri? {
        val bmp = try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoId
            )
            contentResolver.loadThumbnail(uri, Size(width, height), null)

        } catch (t: Throwable) {
            null
        } ?: return null

        // 把 Bitmap 保存到私有缓存目录
        return saveThumbToCache(bmp)
    }


    private fun saveThumbToCache(bitmap: Bitmap): Uri? {
        return try {
            val file = File(
                context.cacheDir,
                "thumb_${System.currentTimeMillis()}.jpg"
            )
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


}