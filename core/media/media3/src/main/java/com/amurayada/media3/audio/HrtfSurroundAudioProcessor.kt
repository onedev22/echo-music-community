package com.amurayada.media3.audio

import android.content.Context
import android.os.PowerManager
import androidx.annotation.WorkerThread
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.amurayada.logger.Logger
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@UnstableApi
class HrtfSurroundAudioProcessor(
    private val context: Context,
    enabled: Boolean = false,
) : BaseAudioProcessor() {

    companion object {
        private const val TAG = "HrtfSurroundAudioProcessor"
        private val CHANNEL_ASSETS = arrayOf(
            Pair("FL_45.wav",  "FR_315.wav"),
            Pair("FR_315.wav", "FL_45.wav"),
            Pair("FC_0.wav",   "FC_0.wav"),
            Pair("FC_0.wav",   "FC_0.wav"),
            Pair("SL_90.wav",  "SR_270.wav"),
            Pair("SR_270.wav", "SL_90.wav"),
            Pair("BL_135.wav", "BR_225.wav"),
            Pair("BR_225.wav", "BL_135.wav"),
        )
        private const val NUM_CHANNELS = 8
        private const val MAX_IR_LENGTH = 512
        private const val OUTPUT_GAIN = 0.9f
        private const val DEFAULT_MIX_NORM = 4.0f
        
        private val sharedHrtfIr = Array(NUM_CHANNELS) { Array(2) { FloatArray(0) } }
        @Volatile private var sharedIrLoaded = false
        @Volatile private var sharedMixNormFactor = DEFAULT_MIX_NORM
        private val irLock = Any()
    }

    private var sampleRate = 0
    private var channelCount = 0

    private var irLoaded = false
    private var mixNormFactor = DEFAULT_MIX_NORM
    
    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    private val olaEngines = Array(NUM_CHANNELS) { Array(2) { OlaEngine() } }

    private var allPassSL: AllPassFilter? = null
    private var allPassSR: AllPassFilter? = null
    private var allPassBL: AllPassFilter? = null
    private var allPassBR: AllPassFilter? = null

    // Buffers reutilizables – evitan asignaciones en cada buffer de audio
    private val channelBuffers = Array(NUM_CHANNELS) { FloatArray(0) }
    private var inLeft  = FloatArray(0)
    private var inRight = FloatArray(0)
    private var outLeft  = FloatArray(0)
    private var outRight = FloatArray(0)
    private var tempL = FloatArray(0)
    private var tempR = FloatArray(0)

    @Volatile
    var enabled: Boolean = enabled
        set(value) {
            if (field != value) {
                field = value
                Logger.d(TAG, "Surround ${if (value) "enabled" else "disabled"}")
                if (!value) resetState()
            }
        }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2) {
            Logger.w(TAG, "Only stereo input supported; bypassing")
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount

        if (!irLoaded) {
            loadImpulseResponses()
            initAllPassFilters()
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = false

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || !irLoaded || sampleRate == 0) {
            val output = replaceOutputBuffer(remaining)
            passThrough(inputBuffer, output, remaining)
            output.flip()
            return
        }

        val frameCount = remaining / 4
        if (frameCount == 0) {
            val output = replaceOutputBuffer(remaining)
            passThrough(inputBuffer, output, remaining)
            output.flip()
            return
        }

        // Asegurar capacidad de todos los buffers reutilizables
        ensureBufferCapacity(frameCount)

        inputBuffer.order(ByteOrder.nativeOrder())
        // --- 1. Lectura y upmix directo en los buffers reutilizables ---
        for (i in 0 until frameCount) {
            val l = inputBuffer.short / 32768f
            val r = inputBuffer.short / 32768f
            inLeft[i]  = l
            inRight[i] = r
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f

            channelBuffers[0][i] = l * 0.8f
            channelBuffers[1][i] = r * 0.8f
            channelBuffers[2][i] = mid * 0.7f
            channelBuffers[3][i] = mid * 0.15f
            channelBuffers[4][i] = allPassSL!!.process(side) * 0.65f
            channelBuffers[5][i] = allPassSR!!.process(side) * 0.65f
            channelBuffers[6][i] = allPassBL!!.process(side) * 0.35f
            channelBuffers[7][i] = allPassBR!!.process(side) * 0.35f
        }

        // --- 2. Reiniciar acumuladores de salida ---
        outLeft.fill(0f, 0, frameCount)
        outRight.fill(0f, 0, frameCount)

        // --- 3. Convolución y acumulación (todo sobre arrays ya existentes) ---
        for (ch in 0 until NUM_CHANNELS) {
            val ir = sharedHrtfIr[ch]
            if (ir[0].isEmpty() || ir[1].isEmpty()) continue

            olaEngines[ch][0].process(channelBuffers[ch], frameCount, tempL)
            for (i in 0 until frameCount) outLeft[i] += tempL[i]

            olaEngines[ch][1].process(channelBuffers[ch], frameCount, tempR)
            for (i in 0 until frameCount) outRight[i] += tempR[i]
        }

        // --- 4. Normalización, ganancia y soft‑clip ---
        val outputBytes = frameCount * 4
        val output = replaceOutputBuffer(outputBytes)
        output.order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            var lRaw = outLeft[i] / mixNormFactor * OUTPUT_GAIN
            var rRaw = outRight[i] / mixNormFactor * OUTPUT_GAIN

            lRaw = softClip(lRaw)
            rRaw = softClip(rRaw)

            val l = clamp(lRaw, -1f, 1f)
            val r = clamp(rRaw, -1f, 1f)

            output.putShort((l * 32767f).toInt().toShort())
            output.putShort((r * 32767f).toInt().toShort())
        }
        output.flip()
    }

    private fun ensureBufferCapacity(frameCount: Int) {
        if (inLeft.size < frameCount) {
            inLeft  = FloatArray(frameCount)
            inRight = FloatArray(frameCount)
            outLeft  = FloatArray(frameCount)
            outRight = FloatArray(frameCount)
            tempL = FloatArray(frameCount)
            tempR = FloatArray(frameCount)
            // Redimensionar también los buffers de canal si es necesario
            for (ch in 0 until NUM_CHANNELS) {
                if (channelBuffers[ch].size < frameCount) {
                    channelBuffers[ch] = FloatArray(frameCount)
                }
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() {
        super.onFlush()
        resetState()
    }

    override fun onReset() {
        super.onReset()
        resetState()
    }

    private fun softClip(x: Float): Float {
        val threshold = 0.85f
        val absX = abs(x)
        if (absX <= threshold) return x
        val sign = if (x < 0) -1f else 1f
        val excess = absX - threshold
        return (threshold + (1f - threshold) * (excess / (excess + (1f - threshold)))) * sign
    }

    private fun resetState() {
        for (ch in 0 until NUM_CHANNELS) {
            olaEngines[ch][0].reset()
            olaEngines[ch][1].reset()
        }
        allPassSL?.reset()
        allPassSR?.reset()
        allPassBL?.reset()
        allPassBR?.reset()
    }

    private fun initAllPassFilters() {
        allPassSL = AllPassFilter((sampleRate * 0.0015f).toInt().coerceAtLeast(1), 0.5f)
        allPassSR = AllPassFilter((sampleRate * 0.0027f).toInt().coerceAtLeast(1), 0.5f)
        allPassBL = AllPassFilter((sampleRate * 0.0021f).toInt().coerceAtLeast(1), 0.6f)
        allPassBR = AllPassFilter((sampleRate * 0.0033f).toInt().coerceAtLeast(1), 0.6f)
    }

    private fun passThrough(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        dst.put(src)
    }

    private fun clamp(value: Float, min: Float, max: Float) = when {
        value < min -> min; value > max -> max; else -> value
    }

    @WorkerThread
    private fun loadImpulseResponses() {
        if (!sharedIrLoaded) {
            synchronized(irLock) {
                if (!sharedIrLoaded) {
                    Logger.d(TAG, "Loading HRTF impulse responses globally…")
                    val cache = mutableMapOf<String, FloatArray>()
                    for (ch in 0 until NUM_CHANNELS) {
                        val (leftAsset, rightAsset) = CHANNEL_ASSETS[ch]
                        sharedHrtfIr[ch][0] = cache.getOrPut(leftAsset)  { loadAndNormalizeWav(leftAsset) }
                        sharedHrtfIr[ch][1] = cache.getOrPut(rightAsset) { loadAndNormalizeWav(rightAsset) }
                    }
                    sharedMixNormFactor = computeMixNorm()
                    sharedIrLoaded = true
                    Logger.d(TAG, "Global IRs loaded. mixNormFactor = $sharedMixNormFactor")
                }
            }
        }
        
        // Init engines for this instance
        for (ch in 0 until NUM_CHANNELS) {
            olaEngines[ch][0].init(sharedHrtfIr[ch][0])
            olaEngines[ch][1].init(sharedHrtfIr[ch][1])
        }
        mixNormFactor = sharedMixNormFactor
        irLoaded = true
    }

    private fun computeMixNorm(): Float {
        var sumPeak = 0f
        for (ch in 0 until NUM_CHANNELS) {
            val irLeft = sharedHrtfIr[ch][0]
            if (irLeft.isNotEmpty()) sumPeak += irLeft.maxOf { abs(it) }
        }
        return max(sumPeak * 1.1f, 0.1f)
    }

    private fun loadAndNormalizeWav(name: String): FloatArray {
        return try {
            val fullIr = context.assets.open(name).use { stream -> decodeWav(stream) }
            if (fullIr.isEmpty()) return FloatArray(0)
            val ir = if (fullIr.size > MAX_IR_LENGTH) fullIr.copyOf(MAX_IR_LENGTH) else fullIr
            val peak = ir.maxOf { abs(it) }
            if (peak > 0f) {
                val gain = 0.5f / peak
                for (i in ir.indices) ir[i] *= gain
            }
            for (i in ir.indices) ir[i] *= exp(-i * 0.002f)
            ir
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load IR '$name': ${e.message}")
            FloatArray(0)
        }
    }

    private fun decodeWav(stream: InputStream): FloatArray {
        val bytes = stream.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        if (riff != "RIFF" || wave != "WAVE") {
            Logger.e(TAG, "Not a valid WAV file")
            return FloatArray(0)
        }

        buf.position(20)
        val audioFormat  = buf.short.toInt() and 0xFFFF
        val numChannels  = buf.short.toInt() and 0xFFFF
        val sampleRateHz = buf.int
        buf.int  // byte rate
        buf.short // block align
        val bitsPerSample = buf.short.toInt() and 0xFFFF

        var dataOffset = 12
        while (dataOffset + 8 <= bytes.size) {
            val chunkId   = String(bytes, dataOffset, 4)
            buf.position(dataOffset + 4)
            val chunkSize = buf.int
            if (chunkId == "data") {
                dataOffset += 8
                val sampleBytes = chunkSize.coerceAtMost(bytes.size - dataOffset)
                val dataBuf = ByteBuffer.wrap(bytes, dataOffset, sampleBytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                return when {
                    audioFormat == 1 && bitsPerSample == 16 ->
                        decodePcm16(dataBuf, numChannels, sampleBytes / 2 / numChannels)
                    audioFormat == 3 && bitsPerSample == 32 ->
                        decodeFloat32(dataBuf, numChannels, sampleBytes / 4 / numChannels)
                    else -> {
                        Logger.e(TAG, "Unsupported WAV format")
                        FloatArray(0)
                    }
                }
            }
            dataOffset += 8 + chunkSize
        }
        Logger.e(TAG, "No 'data' chunk found")
        return FloatArray(0)
    }

    private fun decodePcm16(buf: ByteBuffer, channels: Int, frames: Int): FloatArray {
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (ch in 0 until channels) sum += buf.short / 32768f
            mono[i] = sum / channels
        }
        return mono
    }

    private fun decodeFloat32(buf: ByteBuffer, channels: Int, frames: Int): FloatArray {
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (ch in 0 until channels) sum += buf.float
            mono[i] = sum / channels
        }
        return mono
    }
}

// ── Overlap-Add Engine sin asignaciones nuevas (zero‑allocation) ─────
private class OlaEngine {
    private val BLOCK_SIZE = 128
    private var fftSize = 0
    private var irLen = 0

    private var irFftRe = FloatArray(0)
    private var irFftIm = FloatArray(0)
    private var overlap = FloatArray(0)

    private val inBuffer = FloatRingBuffer(32768)
    private val outBuffer = FloatRingBuffer(32768)

    private var workBlock = FloatArray(0)
    private var workBlockIm = FloatArray(0)
    private var workOutRe = FloatArray(0)
    private var workOutIm = FloatArray(0)
    private var workResult = FloatArray(0)

    private var ready = false

    private var cosTable = FloatArray(0)
    private var sinTable = FloatArray(0)

    fun init(ir: FloatArray) {
        if (ir.isEmpty()) { ready = false; return }
        irLen = ir.size
        fftSize = nextPow2(BLOCK_SIZE + irLen - 1)

        cosTable = FloatArray(fftSize)
        sinTable = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            val ang = 2.0 * Math.PI * i / fftSize
            cosTable[i] = cos(ang).toFloat()
            sinTable[i] = sin(ang).toFloat()
        }

        val irPadded = FloatArray(fftSize)
        ir.copyInto(irPadded, 0, 0, min(irLen, fftSize))
        val imZeros = FloatArray(fftSize)
        fftInPlace(irPadded, imZeros, false)
        irFftRe = irPadded
        irFftIm = imZeros

        overlap = FloatArray(fftSize)

        workBlock = FloatArray(fftSize)
        workBlockIm = FloatArray(fftSize)
        workOutRe = FloatArray(fftSize)
        workOutIm = FloatArray(fftSize)
        workResult = FloatArray(fftSize)

        outBuffer.clear()
        repeat(BLOCK_SIZE) { outBuffer.write(0f) }
        ready = true
    }

    fun reset() {
        if (ready) {
            overlap.fill(0f)
            inBuffer.clear()
            outBuffer.clear()
            repeat(BLOCK_SIZE) { outBuffer.write(0f) }
        }
    }

    fun process(input: FloatArray, inputLength: Int, output: FloatArray) {
        if (!ready || fftSize == 0) {
            input.copyInto(output, 0, 0, inputLength)
            return
        }

        inBuffer.write(input, inputLength)

        while (inBuffer.size >= BLOCK_SIZE) {
            for (i in 0 until BLOCK_SIZE) {
                workBlock[i] = inBuffer.read()
            }
            workBlock.fill(0f, BLOCK_SIZE, fftSize)
            workBlockIm.fill(0f)

            fftInPlace(workBlock, workBlockIm, false)

            for (k in 0 until fftSize) {
                workOutRe[k] = workBlock[k] * irFftRe[k] - workBlockIm[k] * irFftIm[k]
                workOutIm[k] = workBlock[k] * irFftIm[k] + workBlockIm[k] * irFftRe[k]
            }

            fftInPlace(workOutRe, workOutIm, true)
            val inv = 1f / fftSize
            for (i in 0 until fftSize) {
                workResult[i] = workOutRe[i] * inv
            }

            for (i in 0 until fftSize) {
                overlap[i] += workResult[i]
            }
            for (i in 0 until BLOCK_SIZE) {
                outBuffer.write(overlap[i])
            }

            overlap.copyInto(overlap, 0, BLOCK_SIZE, fftSize)
            overlap.fill(0f, fftSize - BLOCK_SIZE, fftSize)
        }

        outBuffer.read(output, inputLength)
    }

    private fun nextPow2(n: Int): Int {
        var p = 1; while (p < n) p = p shl 1; return p
    }

    private fun fftInPlace(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val step = n / len
            val signIm = if (inverse) 1f else -1f
            var i = 0
            while (i < n) {
                for (jj in 0 until halfLen) {
                    val angleIdx = jj * step
                    val curRe = cosTable[angleIdx]
                    val curIm = sinTable[angleIdx] * signIm

                    val uRe = re[i + jj]; val uIm = im[i + jj]
                    val vRe = re[i + jj + halfLen] * curRe - im[i + jj + halfLen] * curIm
                    val vIm = re[i + jj + halfLen] * curIm + im[i + jj + halfLen] * curRe
                    re[i + jj] = uRe + vRe; im[i + jj] = uIm + vIm
                    re[i + jj + halfLen] = uRe - vRe; im[i + jj + halfLen] = uIm - vIm
                }
                i += len
            }
            len = len shl 1
        }
    }
}

// ── FloatRingBuffer ─────────────────────────────────────────────────
private class FloatRingBuffer(capacity: Int) {
    private val data = FloatArray(capacity)
    private var writeIdx = 0
    private var readIdx = 0
    private var count = 0

    val size: Int get() = count

    fun write(value: Float) {
        if (count >= data.size) { readIdx = (readIdx + 1) % data.size; count-- }
        data[writeIdx] = value
        writeIdx = (writeIdx + 1) % data.size; count++
    }

    fun write(array: FloatArray, length: Int) {
        for (i in 0 until length) write(array[i])
    }

    fun read(): Float {
        if (count == 0) return 0f
        val v = data[readIdx]
        readIdx = (readIdx + 1) % data.size; count--
        return v
    }

    fun read(array: FloatArray, length: Int) {
        for (i in 0 until length) array[i] = read()
    }

    fun clear() { writeIdx = 0; readIdx = 0; count = 0; data.fill(0f) }
}

// ── All‑Pass Filter ─────────────────────────────────────────────────
private class AllPassFilter(delaySamples: Int, private val g: Float) {
    private val buffer = FloatArray(delaySamples)
    private var index = 0

    fun process(input: Float): Float {
        val delayed = buffer[index]
        val output = -g * input + delayed + g * buffer[(index - 1 + buffer.size) % buffer.size]
        buffer[index] = input
        index = (index + 1) % buffer.size
        return output
    }

    fun reset() { buffer.fill(0f); index = 0 }
}