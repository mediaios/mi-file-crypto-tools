package io.agora.mpk.test.mpk

import android.util.Log
import io.agora.mpk.test.encrypt.CryptoHeader
import io.agora.mpk.test.encrypt.XORCrypto
import io.agora.mpk.test.utils.Constants
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 自定义数据读取器
 * 用于实现RTC MediaPlayer的自定义数据源，支持本地文件加密解密
 */
class LocalDataReader(
    private val filePath: String,
    private val isEncrypted: Boolean = false,
    private val key: ByteArray = Constants.KEY.toByteArray(Charsets.UTF_8)
) : IMediaPlayerCustomDataProviderBase {

    companion object {
        private const val TAG = "${Constants.TAG}-CustomDataReader"
    }

    // 文件描述符
    private var fileInputStream: FileInputStream? = null

    // XOR加解密工具
    private val crypto = XORCrypto(key)

    // 当前位置
    private var currentPosition: Long = 0

    // 文件大小
    private var fileSize: Long = 0

    // 头部大小
    private val headerSize = if (isEncrypted) CryptoHeader.HEADER_SIZE else 0

    /**
     * 初始化文件
     * @return 是否初始化成功
     */
    override fun init(): Boolean {
        try {
            fileInputStream = FileInputStream(filePath)
            fileSize = fileInputStream?.channel?.size() ?: 0
            currentPosition = 0

            // 如果是加密文件，验证头部
            if (isEncrypted && fileSize >= CryptoHeader.HEADER_SIZE) {
                val headerBytes = ByteArray(CryptoHeader.HEADER_SIZE)
                val bytesRead = fileInputStream?.read(headerBytes) ?: 0

                if (bytesRead != CryptoHeader.HEADER_SIZE) {
                    Log.e(TAG, "LocalDataReader Failed to read file header")
                    release()
                    return false
                }

                try {
                    // 验证加密文件头部
                    val header = CryptoHeader(headerBytes)
                    Log.d(TAG, "LocalDataReader File header verified: $header")

                    // 重置文件位置到头部之后
                    fileInputStream?.close()
                    fileInputStream = FileInputStream(filePath)
                    fileInputStream?.skip(headerSize.toLong())
                    currentPosition = headerSize.toLong()
                } catch (e: Exception) {
                    Log.e(TAG, "LocalDataReader Invalid encrypted file header: ${e.message}")
                    release()
                    return false
                }
            }

            Log.d(TAG, "LocalDataReader File opened successfully: $filePath, size: $fileSize")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "LocalDataReader Failed to open file: ${e.message}")
            release()
            return false
        }
    }

    override fun stop() {
        // local file nothing to do
    }

    /**
     * 关闭文件
     */
    override fun release() {
        try {
            if (fileInputStream != null) {
                fileInputStream?.close()
                fileInputStream = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "LocalDataReader Failed to close file: ${e.message}")
        }
    }

    /**
     * 读取数据
     */
    override fun onReadData(buffer: ByteBuffer?, bufferSize: Int): Int {
        if (fileInputStream == null || bufferSize <= 0) {
            return 0
        }

        try {
            // 计算要读取的实际字节数
            val bytesToRead = if (currentPosition + bufferSize > fileSize) {
                (fileSize - currentPosition).toInt()
            } else {
                bufferSize
            }

            if (bytesToRead <= 0) {
                return 0
            }

            // 读取数据
            val readDataArray = ByteArray(bytesToRead)
            val bytesRead = fileInputStream?.read(
                readDataArray,
                0, bytesToRead
            ) ?: 0

            if (bytesRead > 0) {
                currentPosition += bytesRead

                // 如果是加密文件，解密读取的数据
                if (isEncrypted) {
                    // 计算实际数据偏移量（去除头部）
                    val dataOffset = currentPosition - bytesRead - headerSize

                    // 直接在原数组上解密数据，避免创建新的字节数组
                    crypto.inplaceDecrypt(readDataArray, 0, bytesRead, dataOffset.toInt())

                    // 将解密后的数据复制回buffer
                    buffer?.put(readDataArray, 0, bytesRead)
                } else {
                    buffer?.put(readDataArray, 0, bytesRead)
                }
            }

            return bytesRead
        } catch (e: IOException) {
            Log.e(TAG, "LocalDataReader Error reading data: ${e.message}")
            return -1
        }
    }


    /**
     * 寻址操作
     */
    override fun onSeek(offset: Long, whence: Int): Long {
        Log.d(TAG, "LocalDataReader onSeek called, offset: $offset, whence: $whence")
        if (fileInputStream == null) {
            return -1
        }

        try {
            val absolutePosition = when (whence) {
                0 -> {
                    // 从文件开始处偏移
                    headerSize + offset
                }

                1 -> {
                    // 从当前位置偏移
                    currentPosition + offset
                }

                2 -> {
                    // 从文件末尾偏移
                    fileSize + offset
                }

                65536 -> {
                    // 返回文件大小
                    return fileSize - headerSize
                }

                else -> {
                    Log.e(TAG, "Unknown seek mode: $whence")
                    return -1
                }
            }

            // 规范化位置，确保在有效范围内
            val normalizedPosition = when {
                absolutePosition < headerSize -> headerSize.toLong()
                absolutePosition > fileSize -> fileSize
                else -> absolutePosition
            }

            // 设置文件位置
            fileInputStream?.channel?.position(normalizedPosition)
            currentPosition = normalizedPosition

            // 返回相对于数据开始的位置（去除头部）
            return normalizedPosition - headerSize
        } catch (e: IOException) {
            Log.e(TAG, "Error seeking: ${e.message}")
            return -1
        }
    }

    override fun getTotalSize(): Long {
        return fileSize - headerSize
    }

    override fun getLoadedSize(): Long {
        return fileSize - headerSize
    }
} 