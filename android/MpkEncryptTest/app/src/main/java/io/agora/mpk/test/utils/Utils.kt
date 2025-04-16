package io.agora.mpk.test.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import java.util.UUID


object Utils {

    private const val TAG: String = "AgoraTest-Utils"

    fun generateUniqueRandom(context: Context): String {
        val seed = StringBuilder()

        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString()

        seed.append(timestamp)
            .append(uuid)


        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(seed.toString().toByteArray())

            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            return hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return UUID.randomUUID().toString()
        }
    }

    fun byteArrayToBase64(bytes: ByteArray): String {
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    fun base64ToByteArray(base64String: String): ByteArray {
        return android.util.Base64.decode(base64String, android.util.Base64.NO_WRAP)
    }


    // 获取完整路径的方法
    fun getFilePath(contentResolver: ContentResolver, uri: Uri): String {
        Log.d(TAG, "获取文件路径，URI: $uri")

        // 特殊处理ExternalStorage Documents URI
        if (uri.authority == "com.android.externalstorage.documents") {
            try {
                val docId = uri.lastPathSegment?.split(":")
                if (docId != null && docId.size == 2) {
                    val type = docId[0]
                    val relativePath = docId[1]

                    if ("primary".equals(type, ignoreCase = true)) {
                        val path = "${Environment.getExternalStorageDirectory().path}/$relativePath"
                        Log.d(TAG, "外部存储文档路径: $path")
                        val file = File(path)
                        if (file.exists()) {
                            return path
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析ExternalStorage URI失败: ${e.message}")
            }
        }

        // 尝试多种方法获取完整路径
        val path = when {
            // 如果是file类型的URI，直接返回路径
            uri.scheme == "file" -> uri.path

            // 如果是content类型的URI，尝试获取实际路径
            uri.scheme == "content" -> {
                try {
                    // 获取文件名
                    val displayName = getFileName(contentResolver, uri)
                    Log.d(TAG, "Content URI文件名: $displayName")

                    // 方法1：从MediaStore获取DATA列
                    val mediaStorePath = getMediaStorePath(contentResolver, uri)
                    if (!mediaStorePath.isNullOrEmpty()) {
                        Log.d(TAG, "MediaStore路径: $mediaStorePath")
                        return mediaStorePath
                    }

                    // 方法2：检查常见存储目录
                    val commonStoragePath = findFileInCommonStorages(displayName)
                    if (!commonStoragePath.isNullOrEmpty()) {
                        Log.d(TAG, "常见存储目录路径: $commonStoragePath")
                        return commonStoragePath
                    }

                    // 方法3：检查外部存储根目录
                    val externalStoragePath = findFileInExternalStorage(displayName)
                    if (!externalStoragePath.isNullOrEmpty()) {
                        Log.d(TAG, "外部存储根目录路径: $externalStoragePath")
                        return externalStoragePath
                    }

                    // 如果以上方法都失败，则返回原始URI字符串
                    uri.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "获取文件路径失败", e)
                    uri.toString()
                }
            }

            // 其他类型的URI
            else -> uri.toString()
        }

        return path ?: uri.toString()
    }

    /**
     * 从MediaStore获取文件路径
     */
    private fun getMediaStorePath(contentResolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "从MediaStore获取路径失败", e)
        }
        return null
    }

    /**
     * 在常见存储目录中查找文件
     */
    private fun findFileInCommonStorages(fileName: String): String? {
        // 检查常见的存储位置
        val possibleDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        )

        for (dir in possibleDirs) {
            val file = File(dir, fileName)
            if (file.exists() && file.isFile) {
                return file.absolutePath
            }
        }

        return null
    }

    /**
     * 在外部存储根目录中查找文件
     */
    private fun findFileInExternalStorage(fileName: String): String? {
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            val file = File(externalStorage, fileName)
            if (file.exists() && file.isFile) {
                return file.absolutePath
            }

            // 也检查一下Android/data目录
            val androidDataDir = File(externalStorage, "Android/data")
            if (androidDataDir.exists() && androidDataDir.isDirectory) {
                androidDataDir.listFiles()?.forEach { appDir ->
                    if (appDir.isDirectory) {
                        // 检查此应用目录下的files和cache目录
                        val filesDir = File(appDir, "files")
                        if (filesDir.exists()) {
                            val file = File(filesDir, fileName)
                            if (file.exists() && file.isFile) {
                                return file.absolutePath
                            }
                        }

                        val cacheDir = File(appDir, "cache")
                        if (cacheDir.exists()) {
                            val file = File(cacheDir, fileName)
                            if (file.exists() && file.isFile) {
                                return file.absolutePath
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "在外部存储中查找文件失败", e)
        }

        return null
    }

    /**
     * 获取文件名
     * @param contentResolver 内容解析器
     * @param uri 文件Uri
     * @return 文件名
     */
    fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        // 对于file类型的URI
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "unknown_file"
        }

        // 对于content类型的URI
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            return cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && it.moveToFirst()) {
                    it.getString(nameIndex)
                } else {
                    "unknown_file"
                }
            } ?: "unknown_file"
        }

        return "unknown_file"
    }


    fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
    }


    fun copyAssetsFileToSdcardDownload(
        assets: AssetManager,
        context: Context,
        assetFileName: String
    ) {
        try {
            val inputStream = assets.open(assetFileName)

            // 使用公共下载目录代替应用专属目录
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val outputFile = File(downloadDir, assetFileName)
            val fileExists = outputFile.exists()

            // 如果文件已存在，则不再复制
            if (!fileExists) {
                val outputStream = FileOutputStream(outputFile)

                // 复制文件
                val buffer = ByteArray(1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }

                inputStream.close()
                outputStream.flush()
                outputStream.close()

                Log.d(TAG, "成功复制assets文件到公共下载目录: ${outputFile.absolutePath}")

                // 通知媒体扫描器更新媒体数据库，使文件在文件管理器和其他应用中可见
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val contentUri = Uri.fromFile(outputFile)
                mediaScanIntent.data = contentUri
                context.sendBroadcast(mediaScanIntent)

            } else {
                Log.d(TAG, "文件已存在于公共下载目录，无需复制: ${outputFile.absolutePath}")
                inputStream.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制assets文件到公共下载目录失败", e)
        }
    }

    /**
     * 读取输入流的所有字节
     * @param inputStream 输入流
     * @return 字节数组
     */
    fun readAllBytes(inputStream: InputStream): ByteArray {
        return inputStream.use { it.readBytes() }
    }

    /**
     * 通知媒体扫描器更新媒体库
     * 使用MediaScannerConnection替代旧的广播方法
     * @param context 上下文
     * @param file 文件
     */
    fun notifyMediaScanner(context: Context, file: File) {
        // 使用MediaScannerConnection的scanFile方法，更现代的实现方式
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("video/mp4"),
            null
        )
    }


}