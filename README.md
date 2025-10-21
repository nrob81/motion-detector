# motion-detector

Battery-efficient motion detection for background location tracking.

## Problem

GPS tracking apps drain battery by constantly requesting location updates.
Worse: indoor GPS is inaccurate, creating noise in your data.

**The solution?** Only request GPS when the device is actually moving.

## Features

- 🔋 **Battery-efficient**: No GPS drain when stationary
- 🎯 **Accurate**: Spike filtering ignores phone pickup/handling
- 🔄 **Stable**: Hysteresis prevents flapping between states
- 📊 **Clean data**: Indoor stationary periods = no noisy GPS points
- 🧪 **Tunable**: All parameters exposed for testing and optimization
- 📱 **Platform-independent**: Pure Kotlin, no Android dependencies

## How it works

1. **Low-pass filter** estimates gravity from accelerometer
2. **High-pass filter** extracts linear acceleration
3. **RMS calculation** over 2-second rolling window
4. **Spike rejection** filters phone pickup events
5. **Hysteresis state machine** prevents false triggers

Result: Reliable motion detection without GPS overhead.

## Installation

### Gradle (via JitPack)
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.nrob81:motion-detector:1.0.0'
}
```

## Usage
```kotlin
// 1. Implement Logger interface
class ConsoleLogger : Logger {
    override fun d(tag: String, message: String) = println("[$tag] $message")
    override fun w(tag: String, message: String, throwable: Throwable?) = println("[$tag] WARN: $message")
    override fun e(tag: String, message: String, throwable: Throwable?) = println("[$tag] ERROR: $message")
    override fun setContext(key: String, value: Any) {}
}

// 2. Initialize MotionController
MotionController.init(
    config = MotionConfig.optimized(),
    logger = ConsoleLogger()
)

// 3. Feed accelerometer data
sensorManager.registerListener(object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
        MotionController.onAcceleration(
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            timestamp = System.currentTimeMillis()
        )
    }
})

// 4. Observe motion state
lifecycleScope.launch {
    MotionController.motionFlow.collect { state ->
        if (state.isMoving) {
            // Request GPS update
        }
    }
}
```

## Configuration

Use presets or customize:
```kotlin
// Optimized preset (tuned via grid search)
MotionConfig.optimized()

// Default preset
MotionConfig.default()

// Custom configuration
MotionConfig(
    motionStartThreshold = 0.7f,  // m/s² to trigger motion
    motionStopThreshold = 0.3f,   // m/s² to trigger still
    startDelayMs = 5000,          // delay before STILL → MOVING
    stopDelayMs = 2500,           // delay before MOVING → STILL
    spikeThreshold = 1.5f,        // reject acceleration spikes
    rmsAlpha = 0.4f               // RMS smoothing factor
)
```

## License

MIT - use freely, see [LICENSE](LICENSE)

## Background

Built for a family GPS safety tracker to monitor my daughter's location.

**Challenge:** Balance battery life with reliable tracking.  
**Solution:** Motion-based GPS - only track when actually moving.

This library is the battle-tested core, extracted and optimized via grid search
on 50+ minutes of real-world data (walking, driving, phone handling, stationary).

Open-sourced to help others solve similar problems.