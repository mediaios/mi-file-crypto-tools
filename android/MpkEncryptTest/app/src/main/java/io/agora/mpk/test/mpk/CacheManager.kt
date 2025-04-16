package io.agora.mpk.test.mpk

import android.content.Context
import android.net.Uri
import android.util.Log
import io.agora.mpk.test.encrypt.CryptoHeader
import io.agora.mpk.test.utils.Constants
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

/**
 * 媒体缓存管理器
 * 负责处理和管理媒体文件的缓存逻辑
 */
class CacheManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "${Constants.TAG}-CacheManager"

        // 文件名格式：filename_size.ext
        private val FILE_SIZE_PATTERN = Pattern.compile("(.+)_SIZE_(\\d+)(\\..+)?$")
    }

    // 缓存目录
    private val cacheDir by lazy { File(context.externalCacheDir, "media_cache") }

    // 缓存文件
    private var cacheFile: File? = null

    // 文件总大小
    private val totalBytes = AtomicLong(-1)

    // 已下载大小
    private val downloadedBytes = AtomicLong(0)

    // 是否已验证头部
    private val headerVerified = AtomicBoolean(false)

    /**
     * 初始化缓存管理器
     */
    fun init() {
        // 确保缓存目录存在
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    /**
     * 初始化缓存文件
     * @param url 媒体文件URL
     * @return 是否成功初始化
     */
    fun initCacheFile(url: String): Boolean {
        try {
            totalBytes.set(0L)
            downloadedBytes.set(0L)

            // 获取基本文件名
            val uri = Uri.parse(url)
            val baseFileName = uri.lastPathSegment ?: "cached_${url.hashCode()}"
            val extension = baseFileName.substringAfterLast('.', "")
            val nameWithoutExt = baseFileName.substringBeforeLast('.', baseFileName)

            // 查找缓存目录中是否有匹配的文件
            val cachedFiles = cacheDir.listFiles { file ->
                file.name.startsWith(nameWithoutExt) &&
                        (extension.isEmpty() || file.name.endsWith(extension))
            }

            // 检查是否有之前缓存的文件
            cachedFiles?.forEach { file ->
                val matcher = FILE_SIZE_PATTERN.matcher(file.name)
                if (matcher.find()) {
                    val fileSize = matcher.group(2)?.toLongOrNull() ?: -1
                    if (fileSize > 0) {
                        // 找到带有大小信息的缓存文件
                        cacheFile = file
                        totalBytes.set(fileSize)
                        downloadedBytes.set(file.length())
                        Log.d(
                            TAG,
                            "Found cache file with size info: ${file.name}, total size: $fileSize, downloaded: ${file.length()}"
                        )
                        return true
                    }
                }
            }

            if (cacheFile == null) {
                val fileName = if (extension.isEmpty()) {
                    "$baseFileName.mp4"
                } else {
                    "$baseFileName.$extension"
                }
                cacheFile = File(cacheDir, fileName)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init cache file: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * 获取缓存文件
     * @return 缓存文件对象
     */
    fun getCacheFile(): File? {
        return cacheFile
    }

    /**
     * 获取已缓存的数据大小
     * @return 已缓存的字节数
     */
    fun getDownloadedBytes(): Long {
        return downloadedBytes.get()
    }

    /**
     * 设置已下载的字节数
     * @param bytes 已下载的字节数
     */
    fun setDownloadedBytes(bytes: Long) {
        downloadedBytes.set(bytes)
    }

    /**
     * 增加已下载的字节数
     * @param bytes 要增加的字节数
     * @return 增加后的值
     */
    fun addDownloadedBytes(bytes: Long): Long {
        return downloadedBytes.addAndGet(bytes)
    }

    /**
     * 获取文件总大小
     * @return 文件总字节数
     */
    fun getTotalBytes(): Long {
        return totalBytes.get()
    }

    /**
     * 设置文件总大小
     * @param bytes 文件总字节数
     */
    fun setTotalBytes(bytes: Long) {
        totalBytes.set(bytes)
    }

    /**
     * 更新缓存文件名，添加文件大小标识
     * @param fileSize 文件大小
     */
    fun updateCacheFileName(fileSize: Long) {
        val currentFile = cacheFile ?: return

        // 如果文件名已经包含大小信息，则不需要更新
        if (FILE_SIZE_PATTERN.matcher(currentFile.name).matches()) {
            return
        }

        val oldName = currentFile.name
        val extension = oldName.substringAfterLast('.', "")
        val nameWithoutExt = oldName.substringBeforeLast('.', oldName)

        val newName = if (extension.isNotEmpty()) {
            "${nameWithoutExt}_SIZE_${fileSize}.${extension}"
        } else {
            "${nameWithoutExt}_SIZE_${fileSize}"
        }

        val newFile = File(cacheDir, newName)

        // 如果文件已经存在且有内容，复制内容
        if (currentFile.exists() && currentFile.length() > 0) {
            val success = currentFile.renameTo(newFile)
            if (success) {
                cacheFile = newFile
                Log.d(TAG, "Updated cache file name to include size: $newName")
            } else {
                Log.e(TAG, "Failed to rename cache file")
            }
        } else {
            // 如果文件不存在或为空，直接使用新文件
            cacheFile = newFile
            Log.d(TAG, "Set new cache file name with size: $newName")
        }
    }

    /**
     * 读取缓存文件的头部
     * @param isEncrypted 是否是加密文件
     * @return 解析的CryptoHeader对象，如果失败返回null
     */
    fun readCachedHeader(isEncrypted: Boolean): CryptoHeader? {
        val file = cacheFile ?: return null

        if (!isEncrypted || !file.exists() || file.length() < CryptoHeader.HEADER_SIZE) {
            return null
        }

        try {
            val inputStream = FileInputStream(file)
            val headerBytes = ByteArray(CryptoHeader.HEADER_SIZE)
            val bytesRead = inputStream.read(headerBytes)
            inputStream.close()

            if (bytesRead != CryptoHeader.HEADER_SIZE) {
                return null
            }

            return CryptoHeader(headerBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read header: ${e.message}")
            return null
        }
    }

    /**
     * 设置头部验证状态
     * @param verified 是否验证通过
     */
    fun setHeaderVerified(verified: Boolean) {
        headerVerified.set(verified)
    }

    /**
     * 获取头部验证状态
     * @return 是否已验证头部
     */
    fun isHeaderVerified(): Boolean {
        return headerVerified.get()
    }

    /**
     * 删除当前缓存文件
     */
    fun deleteCacheFile() {
        cacheFile?.delete()
        cacheFile = null
    }

    /**
     * 清理所有缓存文件
     */
    fun clearAllCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}