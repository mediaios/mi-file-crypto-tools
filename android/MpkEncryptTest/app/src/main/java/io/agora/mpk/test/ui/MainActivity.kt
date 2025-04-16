package io.agora.mpk.test.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lxj.xpopup.XPopup
import io.agora.mpk.test.BuildConfig
import io.agora.mpk.test.R
import io.agora.mpk.test.databinding.ActivityMainBinding
import io.agora.mpk.test.encrypt.MediaEncryptHelper
import io.agora.mpk.test.mpk.MediaPlayerController
import io.agora.mpk.test.utils.KeyCenter
import io.agora.mpk.test.utils.Utils
import io.agora.rtc2.RtcEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks,
    MediaPlayerController.PlayerObserver {
    companion object {
        const val TAG: String = "AgoraTest-MainActivity"
        const val MY_PERMISSIONS_REQUEST_CODE = 123
        const val DEFAULT_URL_FILE =
            "https://download.agora.io/demo/test/oceans_encrypted.mp4"
    }

    private var selectedFileUri: Uri? = null
    private var selectedPlayFileUri: Uri? = null

    // 使用ViewBinding
    private lateinit var binding: ActivityMainBinding

    // 文件选择启动器
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var playFilePickerLauncher: ActivityResultLauncher<Intent>

    private var mediaPlayerController: MediaPlayerController? = null

    // 定时器相关变量
    private var progressTimer: java.util.Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化文件选择启动器
        initActivityResultLaunchers()

        checkPermissions()

        // 拷贝assets中的oceans.mp4到应用专属存储目录
        Utils.copyAssetsFileToSdcardDownload(assets, applicationContext, "oceans.mp4")

        initView()
        setupListeners()

        mediaPlayerController = MediaPlayerController(applicationContext)
        mediaPlayerController?.initialize(
            KeyCenter.APP_ID,
            this,
            binding.videoView
        )
    }

    private fun initActivityResultLaunchers() {
        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedFileUri = uri
                    Log.d(TAG, "Selected file URI: $uri")
                    // 尝试获取完整路径
                    val filePath = Utils.getFilePath(contentResolver, uri)
                    val selectFileName = "已选择: $filePath"
                    binding.tvSelectedFile.text = selectFileName
                    Log.d(TAG, "Selected file path: $filePath")
                    binding.btnEncryptFile.isEnabled = true
                }
            }
        }

        playFilePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    playLocalVideo(uri)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (EasyPermissions.hasPermissions(this, *permissions)) {
            // 已经获取到权限，执行相应的操作
            Log.d(TAG, "granted permission")
        } else {
            Log.i(TAG, "requestPermissions")
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.permission_needed),
                MY_PERMISSIONS_REQUEST_CODE,
                *permissions
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        // 权限被授予，执行相应的操作
        Log.d(TAG, "onPermissionsGranted requestCode:$requestCode perms:$perms")
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.d(TAG, "onPermissionsDenied requestCode:$requestCode perms:$perms")
        // 权限被拒绝，显示一个提示信息
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            // 如果权限被永久拒绝，可以显示一个对话框引导用户去应用设置页面手动授权
            AppSettingsDialog.Builder(this).build().show()
        }
    }


    private fun handleOnBackPressed() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val xPopup = XPopup.Builder(this@MainActivity)
                    .asConfirm(
                        getString(R.string.exit),
                        getString(R.string.confirm_exit),
                        {
                            exit()
                        },
                        {}
                    )
                xPopup.show()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun initView() {
        handleOnBackPressed()

        val version =
            "Demo Version: ${BuildConfig.VERSION_NAME}  Rtc Version:${RtcEngine.getSdkVersion()}"

        binding.tvVersion.text = version
        binding.btnEncryptFile.isEnabled = false

        // 自定义进度条颜色
        binding.seekBar.progressDrawable = createCustomProgressDrawable()
    }

    private fun setupListeners() {
        // 文件选择按钮
        binding.btnSelectFile.setOnClickListener {
            openFileSelector()
        }

        // 加密文件按钮
        binding.btnEncryptFile.setOnClickListener {
            encryptSelectedFile()
        }

        // 播放本地文件按钮
        binding.btnPlayLocal.setOnClickListener {
            openFileSelector(true)
        }

        // 播放网络视频按钮
        binding.btnPlayUrl.setOnClickListener {
            showUrlInputDialog()
        }

        // 播放/暂停按钮
        binding.btnPlayPause.setOnClickListener {
            if (null == selectedPlayFileUri) {
                Toast.makeText(this, "请先选择本地视频文件", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            mediaPlayerController?.playOrPause()
            binding.btnPlayPause.text = if (mediaPlayerController?.isPlaying() == true) {
                getString(R.string.pause)
            } else {
                getString(R.string.play)
            }
        }

        // 停止按钮
        binding.btnStop.setOnClickListener {
            if (null == selectedPlayFileUri) {
                Toast.makeText(this, "请先选择本地视频文件", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (mediaPlayerController?.isPlaying() == false) {
                return@setOnClickListener
            }
            mediaPlayerController?.stop()
            binding.seekBar.progress = 0
            binding.tvCurrentPosition.text = getString(R.string.initial_time)
            binding.btnPlayPause.text = getString(R.string.play)
        }

        // 快退按钮
        binding.btnRewind10.setOnClickListener {
            if (null == selectedPlayFileUri) {
                Toast.makeText(this, "请先选择本地视频文件", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            mediaPlayerController?.rewind(10 * 1000)
        }

        // 快进按钮
        binding.btnForward10.setOnClickListener {
            if (null == selectedPlayFileUri) {
                Toast.makeText(this, "请先选择本地视频文件", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (mediaPlayerController?.isPlaying() == false) {
                return@setOnClickListener
            }

            mediaPlayerController?.forward(10 * 1000)
        }

        // 进度条
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayerController?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // 不需要实现
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 不需要实现
            }
        })
    }

    private fun openFileSelector(forPlayback: Boolean = false) {
        // 创建存储访问框架的意图
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "video/mp4"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            // 获取公共下载目录
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir.exists()) {
                // 尝试使用ACTION_OPEN_DOCUMENT并设置初始目录
                val docIntent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                docIntent.type = "video/mp4"
                docIntent.addCategory(Intent.CATEGORY_OPENABLE)

                // 设置初始URI（Android 8.0+的DocumentsContract方法）
                // 注意：这不能保证在所有设备上都有效，取决于文件选择器的实现
                val initialUri = Uri.fromFile(downloadDir)
                docIntent.putExtra("android.provider.extra.INITIAL_URI", initialUri)

                // 使用DocumentsContract的方式
                docIntent.putExtra("android.content.extra.SHOW_ADVANCED", true)

                if (forPlayback) {
                    try {
                        playFilePickerLauncher.launch(docIntent)
                        return
                    } catch (e: Exception) {
                        // 如果失败，回退到原始的GET_CONTENT方式
                        Log.e(TAG, "无法使用ACTION_OPEN_DOCUMENT: ${e.message}")
                    }
                } else {
                    try {
                        filePickerLauncher.launch(docIntent)
                        return
                    } catch (e: Exception) {
                        // 如果失败，回退到原始的GET_CONTENT方式
                        Log.e(TAG, "无法使用ACTION_OPEN_DOCUMENT: ${e.message}")
                    }
                }
            }

            // 如果设置初始目录失败或目录不存在，回退到普通的文件选择
            if (forPlayback) {
                playFilePickerLauncher.launch(intent)
            } else {
                filePickerLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.cannot_open_file_selector, e.message),
                Toast.LENGTH_SHORT
            ).show()
            Log.e(TAG, "打开文件选择器失败", e)
        }
    }

    private fun encryptSelectedFile() {
        selectedFileUri?.let { uri ->
            try {
                // 使用我们的媒体加密辅助类进行加密
                val encryptedFilePath = MediaEncryptHelper.encryptFile(applicationContext, uri)

                if (encryptedFilePath != null) {
                    // 在UI上显示加密后的文件路径
                    binding.tvEncryptedFile.visibility = View.VISIBLE
                    binding.tvEncryptedFile.text = "加密后文件: $encryptedFilePath"

                    // 简化文件信息显示
                    val message = """
                        |保存位置: $encryptedFilePath
                    """.trimMargin()

                    // 显示对话框让用户知道文件已保存并提供选项查看或播放
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.encryption_complete))
                        .setMessage(message)
                        .setPositiveButton(getString(R.string.play_encrypted_file)) { _, _ ->
                            playLocalVideo(Uri.fromFile(File(encryptedFilePath)))
                        }
                        .setNegativeButton(getString(R.string.ok), null)
                        .show()
                } else {
                    Log.e(TAG, "Error encrypting file")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    getString(R.string.file_encrypt_failed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, "Error encrypting file", e)
            }
        } ?: run {
            Toast.makeText(this, getString(R.string.please_select_file), Toast.LENGTH_SHORT).show()
        }
    }

    private fun playLocalVideo(uri: Uri) {
        mediaPlayerController?.stop()

        selectedPlayFileUri = uri
        binding.btnPlayPause.text = getString(R.string.pause)

        mediaPlayerController?.playLocalFile(Utils.getFilePath(contentResolver, uri))

        // 显示当前播放源路径
        binding.tvCurrentPlayingSource.visibility = View.VISIBLE
        val sourceText = when {
            uri.scheme == "file" -> "本地: ${uri.path}"
            uri.scheme == "content" -> "本地: ${Utils.getFilePath(contentResolver, uri)}"
            uri.scheme == "http" || uri.scheme == "https" -> "网络: ${uri.toString()}"
            else -> uri.toString()
        }
        binding.tvCurrentPlayingSource.text = sourceText
    }

    private fun playNetworkVideo(url: String) {
        mediaPlayerController?.stop()

        binding.btnPlayPause.text = getString(R.string.pause)

        mediaPlayerController?.playNetworkFile(url)

        // 显示当前播放源路径
        binding.tvCurrentPlayingSource.visibility = View.VISIBLE
        val sourceText = "网络: $url"
        binding.tvCurrentPlayingSource.text = sourceText
    }

    private fun showUrlInputDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.input_video_url))

        val input = EditText(this)
        input.hint = getString(R.string.url_hint)
        input.setText(DEFAULT_URL_FILE)
        builder.setView(input)

        builder.setPositiveButton(getString(R.string.play)) { dialog, _ ->
            val url = input.text.toString().trim()
            if (url.isNotEmpty()) {
                try {
                    selectedPlayFileUri = Uri.parse(url)
                    playNetworkVideo(url)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.please_input_url), Toast.LENGTH_SHORT)
                    .show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    @SuppressLint("DefaultLocale")


    private fun releaseMediaPlayer() {
        mediaPlayerController?.release()
    }

    private fun exit() {
        finishAffinity()
        finish()
        exitProcess(0)
    }

    private fun updateToolbarTitle(title: String) {
        binding.toolbarTitle.text = title
    }

    override fun onPlayStart() {
        Log.d(TAG, "onPlayStart")
        CoroutineScope(Dispatchers.Main).launch {
            startPlayProgressTimer()
        }

    }

    override fun onPlayStop() {
        Log.d(TAG, "onPlayStop")
        CoroutineScope(Dispatchers.Main).launch {
            stopPlayProgressTimer()
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun startPlayProgressTimer() {
        Log.d(TAG, "startPlayProgressTimer")
        // 先停止已有定时器
        stopPlayProgressTimer()

        // 获取视频总时长
        val duration = mediaPlayerController?.getDuration() ?: 0
        if (duration <= 0) {
            return
        }

        // 设置进度条最大值
        binding.seekBar.max = duration.toInt()

        // 更新视频总时长显示
        binding.tvDuration.text = Utils.formatTime(duration.toInt())

        // 创建新定时器，每100ms更新一次进度
        progressTimer = java.util.Timer().apply {
            scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    runOnUiThread {
                        try {
                            // 获取当前播放位置
                            val position = mediaPlayerController?.getCurrentPosition() ?: 0

                            // 获取加载信息
                            val loadedSize = mediaPlayerController?.getLoadedSize() ?: 0
                            val totalSize = mediaPlayerController?.getTotalSize() ?: 0


                            // 计算缓冲进度位置
                            var cachedPosition = 0
                            if (loadedSize > 0 && totalSize > 0) {
                                // 根据加载比例计算缓冲位置
                                val loadRatio = loadedSize.toFloat() / totalSize.toFloat()
                                cachedPosition = (duration * loadRatio).toInt()
                            }

                            // 更新进度条
                            updateProgressBar(position, cachedPosition)

                            // 更新当前时间显示
                            binding.tvCurrentPosition.text = Utils.formatTime(position)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating progress: ${e.message}")
                        }
                    }
                }
            }, 0, 100)
        }
    }

    /**
     * 创建自定义进度条
     */
    private fun createCustomProgressDrawable(): android.graphics.drawable.LayerDrawable {
        // 创建进度条图层
        val progressBarDrawable =
            binding.seekBar.progressDrawable as android.graphics.drawable.LayerDrawable

        // 获取背景层、缓冲层和播放进度层
        val backgroundLayer = progressBarDrawable.findDrawableByLayerId(android.R.id.background)
        val secondaryLayer =
            progressBarDrawable.findDrawableByLayerId(android.R.id.secondaryProgress)
        val progressLayer = progressBarDrawable.findDrawableByLayerId(android.R.id.progress)

        // 设置进度条各层颜色
        backgroundLayer?.setColorFilter(
            android.graphics.Color.GRAY,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        secondaryLayer?.setColorFilter(
            android.graphics.Color.BLUE,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        progressLayer?.setColorFilter(
            android.graphics.Color.RED,
            android.graphics.PorterDuff.Mode.SRC_IN
        )

        return progressBarDrawable
    }

    /**
     * 更新进度条的多种状态
     * @param playPosition 当前播放位置
     * @param cachedPosition 已缓冲位置
     */
    private fun updateProgressBar(playPosition: Int, cachedPosition: Int) {
        // 更新缓冲进度
        binding.seekBar.secondaryProgress = cachedPosition.toInt()

        // 更新播放进度
        binding.seekBar.progress = playPosition
    }

    private fun stopPlayProgressTimer() {
        // 取消并清除定时器
        progressTimer?.cancel()
        progressTimer = null

        // 重置UI
        runOnUiThread {
            binding.seekBar.progress = 0
            binding.seekBar.secondaryProgress = 0
            binding.tvCurrentPosition.text = getString(R.string.initial_time)
        }
    }
}