package com.amurayada.common

import kotlinx.coroutines.flow.StateFlow

interface SoundDetectionManager {
    val isDetecting: StateFlow<Boolean>
    val isAncActive: StateFlow<Boolean>

    fun startDetection()
    fun stopDetection()
    fun setOnLoudSoundDetected(callback: () -> Unit)
    fun setOnSilenceDetected(callback: () -> Unit)

    fun startAnc(profile: String, intensity: Float)
    fun stopAnc()
    fun setAncIntensity(intensity: Float)
}
