package io.agora.mpk.test.encrypt

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 加密文件头部结构
 * 用于标记和存储加密文件的元数据
 */
class CryptoHeader {
    companion object {
        // 魔数："MGPK"的十六进制表示
        const val MAGIC_NUMBER: Int = 0x4B50474D // "MGPK"
        const val VERSION: Int = 1
        const val HEADER_SIZE = 16 // 总长度16字节
    }

    // 4字节：魔数标识
    private val magic: Int

    // 4字节：版本号
    val version: Int

    // 8字节：原始文件大小，这里使用两个Int来表示
    private val originalSizeHigh: Int  // 高32位
    private val originalSizeLow: Int   // 低32位

    /**
     * 构造函数 - 用于创建新的头部
     * @param fileSize 原始文件大小
     */
    constructor(fileSize: Long) {
        this.magic = MAGIC_NUMBER
        this.version = VERSION
        // 将Long拆分为两个Int
        this.originalSizeHigh = (fileSize ushr 32).toInt()
        this.originalSizeLow = fileSize.toInt()
    }

    /**
     * 构造函数 - 从字节数组解析头部
     * @param data 包含头部数据的字节数组
     */
    constructor(data: ByteArray) {
        if (data.size < HEADER_SIZE) {
            throw IllegalArgumentException("数据长度不足以解析头部")
        }

        val buffer = ByteBuffer.wrap(data, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        // 解析魔数
        magic = buffer.int
        // 解析版本号
        version = buffer.int
        // 解析原始文件大小为两个Int
        originalSizeHigh = buffer.int
        originalSizeLow = buffer.int

        // 验证魔数
        if (magic != MAGIC_NUMBER) {
            throw IllegalArgumentException("无效的文件头魔数: $magic")
        }
    }

    /**
     * 获取原始文件大小的完整值
     * @return 文件大小的Long值
     */
    fun getOriginalSize(): Long {
        return (originalSizeHigh.toLong() shl 32) or (originalSizeLow.toLong() and 0xFFFFFFFFL)
    }

    /**
     * 将头部序列化为字节数组
     * @return 序列化后的字节数组
     */
    fun serialize(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(magic)
        buffer.putInt(version)
        buffer.putInt(originalSizeHigh)
        buffer.putInt(originalSizeLow)
        return buffer.array()
    }

    /**
     * 判断文件是否是有效的加密文件
     * @return 是否有效
     */
    fun isValid(): Boolean {
        return magic == MAGIC_NUMBER
    }

    override fun toString(): String {
        return "CryptoHeader(magic=0x${magic.toString(16)}, version=$version, originalSize=${getOriginalSize()})"
    }
} 