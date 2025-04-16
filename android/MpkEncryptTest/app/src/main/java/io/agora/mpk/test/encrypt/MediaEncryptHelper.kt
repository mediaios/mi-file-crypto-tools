package io.agora.mpk.test.encrypt

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import io.agora.mpk.test.utils.Utils
import java.io.File

/**
 * 媒体加密解密帮助类
 * 用于处理视频文件的加密和解密操作
 */
object MediaEncryptHelper {
    private const val TAG = "AgoraTest-MediaEncryptHelper"
    private val crypto = XORCrypto()

    /**
     * 加密进度回调接口
     */
    interface EncryptProgressCallback {
        /**
         * 当进度更新时调用
         * @param progress 进度值 (0-100)
         */
        fun onProgressUpdate(progress: Int)

        /**
         * 当操作完成时调用
         * @param success 是否成功
         * @param outputPath 输出文件路径，如果成功
         */
        fun onComplete(success: Boolean, outputPath: String?)
    }

    /**
     * 加密文件
     * @param sourceUri 源文件Uri
     * @param callback 进度回调，可为null
     * @return 加密后的文件路径
     */
    fun encryptFile(
        context: Context,
        sourceUri: Uri,
        callback: EncryptProgressCallback? = null
    ): String? {
        try {
            // 获取原始文件名和路径
            val fileName = Utils.getFileName(context.contentResolver, sourceUri)
            val encryptedFileName = "${fileName.substringBeforeLast(".")}_encrypted.mp4"

            // 获取源文件所在目录
            val sourceFilePath = Utils.getFilePath(context.contentResolver, sourceUri)
            val sourceParentDir = File(sourceFilePath).parentFile

            // 确保目录存在
            if (sourceParentDir != null && !sourceParentDir.exists()) {
                sourceParentDir.mkdirs()
            }

            // 创建目标文件
            val encryptedFile = File(sourceParentDir, encryptedFileName)

            // 获取输入文件
            val sourceFile = File(sourceFilePath)

            // 确保源文件存在
            if (!sourceFile.exists()) {
                throw Exception("源文件不存在")
            }

            // 使用XORCrypto的加密方法
            crypto.encryptFile(sourceFile, encryptedFile)

            // 更新媒体库
            Utils.notifyMediaScanner(context, encryptedFile)

            Log.d(
                TAG,
                "成功加密文件到: ${encryptedFile.absolutePath} file size: ${sourceFile.length()}"
            )

            // 通知完成
            callback?.onComplete(true, encryptedFile.absolutePath)

            return encryptedFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "加密文件失败: ${e.message}", e)
            Toast.makeText(context, "加密失败: ${e.message}", Toast.LENGTH_SHORT).show()

            // 通知失败
            callback?.onComplete(false, null)

            return null
        }
    }

    /**
     * 解密文件
     * @param sourceUri 加密文件Uri
     * @param callback 进度回调，可为null
     * @return 解密后的文件路径
     */
    fun decryptFile(
        context: Context,
        sourceUri: Uri,
        callback: EncryptProgressCallback? = null
    ): String? {
        try {
            // 获取原始文件名和路径
            val fileName = Utils.getFileName(context.contentResolver, sourceUri)
            val decryptedFileName = "${fileName.substringBeforeLast(".")}_decrypted.mp4"

            // 获取源文件所在目录
            val sourceFilePath = Utils.getFilePath(
                context.contentResolver, sourceUri
            )
            val sourceParentDir = File(sourceFilePath).parentFile

            // 确保目录存在
            if (sourceParentDir != null && !sourceParentDir.exists()) {
                sourceParentDir.mkdirs()
            }

            // 创建目标文件
            val decryptedFile = File(sourceParentDir, decryptedFileName)

            // 获取输入文件
            val sourceFile = File(sourceFilePath)

            // 确保源文件存在
            if (!sourceFile.exists()) {
                throw Exception("源文件不存在")
            }

            // 使用XORCrypto的解密方法
            crypto.decryptFile(sourceFile, decryptedFile)

            // 更新媒体库
            Utils.notifyMediaScanner(context, decryptedFile)

            Log.d(TAG, "成功解密文件到: ${decryptedFile.absolutePath}")

            // 通知完成
            callback?.onComplete(true, decryptedFile.absolutePath)

            return decryptedFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "解密文件失败: ${e.message}", e)
            Toast.makeText(context, "解密失败: ${e.message}", Toast.LENGTH_SHORT).show()

            // 通知失败
            callback?.onComplete(false, null)

            return null
        }
    }

    /**
     * 加密本地文件
     * @param sourceFile 源文件
     * @param callback 进度回调，可为null
     * @return 加密后的文件
     */
    fun encryptLocalFile(
        context: Context,
        sourceFile: File,
        callback: EncryptProgressCallback? = null
    ): File? {
        try {
            // 获取源文件所在目录
            val sourceParentDir = sourceFile.parentFile

            // 创建加密后的文件名
            val encryptedFileName = "${sourceFile.nameWithoutExtension}_encrypted.mp4"
            val encryptedFile = File(sourceParentDir, encryptedFileName)

            // 加密文件
            crypto.encryptFile(sourceFile, encryptedFile)

            // 更新媒体库
            Utils.notifyMediaScanner(context, encryptedFile)

            // 通知完成
            callback?.onComplete(true, encryptedFile.absolutePath)

            return encryptedFile
        } catch (e: Exception) {
            Log.e(TAG, "加密本地文件失败: ${e.message}", e)
            Toast.makeText(context, "加密本地文件失败: ${e.message}", Toast.LENGTH_SHORT).show()

            // 通知失败
            callback?.onComplete(false, null)

            return null
        }
    }

    /**
     * 解密本地文件
     * @param sourceFile 加密源文件
     * @param callback 进度回调，可为null
     * @return 解密后的文件
     */
    fun decryptLocalFile(
        context: Context,
        sourceFile: File,
        callback: EncryptProgressCallback? = null
    ): File? {
        try {
            // 获取源文件所在目录
            val sourceParentDir = sourceFile.parentFile

            // 创建解密后的文件名
            val decryptedFileName = "${sourceFile.nameWithoutExtension}_decrypted.mp4"
            val decryptedFile = File(sourceParentDir, decryptedFileName)

            // 解密文件
            crypto.decryptFile(sourceFile, decryptedFile)

            // 更新媒体库
            Utils.notifyMediaScanner(context, decryptedFile)

            // 通知完成
            callback?.onComplete(true, decryptedFile.absolutePath)

            return decryptedFile
        } catch (e: Exception) {
            Log.e(TAG, "解密本地文件失败: ${e.message}", e)
            Toast.makeText(context, "解密本地文件失败: ${e.message}", Toast.LENGTH_SHORT).show()

            // 通知失败
            callback?.onComplete(false, null)

            return null
        }
    }
} 