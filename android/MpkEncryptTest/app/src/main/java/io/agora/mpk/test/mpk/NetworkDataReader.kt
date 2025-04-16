package io.agora.mpk.test.mpk

import android.content.Context
import android.util.Log
import io.agora.mpk.test.encrypt.CryptoHeader
import io.agora.mpk.test.encrypt.XORCrypto
import io.agora.mpk.test.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 网络数据读取器
 * 用于处理加密的网络视频流
 */
class NetworkDataReader(
    private val context: Context,
    private val url: String,
    private val isEncrypted: Boolean = true
) : IMediaPlayerCustomDataProviderBase {
    companion object {
        private const val TAG = "${Constants.TAG}-NetworkDataReader"
        private const val BUFFER_SIZE = 8192 // 8KB
        private const val DOWNLOAD_CHUNK_SIZE = 128 * 1024 // 128KB
    }

    // OkHttpClient实例
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // 下载线程池
    private val downloadExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // 缓存管理器
    private val cacheManager = CacheManager(context)

    // 下载状态
    private val isDownloading = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)

    // 当前位置
    private val currentPosition = AtomicLong(0)

    // 加密解密工具
    private val crypto = XORCrypto()

    // 头部大小
    private val headerSize = if (isEncrypted) CryptoHeader.HEADER_SIZE else 0

    override fun init(): Boolean {
        try {
            isStopped.set(false)

            // 初始化缓存管理器
            cacheManager.init()

            currentPosition.set(headerSize.toLong())

            // 初始化缓存文件
            if (!cacheManager.initCacheFile(url)) {
                Log.e(TAG, "Failed to initialize cache file")
                return false
            }

            // 如果缓存文件存在，验证头部
            val cacheFile = cacheManager.getCacheFile()
            if (cacheFile?.exists() == true) {
                val cachedSize = cacheFile.length()
                cacheManager.setDownloadedBytes(cachedSize)

                // 如果缓存文件存在，验证头部并设置总大小
                if (isEncrypted && cachedSize >= CryptoHeader.HEADER_SIZE) {
                    val header = cacheManager.readCachedHeader(isEncrypted)
                    if (header != null) {
                        cacheManager.setHeaderVerified(true)
                        Log.d(
                            TAG,
                            "Cache header verified, original size: ${header.getOriginalSize()}"
                        )
                    }
                }
            }

            // 开始下载
            startDownload()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * 安排下载指定范围
     */
    private fun scheduleDownloadRange(start: Long, end: Long) {
        if (!isDownloading.get() || isStopped.get()) {
            startDownload()
            return
        }

        // 已安排下载任务，无需额外操作
    }

    /**
     * 开始下载
     */
    private fun startDownload() {
        Log.d(TAG, "Starting download:$url isDownloading:${isDownloading.get()}")
        if (isDownloading.getAndSet(true) || isStopped.get()) {
            return
        }

        downloadExecutor.execute {
            try {
                if (!getFileInfo()) {
                    Log.e(TAG, "无法获取文件信息，将使用默认值")
                    return@execute
                }

                if (isStopped.get()) {
                    isDownloading.set(false)
                    return@execute
                }

                // 如果缓存已完整，不需要下载
                if (cacheManager.getTotalBytes() > 0 && cacheManager.getDownloadedBytes() >= cacheManager.getTotalBytes()) {
                    Log.d(TAG, "Cache is complete, no need to download")
                    isDownloading.set(false)
                    return@execute
                }

                downloadFromPosition(cacheManager.getDownloadedBytes())

                isDownloading.set(false)
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}")
                e.printStackTrace()
            } finally {
                isDownloading.set(false)
                Log.d(TAG, "Download completed or stopped")
            }
        }
    }

    private fun getFileInfo(): Boolean {
        try {
            val requestBuilder = Request.Builder().url(url)
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val responseCode = response.code
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    Log.e(TAG, "HTTP error: $responseCode")
                    return false
                }

                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1
                if (contentLength > 0) {
                    if (cacheManager.getTotalBytes() == 0L) {
                        cacheManager.setTotalBytes(contentLength)
                        Log.d(TAG, "File size: $contentLength bytes")

                        // 更新缓存文件名称，加入文件长度标识
                        cacheManager.updateCacheFileName(contentLength)
                    } else {
                        if (contentLength != cacheManager.getTotalBytes()) {
                            Log.e(TAG, "File size mismatch maybe file change")
                            // 删除缓存文件
                            cacheManager.deleteCacheFile()
                            // 重新初始化
                            cacheManager.initCacheFile(url)
                            cacheManager.updateCacheFileName(contentLength)
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file info: ${e.message}")
            return false
        }
    }

    /**
     * 从指定位置开始下载
     */
    private fun downloadFromPosition(position: Long) {
        Log.d(TAG, "Downloading from position: $position")
        try {
            // 确保缓存文件存在
            val file = cacheManager.getCacheFile() ?: return
            val fileOutputStream = FileOutputStream(file, position > 0)

            // 创建带有Range头的请求
            val requestBuilder = Request.Builder().url(url)
            if (position > 0) {
                requestBuilder.header("Range", "bytes=$position-")
            }

            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                val responseCode = response.code
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    Log.e(TAG, "HTTP error: $responseCode")
                    return
                }

                // 读取流数据
                response.body?.byteStream()?.use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped.get()) {
                            break
                        }

                        Log.d(
                            TAG,
                            "Downloaded: ${cacheManager.getDownloadedBytes()} / ${cacheManager.getTotalBytes()}"
                        )
                        // 写入缓存文件
                        fileOutputStream.write(buffer, 0, bytesRead)

                        // 更新进度
                        cacheManager.addDownloadedBytes(bytesRead.toLong())

                        // 验证头部（如果需要）
                        if (isEncrypted && !cacheManager.isHeaderVerified() && cacheManager.getDownloadedBytes() >= CryptoHeader.HEADER_SIZE) {
                            validateHeader()
                        }
                    }
                }
            }

            fileOutputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
        }
    }

    /**
     * 验证文件头部
     */
    private fun validateHeader() {
        try {
            val header = cacheManager.readCachedHeader(isEncrypted)

            if (header != null) {
                cacheManager.setHeaderVerified(true)
                Log.d(TAG, "Header verified, total size: ${cacheManager.getTotalBytes()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate header: ${e.message}")
        }
    }

    /**
     * 停止下载
     */
    override fun stop() {
        isStopped.set(true)
        isDownloading.set(false)
    }

    override fun release() {
        // 设置停止标志，避免开始新任务
        isStopped.set(true)

        // 关闭线程池，但等待任务完成
        try {
            // 尝试优雅关闭线程池，等待10秒
            downloadExecutor.shutdown()
            if (!downloadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                // 如果等待超时，强制关闭
                downloadExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            // 如果等待过程被中断，强制关闭
            downloadExecutor.shutdownNow()
            Thread.currentThread().interrupt()
            Log.e(TAG, "Thread pool shutdown interrupted: ${e.message}")
        }
    }

    override fun getTotalSize(): Long {
        return cacheManager.getTotalBytes() - headerSize
    }

    override fun getLoadedSize(): Long {
        return cacheManager.getDownloadedBytes() - headerSize
    }

    override fun onReadData(buffer: ByteBuffer?, bufferSize: Int): Int {
        if (isStopped.get()) {
            return 0
        }

        try {
            // 如果当前位置超出已下载大小，等待下载
            while (currentPosition.get() + bufferSize >= cacheManager.getDownloadedBytes() &&
                isDownloading.get() && !isStopped.get()
            ) {
                try {
                    Thread.sleep(100) // 短暂等待下载进度
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            // 如果已经停止，直接返回
            if (isStopped.get()) {
                return 0
            }

            // 计算可读取的字节数
            val availableBytes = (cacheManager.getDownloadedBytes() - currentPosition.get()).toInt()
            if (availableBytes <= 0) {
                return 0
            }

            val bytesToRead = bufferSize

            // 从缓存文件读取数据
            val file = cacheManager.getCacheFile() ?: return 0
            val fileInputStream = FileInputStream(file)

            try {
                // 跳转到当前位置
                fileInputStream.skip(currentPosition.get())

                val readDataArray = ByteArray(bytesToRead)
                // 读取数据
                val bytesRead = fileInputStream.read(readDataArray, 0, bytesToRead)

                if (bytesRead > 0) {
                    currentPosition.addAndGet(bytesRead.toLong())
                    // 如果是加密文件，解密数据
                    if (isEncrypted) {
                        val dataOffset = currentPosition.get() - bytesRead - headerSize

                        // 直接在原数组上解密数据，避免创建新的字节数组
                        crypto.inplaceDecrypt(readDataArray, 0, bytesRead, dataOffset.toInt())

                        // 将解密后的数据复制回buffer
                        buffer?.put(readDataArray, 0, bytesRead)
                    } else {
                        buffer?.put(readDataArray, 0, bytesRead)
                    }
                }

                return bytesRead
            } finally {
                fileInputStream.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading data: ${e.message}")
            return -1
        }
    }

    override fun onSeek(offset: Long, whence: Int): Long {
        if (isStopped.get()) {
            return -1
        }

        Log.d(TAG, "Seek: offset=$offset, whence=$whence")

        try {
            val absolutePosition = when (whence) {
                0 -> { // SEEK_SET: 设置绝对位置
                    headerSize + offset
                }

                1 -> { // SEEK_CUR: 相对当前位置
                    currentPosition.get() + offset
                }

                2 -> { // SEEK_END: 相对文件末尾
                    if (cacheManager.getTotalBytes() <= 0) {
                        return -1 // 未知大小
                    }
                    cacheManager.getTotalBytes() + offset
                }

                65536 -> { // AVSEEK_SIZE: 返回文件大小
                    return if (cacheManager.getTotalBytes() > 0) {
                        if (isEncrypted) cacheManager.getTotalBytes() - headerSize else cacheManager.getTotalBytes()
                    } else {
                        -1
                    }
                }

                else -> return -1
            }

            // 规范化位置，确保在有效范围内
            val normalizedPosition = when {
                absolutePosition < headerSize -> headerSize.toLong()
                cacheManager.getTotalBytes() in 1..<absolutePosition -> cacheManager.getTotalBytes()
                else -> absolutePosition
            }

            // 如果请求的位置超出当前下载范围，触发下载
            if (normalizedPosition > cacheManager.getDownloadedBytes()) {
                scheduleDownloadRange(normalizedPosition, normalizedPosition + DOWNLOAD_CHUNK_SIZE)
            }

            // 设置当前位置
            currentPosition.set(normalizedPosition)

            // 返回相对于数据开始的位置（去除头部）
            return normalizedPosition - headerSize
        } catch (e: Exception) {
            Log.e(TAG, "Error during seek: ${e.message}")
            return -1
        }
    }
} 
