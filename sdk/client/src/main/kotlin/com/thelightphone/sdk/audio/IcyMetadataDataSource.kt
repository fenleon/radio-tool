package com.thelightphone.sdk.audio

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Latest ICY "now playing" title parsed from the active radio stream.
 *
 * Shared process-locally: [LightAudioService] (which owns the stream
 * connection) publishes here, and every [LightAudioPlayer] handle merges it
 * into its `mediaMetadata` so tools can show the current track.
 */
internal object IcyMetadataRelay {
    private val _current = MutableStateFlow<LightMediaMetadata?>(null)
    val current: StateFlow<LightMediaMetadata?> = _current.asStateFlow()

    fun clear() {
        _current.value = null
    }

    fun publish(title: String) {
        if (_current.value?.title != title) {
            _current.value = LightMediaMetadata(title = title)
        }
    }
}

/**
 * HttpDataSource wrapper that strips SHOUTcast/Icecast ICY metadata blocks
 * from the byte stream (ExoPlayer's extractor only ever sees audio) and
 * publishes the `StreamTitle` as the now-playing track.
 *
 * media3 1.10.1 ships `IcyHeaders`/`IcyInfo`/`IcyDecoder` but nothing in its
 * extractor pipeline consumes them, so the in-band parsing lives here, at the
 * data-source layer, where the raw stream and its response headers are both
 * visible.
 */
@UnstableApi
internal class IcyMetadataDataSource(
    private val delegate: HttpDataSource,
) : HttpDataSource by delegate {

    private var icyMetaInt = -1
    private var bytesUntilMeta = 0

    override fun open(dataSpec: DataSpec): Long {
        val length = delegate.open(dataSpec)
        // Icecast/SHOUTcast advertise the metadata interval as an HTTP header;
        // no header means no in-band titles. Start each (re)open clean so a
        // stale title never survives a stream change or retry.
        icyMetaInt = delegate.responseHeaders["icy-metaint"]?.firstOrNull()?.toIntOrNull() ?: -1
        bytesUntilMeta = if (icyMetaInt > 0) icyMetaInt else 0
        IcyMetadataRelay.clear()
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (icyMetaInt <= 0) return delegate.read(buffer, offset, length)

        // Only hand the extractor audio bytes; a metadata block is expected at
        // exactly the interval boundary, so never read past it in one call.
        val audioLength = minOf(length, bytesUntilMeta)
        val n = delegate.read(buffer, offset, audioLength)
        if (n > 0) {
            bytesUntilMeta -= n
            if (bytesUntilMeta <= 0) readMetadataBlock()
        }
        return n
    }

    /** Consumes one metadata block (1 length byte + payload) and publishes its StreamTitle. */
    private fun readMetadataBlock() {
        val lengthByte = readByte()
        if (lengthByte == null) {
            // Stream ended mid-block; pass everything through from here on.
            icyMetaInt = -1
            return
        }
        val metaLength = lengthByte * 16
        if (metaLength > 0) {
            val block = ByteArray(metaLength)
            if (readFully(block)) {
                parseStreamTitle(block)?.let(IcyMetadataRelay::publish)
            }
        }
        bytesUntilMeta = icyMetaInt
    }

    private fun readByte(): Int? {
        val one = ByteArray(1)
        return if (readFully(one)) one[0].toInt() and 0xFF else null
    }

    private fun readFully(target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val n = delegate.read(target, offset, target.size - offset)
            if (n <= 0) return false
            offset += n
        }
        return true
    }

    private fun parseStreamTitle(block: ByteArray): String? {
        // Blocks look like "StreamTitle='Artist - Song';StreamUrl='...';" (null-padded).
        val text = block.toString(Charsets.UTF_8)
        return STREAM_TITLE.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val STREAM_TITLE = Regex("StreamTitle='([^']*)'")
    }
}

/** Wraps any HTTP data-source factory so progressive streams get ICY parsing. */
@UnstableApi
internal class IcyMetadataDataSourceFactory(
    private val base: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        IcyMetadataDataSource(base.createDataSource() as HttpDataSource)
}
