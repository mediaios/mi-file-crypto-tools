package io.agora.mpk.test.encrypt

import android.util.Log
import io.agora.mpk.test.utils.Constants
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * XOR加密解密工具类
 * 使用XOR操作加密和解密文件或数据
 */
class XORCrypto(
    // 加密密钥，默认为8字节密钥
    private val key: ByteArray = Constants.KEY.toByteArray(Charsets.UTF_8)
) {
    companion object {
        private const val TAG = "${Constants.TAG}-XORCrypto"
        private const val BUFFER_SIZE = 8192 // 8KB缓冲区
    }

    /**
     * 加密文件
     * @param sourceFile 源文件
     * @param destinationFile 目标文件
     * @return 是否加密成功
     */
    @Throws(IOException::class)
    fun encryptFile(sourceFile: File, destinationFile: File): Boolean {
        // 检查文件大小
        val fileSize = sourceFile.length()

        if (fileSize < BUFFER_SIZE * 10) { // 对于小文件，使用原来的内存方式处理
            try {
                // 读取源文件
                val sourceData = sourceFile.readBytes()

                // 加密数据
                val encryptedData = encrypt(sourceData)

                // 写入目标文件
                FileOutputStream(destinationFile).use { it.write(encryptedData) }

                Log.d(TAG, "成功加密小文件: ${sourceFile.path} 到 ${destinationFile.path}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "加密小文件失败: ${e.message}", e)
                throw IOException("加密文件失败", e)
            }
        } else { // 对于大文件，使用流式处理
            try {
                BufferedInputStream(FileInputStream(sourceFile)).use { inputStream ->
                    BufferedOutputStream(FileOutputStream(destinationFile)).use { outputStream ->
                        // 写入头部
                        val header = CryptoHeader(fileSize)
                        outputStream.write(header.serialize())

                        // 加密并写入文件内容
                        encryptStream(inputStream, outputStream)

                        outputStream.flush()
                    }
                }

                Log.d(TAG, "成功流式加密大文件: ${sourceFile.path} 到 ${destinationFile.path}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "流式加密大文件失败: ${e.message}", e)
                throw IOException("加密文件失败", e)
            }
        }
    }

    /**
     * 流式加密数据
     * @param inputStream 输入流
     * @param outputStream 输出流
     */
    @Throws(IOException::class)
    private fun encryptStream(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var position = 0

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            // 对缓冲区中的数据执行XOR加密
            for (i in 0 until bytesRead) {
                buffer[i] = (buffer[i].toInt() xor key[(position + i) % key.size].toInt()).toByte()
            }

            // 写入加密后的数据
            outputStream.write(buffer, 0, bytesRead)
            position += bytesRead
        }
    }

    /**
     * 加密数据
     * @param data 待加密的原始数据
     * @return 加密后的数据
     */
    @Throws(IOException::class)
    fun encrypt(data: ByteArray): ByteArray {
        try {
            // 创建头部
            val header = CryptoHeader(data.size.toLong())
            val headerBytes = header.serialize()

            // 对数据执行XOR加密
            val encryptedContent = ByteArray(data.size)
            for (i in data.indices) {
                encryptedContent[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
            }

            // 合并头部和加密后的内容
            val result = ByteArray(headerBytes.size + encryptedContent.size)
            System.arraycopy(headerBytes, 0, result, 0, headerBytes.size)
            System.arraycopy(encryptedContent, 0, result, headerBytes.size, encryptedContent.size)

            return result
        } catch (e: Exception) {
            Log.e(TAG, "加密数据失败: ${e.message}", e)
            throw IOException("加密数据失败", e)
        }
    }

    /**
     * 解密文件
     * @param sourceFile 源文件
     * @param destinationFile 目标文件
     * @return 是否解密成功
     */
    @Throws(IOException::class)
    fun decryptFile(sourceFile: File, destinationFile: File): Boolean {
        // 检查文件大小
        val fileSize = sourceFile.length()

        if (fileSize < BUFFER_SIZE * 10) { // 对于小文件，使用内存方式处理
            try {
                // 读取源文件
                val sourceData = sourceFile.readBytes()

                // 解密数据
                val decryptedData = decrypt(sourceData)

                // 写入目标文件
                FileOutputStream(destinationFile).use { it.write(decryptedData) }

                Log.d(TAG, "成功解密小文件: ${sourceFile.path} 到 ${destinationFile.path}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "解密小文件失败: ${e.message}", e)
                throw IOException("解密文件失败", e)
            }
        } else { // 对于大文件，使用流式处理
            try {
                BufferedInputStream(FileInputStream(sourceFile)).use { inputStream ->
                    // 读取并验证头部
                    val headerBytes = ByteArray(CryptoHeader.HEADER_SIZE)
                    val headerBytesRead = inputStream.read(headerBytes)

                    if (headerBytesRead != CryptoHeader.HEADER_SIZE) {
                        throw IOException("无法读取完整的文件头部")
                    }

                    val header = CryptoHeader(headerBytes)
                    if (!header.isValid()) {
                        throw IOException("无效的加密文件头部")
                    }

                    // 创建输出流并流式解密
                    BufferedOutputStream(FileOutputStream(destinationFile)).use { outputStream ->
                        decryptStream(inputStream, outputStream, CryptoHeader.HEADER_SIZE)
                    }
                }

                Log.d(TAG, "成功流式解密大文件: ${sourceFile.path} 到 ${destinationFile.path}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "流式解密大文件失败: ${e.message}", e)
                throw IOException("解密文件失败", e)
            }
        }
    }

    /**
     * 流式解密数据
     * @param inputStream 输入流
     * @param outputStream 输出流
     * @param offset 密钥偏移量
     */
    @Throws(IOException::class)
    private fun decryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        offset: Int = 0
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var position = offset

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            // 对缓冲区中的数据执行XOR解密
            for (i in 0 until bytesRead) {
                val keyIndex = (position + i) % key.size
                buffer[i] = (buffer[i].toInt() xor key[keyIndex].toInt()).toByte()
            }

            // 写入解密后的数据
            outputStream.write(buffer, 0, bytesRead)
            position += bytesRead
        }
    }

    /**
     * 解密数据
     * @param data 待解密的数据
     * @param offset XOR操作的偏移量，用于网络流式解密
     * @return 解密后的数据
     */
    fun decrypt(data: ByteArray, offset: Int = 0): ByteArray {
        // 检查是否是完整的加密数据（包含有效的头部）
        if (offset == 0 && hasValidHeader(data)) {
            try {
                // 尝试解析头部
                val header = CryptoHeader(data)

                // 提取内容部分
                val contentData = data.copyOfRange(CryptoHeader.HEADER_SIZE, data.size)

                // 对内容执行XOR解密
                val decrypted = ByteArray(contentData.size)
                for (i in contentData.indices) {
                    decrypted[i] = (contentData[i].toInt() xor key[i % key.size].toInt()).toByte()
                }

                return decrypted
            } catch (e: Exception) {
                // 如果解析头部失败，可能不是加密文件，直接返回原数据
                Log.w(TAG, "数据不是有效的加密格式或解密失败: ${e.message}")
                return data
            }
        } else {
            // 计算实际的XOR密钥偏移量，需要基于密钥长度取模
            val keyOffset = offset % key.size

            // 对数据执行XOR解密，使用正确计算的偏移量
            val decrypted = ByteArray(data.size)
            for (i in data.indices) {
                // 计算当前字节使用的密钥索引，考虑偏移量
                val keyIndex = (i + keyOffset) % key.size
                decrypted[i] = (data[i].toInt() xor key[keyIndex].toInt()).toByte()
            }
            return decrypted
        }
    }

    /**
     * 检查数据是否包含有效的加密头部
     * @param data 待检查的数据
     * @return 是否包含有效头部
     */
    private fun hasValidHeader(data: ByteArray): Boolean {
        if (data.size < CryptoHeader.HEADER_SIZE) {
            return false
        }

        try {
            // 尝试从数据中读取魔数和版本
            val buffer =
                ByteBuffer.wrap(data, 0, CryptoHeader.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.int

            // 检查魔数是否匹配
            return magic == CryptoHeader.MAGIC_NUMBER
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 获取头部大小
     * @return 头部大小（字节数）
     */
    fun getHeaderSize(): Int {
        return CryptoHeader.HEADER_SIZE
    }

    /**
     * 在原数组上执行解密操作，避免创建新的字节数组
     * @param data 待解密的数据数组
     * @param startIndex 开始解密的索引
     * @param length 要解密的长度
     * @param offset XOR操作的偏移量
     */
    fun inplaceDecrypt(data: ByteArray, startIndex: Int, length: Int, offset: Int) {
        // 计算实际的XOR密钥偏移量，需要基于密钥长度取模
        val keyOffset = offset % key.size

        // 在原数组上执行XOR解密
        for (i in 0 until length) {
            if (startIndex + i < data.size) {
                // 计算当前字节使用的密钥索引，考虑偏移量
                val keyIndex = (i + keyOffset) % key.size
                data[startIndex + i] =
                    (data[startIndex + i].toInt() xor key[keyIndex].toInt()).toByte()
            }
        }
    }
} 