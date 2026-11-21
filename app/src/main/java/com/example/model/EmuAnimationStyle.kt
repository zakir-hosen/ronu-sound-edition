package com.example.model

import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Immutable

@Immutable
data class EmuPhysicsProfile(
    val openDampingRatio: Float,
    val openStiffness: Float,
    val closeDurationMs: Int,
    val idleBlinkIntervalMs: Long,
    val tiltScale: Float,
    val gazeScale: Float
)

enum class EmuAnimationStyle(
    val displayName: String,
    val description: String,
    val badge: String,
    val physics: EmuPhysicsProfile
) {
    BOUNCY_PLAYFUL(
        displayName = "Bouncy Playful",
        description = "Lively elastic squash-and-stretch blinks with responsive 3D gyroscope eye-tracking.",
        badge = "Default",
        physics = EmuPhysicsProfile(
            openDampingRatio = Spring.DampingRatioMediumBouncy,
            openStiffness = Spring.StiffnessMediumLow,
            closeDurationMs = 85,
            idleBlinkIntervalMs = 9_000L,
            tiltScale = 1.0f,
            gazeScale = 1.0f
        )
    ),
    SMOOTH_ZEN(
        displayName = "Smooth Zen",
        description = "Ultra-smooth liquid transitions with zero overshoot, gentle breathing pulse, and calm tilt.",
        badge = "Calm",
        physics = EmuPhysicsProfile(
            openDampingRatio = Spring.DampingRatioNoBouncy,
            openStiffness = Spring.StiffnessLow,
            closeDurationMs = 240,
            idleBlinkIntervalMs = 11_000L,
            tiltScale = 0.65f,
            gazeScale = 0.7f
        )
    ),
    HYPER_CYBER_GLITCH(
        displayName = "Hyper Cyber-Glitch",
        description = "Crisp mechanical blinks with horizontal micro-jitter and subtle cyan scanlines on updates.",
        badge = "Cyber",
        physics = EmuPhysicsProfile(
            openDampingRatio = Spring.DampingRatioLowBouncy,
            openStiffness = Spring.StiffnessHigh,
            closeDurationMs = 45,
            idleBlinkIntervalMs = 7_500L,
            tiltScale = 1.2f,
            gazeScale = 1.15f
        )
    ),
    SLEEPY_CHILL(
        displayName = "Sleepy / Chill Mode",
        description = "Half-lidded relaxed eyes that blink slowly and lazily drift toward gyroscope tilt.",
        badge = "Chill",
        physics = EmuPhysicsProfile(
            openDampingRatio = Spring.DampingRatioNoBouncy,
            openStiffness = Spring.StiffnessVeryLow,
            closeDurationMs = 170,
            idleBlinkIntervalMs = 12_500L,
            tiltScale = 0.75f,
            gazeScale = 0.8f
        )
    ),
    CURIOUS_EXPLORER(
        displayName = "Curious Explorer",
        description = "Autonomously glances around 3D space, perks up wide when tilted, and double-blinks on touch.",
        badge = "Curious",
        physics = EmuPhysicsProfile(
            openDampingRatio = Spring.DampingRatioMediumBouncy,
            openStiffness = Spring.StiffnessMedium,
            closeDurationMs = 75,
            idleBlinkIntervalMs = 8_500L,
            tiltScale = 1.15f,
            gazeScale = 1.25f
        )
    ),
    SENTINEL(
        displayName = "Sentinel (Curious + Shake)",
        description = "Tracks 3D tilt, scans around curiously when resting, and performs a dizzy 720° head-spin when shaken.",
        badge = "Sentinel",
        physics = EmuPhysicsProfile(
            openDampingRatio = Spring.DampingRatioMediumBouncy,
            openStiffness = Spring.StiffnessMedium,
            closeDurationMs = 70,
            idleBlinkIntervalMs = 8_000L,
            tiltScale = 1.1f,
            gazeScale = 1.2f
        )
    )
}
