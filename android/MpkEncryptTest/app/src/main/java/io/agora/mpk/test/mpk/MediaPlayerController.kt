package io.agora.mpk.test.mpk

import android.content.Context
import android.util.Log
import android.view.View
import io.agora.mediaplayer.Constants.MediaPlayerState
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediaplayer.IMediaPlayerObserver
import io.agora.mediaplayer.data.CacheStatistics
import io.agora.mediaplayer.data.MediaPlayerSource
import io.agora.mediaplayer.data.PlayerPlaybackStats
import io.agora.mediaplayer.data.PlayerUpdatedInfo
import io.agora.mediaplayer.data.SrcInfo
import io.agora.mpk.test.utils.Constants
import io.agora.rtc2.RtcEngine
import java.io.File

/**
 * 媒体播放控制器
 * 用于管理和控制RTC MediaPlayer的播放功能，支持本地和网络加密文件的播放
 */
class MediaPlayerController(private val context: Context) {
    companion object {
        private const val TAG = "AgoraTest-MediaPlayerController"
    }

    // RTC引擎
    private var rtcEngine: RtcEngine? = null

    // 媒体播放器
    private var mediaPlayer: IMediaPlayer? = null

    private var customDataProvider: IMediaPlayerCustomDataProviderBase? = null

    // 下载进度回调
    private var progressCallback: ((Float) -> Unit)? = null

    private var playObserver: PlayerObserver? = null

    interface PlayerObserver {
        fun onPlayStart()
        fun onPlayStop()
    }

    private val playerObserver = object : IMediaPlayerObserver {
        override fun onPlayerStateChanged(
            state: MediaPlayerState?,
            reason: io.agora.mediaplayer.Constants.MediaPlayerReason?
        ) {
            Log.d(TAG, "MediaPlayer state changed: $state, reason: $reason")
            when (state) {
                MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED -> {
                    Log.d(TAG, "MediaPlayer opened successfully")
                    mediaPlayer?.play()
                    mediaPlayer?.selectAudioTrack(1)
                    playObserver?.onPlayStart()
                }

                MediaPlayerState.PLAYER_STATE_PLAYING -> {
                    Log.d(TAG, "MediaPlayer playing")
                }

                MediaPlayerState.PLAYER_STATE_PAUSED -> {
                    Log.d(TAG, "MediaPlayer paused")
                }

                MediaPlayerState.PLAYER_STATE_STOPPED -> {
                    Log.d(TAG, "MediaPlayer stopped")
                    playObserver?.onPlayStop()
                }

                MediaPlayerState.PLAYER_STATE_PLAYBACK_COMPLETED -> {
                    Log.d(TAG, "MediaPlayer playback completed")
                    playObserver?.onPlayStop()
                }

                MediaPlayerState.PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED -> {
                    Log.d(TAG, "MediaPlayer playback all loops completed")
                    playObserver?.onPlayStop()
                }

                MediaPlayerState.PLAYER_STATE_UNKNOWN -> {}
                MediaPlayerState.PLAYER_STATE_IDLE -> {}
                MediaPlayerState.PLAYER_STATE_OPENING -> {}
                MediaPlayerState.PLAYER_STATE_PAUSING_INTERNAL -> {}
                MediaPlayerState.PLAYER_STATE_STOPPING_INTERNAL -> {}
                MediaPlayerState.PLAYER_STATE_SEEKING_INTERNAL -> {}
                MediaPlayerState.PLAYER_STATE_GETTING_INTERNAL -> {}
                MediaPlayerState.PLAYER_STATE_NONE_INTERNAL -> {}
                MediaPlayerState.PLAYER_STATE_DO_NOTHING_INTERNAL -> {}
                MediaPlayerState.PLAYER_STATE_SET_TRACK_INTERNAL -> {}
                MediaPlayerState.PLAYER_STATE_FAILED -> {}
                null -> {}
            }
        }

        override fun onPositionChanged(positionMs: Long, timestampMs: Long) {

        }

        override fun onPlayerEvent(
            eventCode: io.agora.mediaplayer.Constants.MediaPlayerEvent?,
            elapsedTime: Long,
            message: String?
        ) {

        }

        override fun onMetaData(
            type: io.agora.mediaplayer.Constants.MediaPlayerMetadataType?,
            data: ByteArray?
        ) {

        }

        override fun onPlayBufferUpdated(playCachedBuffer: Long) {

        }

        override fun onPreloadEvent(
            src: String?,
            event: io.agora.mediaplayer.Constants.MediaPlayerPreloadEvent?
        ) {

        }

        override fun onAgoraCDNTokenWillExpire() {

        }

        override fun onPlayerSrcInfoChanged(from: SrcInfo?, to: SrcInfo?) {

        }

        override fun onPlayerInfoUpdated(info: PlayerUpdatedInfo?) {

        }

        override fun onPlayerCacheStats(stats: CacheStatistics?) {

        }

        override fun onPlayerPlaybackStats(stats: PlayerPlaybackStats?) {

        }

        override fun onAudioVolumeIndication(volume: Int) {

        }
    }

    /**
     * 初始化
     * @param appId Agora应用ID
     * @return 是否初始化成功
     */
    fun initialize(appId: String, observer: PlayerObserver, view: View): Boolean {
        try {
            playObserver = observer
            if (null == rtcEngine) {
                // 创建RTC引擎
                rtcEngine = RtcEngine.create(context, appId, null)
            }

            // 创建媒体播放器实例
            mediaPlayer = rtcEngine?.createMediaPlayer()
            if (mediaPlayer == null) {
                Log.e(TAG, "Failed to create media player")
                return false
            }

            mediaPlayer?.registerPlayerObserver(playerObserver)
            mediaPlayer?.setView(view)

            Log.d(TAG, "MediaPlayer initialized with appId: $appId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed: ${e.message}", e)
            return false
        }
    }

    /**
     * 播放本地加密文件
     * @param filePath 文件路径
     * @param isEncrypted 是否为加密文件
     * @return 是否成功开始播放
     */
    fun playLocalFile(filePath: String, isEncrypted: Boolean = true): Boolean {
        Log.d(TAG, "Play local file: $filePath, encrypted: $isEncrypted")
        try {
            mediaPlayer?.stop()

            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "playLocalFile File does not exist: $filePath")
                return false
            }

            customDataProvider =
                LocalDataReader(filePath, isEncrypted, Constants.KEY.toByteArray(Charsets.UTF_8))
            if (!(customDataProvider as LocalDataReader).init()) {
                Log.e(TAG, "playLocalFile Failed to initialize data reader")
                return false
            }

            val mediaPlayerSource = MediaPlayerSource()
            mediaPlayerSource.url = ""
            mediaPlayerSource.provider = customDataProvider

            // 开始播放
            val ret = mediaPlayer?.openWithMediaSource(mediaPlayerSource)
            if (ret != 0) {
                Log.e(TAG, "playLocalFile Failed to open media: $ret")
                return false
            }

            Log.d(TAG, "Successfully opened local file: $filePath, encrypted: $isEncrypted")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play local file: ${e.message}", e)
            return false
        }
    }

    /**
     * 播放网络加密文件
     * @param url 网络URL
     * @param isEncrypted 是否为加密文件
     * @return 是否成功开始播放
     */
    fun playNetworkFile(url: String, isEncrypted: Boolean = true): Boolean {
        try {
            mediaPlayer?.stop()

            customDataProvider = NetworkDataReader(context, url, isEncrypted)

            if (!(customDataProvider as NetworkDataReader).init()) {
                Log.e(TAG, "playNetworkFile Failed to initialize data reader")
            }

            val mediaPlayerSource = MediaPlayerSource()
            mediaPlayerSource.url = ""
            mediaPlayerSource.provider = customDataProvider

            // 开始播放
            val ret = mediaPlayer?.openWithMediaSource(mediaPlayerSource)
            if (ret != 0) {
                Log.e(TAG, "playNetworkFile Failed to open media: $ret")
                return false
            }

            Log.d(TAG, "Successfully start play network file: $url, encrypted: $isEncrypted")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to play network file: ${e.message}", e)
            return false
        }
    }

    /**
     * 播放/暂停
     * @return 当前播放状态
     */
    fun playOrPause(): Boolean {
        val mediaPlayer = this.mediaPlayer ?: return false

        return if (mediaPlayer.state == MediaPlayerState.PLAYER_STATE_PLAYING) {
            mediaPlayer.pause()
            false
        } else {
            mediaPlayer.play()
            true
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        try {
            mediaPlayer?.stop()
            customDataProvider?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop media player: ${e.message}")
        }
    }

    /**
     * 跳转到指定位置
     * @param position 目标位置（毫秒）
     * @return 是否成功跳转
     */
    fun seekTo(position: Number): Boolean {
        return mediaPlayer?.seek(position.toLong()) == 0
    }

    /**
     * 前进指定时间
     * @param time 时间（毫秒）
     * @return 是否成功跳转
     */
    fun forward(time: Int): Boolean {
        val currentPosition = getCurrentPosition()
        val duration = getDuration()
        // 计算目标位置，确保不超过视频总长度
        val targetPosition =
            if (currentPosition + time > duration) duration else currentPosition + time
        return seekTo(targetPosition)
    }

    /**
     * 后退指定时间
     * @param time 时间（毫秒）
     * @return 是否成功跳转
     */
    fun rewind(time: Int): Boolean {
        val currentPosition = getCurrentPosition()
        // 计算目标位置，确保不小于0
        val targetPosition = if (currentPosition >= time) currentPosition - time else 0
        return seekTo(targetPosition)
    }

    /**
     * 获取当前播放位置
     * @return 当前位置（毫秒）
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.playPosition?.toInt() ?: 0
    }

    /**
     * 获取总数据大小
     * @return 总数据大小（字节）
     */
    fun getTotalSize(): Long {
        return customDataProvider?.getTotalSize() ?: 0
    }

    /**
     * 获取已下载数据大小
     * @return 已下载数据大小（字节）
     */
    fun getLoadedSize(): Long {
        return customDataProvider?.getLoadedSize() ?: 0
    }

    /**
     * 获取媒体总时长
     * @return 总时长（毫秒）
     */
    fun getDuration(): Long {
        if (mediaPlayer == null) {
            return 0
        }
        return mediaPlayer?.duration ?: 0
    }

    /**
     * 是否正在播放
     * @return 是否播放中
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.state == MediaPlayerState.PLAYER_STATE_PLAYING
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            // 首先停止播放
            if (isPlaying()) {
                try {
                    mediaPlayer?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop media player during release: ${e.message}")
                }
            }

            // 释放自定义数据提供者
            try {
                customDataProvider?.release()
                customDataProvider = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release custom data provider: ${e.message}")
            }

            // 移除播放器观察者
            try {
                mediaPlayer?.registerPlayerObserver(null)
                mediaPlayer = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister player observer: ${e.message}")
            }

            // 销毁RTC引擎
            try {
                RtcEngine.destroy()
                rtcEngine = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to destroy RTC engine: ${e.message}")
            }

            playObserver = null
            progressCallback = null

            Log.d(TAG, "MediaPlayer resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release resources: ${e.message}")
        }
    }
} 