package gh.ug.kasacore.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * Records 16kHz mono PCM and writes a real .wav file — the exact format
 * Kelvin's run_asr expects (03_FRONTEND_ANDROID_GUIDE.md Step 3.2). Android's
 * MediaRecorder cannot emit raw WAV (only AAC/AMR containers), so this uses
 * AudioRecord directly and hand-writes the 44-byte WAV header.
 *
 * Requires RECORD_AUDIO granted at runtime before start() is called.
 */
class WavRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var recording = false
    private var outputFile: File? = null

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    fun start(): File {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufferSize = maxOf(minBuf, SAMPLE_RATE) // >= ~0.5s, avoids tiny-buffer underruns
        val file = File(context.cacheDir, "kasa_command_${System.currentTimeMillis()}.wav")
        outputFile = file

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize
        )
        audioRecord = record
        recording = true
        record.startRecording()

        recordingThread = thread(start = true) {
            val pcmFile = File(context.cacheDir, "kasa_pcm_tmp.raw")
            FileOutputStream(pcmFile).use { out ->
                val buffer = ByteArray(bufferSize)
                while (recording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) out.write(buffer, 0, read)
                }
            }
            writeWavFile(pcmFile, file)
            pcmFile.delete()
        }
        return file
    }

    /** Stops recording. The .wav file is finished shortly after (writer thread joins). */
    fun stop() {
        recording = false
        recordingThread?.join(2000)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun writeWavFile(pcmFile: File, wavFile: File) {
        val pcmDataSize = pcmFile.length()
        RandomAccessFile(wavFile, "rw").use { out ->
            writeWavHeader(out, pcmDataSize)
            pcmFile.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) out.write(buffer, 0, read)
            }
        }
    }

    private fun writeWavHeader(out: RandomAccessFile, pcmDataSize: Long) {
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        val totalDataLen = pcmDataSize + 36
        out.setLength(0)
        out.writeBytes("RIFF")
        out.write(intToLe(totalDataLen.toInt()))
        out.writeBytes("WAVE")
        out.writeBytes("fmt ")
        out.write(intToLe(16))               // fmt chunk size
        out.write(shortToLe(1))              // PCM
        out.write(shortToLe(1))              // mono
        out.write(intToLe(SAMPLE_RATE))
        out.write(intToLe(byteRate))
        out.write(shortToLe((1 * 16 / 8)))   // block align
        out.write(shortToLe(16))             // bits per sample
        out.writeBytes("data")
        out.write(intToLe(pcmDataSize.toInt()))
    }

    private fun intToLe(v: Int) = byteArrayOf(
        (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()
    )

    private fun shortToLe(v: Int) = byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())
}
