package io.agora.mpk.test.mpk

import io.agora.mediaplayer.IMediaPlayerCustomDataProvider

interface IMediaPlayerCustomDataProviderBase : IMediaPlayerCustomDataProvider {
    fun init(): Boolean
    fun stop()
    fun release()
    fun getTotalSize(): Long
    fun getLoadedSize(): Long
}