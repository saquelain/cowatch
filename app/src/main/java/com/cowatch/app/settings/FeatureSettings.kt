package com.cowatch.app.settings

/**
 * Every user-facing feature has its own on/off switch. The Settings screen is
 * generated from [Feature.entries], so adding a feature here is all it takes to
 * get a toggle for it.
 */
enum class Feature(
    val title: String,
    val description: String,
    val defaultOn: Boolean,
    val section: Section,
) {
    VIDEO_CALL("Video call", "Share your camera and see your partner while you watch.", true, Section.CALL),
    VOICE_CHAT("Voice chat", "Talk to each other through the microphone.", true, Section.CALL),
    SELF_PREVIEW("Show my camera preview", "Small mirror of your own camera in the corner.", true, Section.CALL),
    SPEAKERPHONE("Speakerphone", "Play your partner's voice on the loudspeaker instead of the earpiece.", true, Section.CALL),
    DUCK_MOVIE("Lower movie volume during call", "Keeps the movie a little quieter so you can hear each other.", true, Section.CALL),

    PLAYBACK_SYNC("Playback sync", "Play, pause and seek together with your partner.", true, Section.WATCH),
    DRIFT_CORRECTION("Auto re-sync", "Quietly nudges playback back in sync if you drift apart.", true, Section.WATCH),
    SHARE_LINKS("Auto-share video links", "When you open a link, your partner's app opens it too.", true, Section.WATCH),
    STREAM_FILES("Stream my videos to partner", "Videos from your phone play on your partner's phone too, sent over your call.", true, Section.WATCH),
    KEEP_SCREEN_ON("Keep screen on", "Stop the screen from sleeping during a movie.", true, Section.WATCH),

    TEXT_CHAT("Text chat", "Send messages without pausing the movie.", true, Section.FUN),
    REACTIONS("Emoji reactions", "Send floating hearts, laughs and gasps.", true, Section.FUN),
    HAPTICS("Vibrate on partner activity", "A light buzz when your partner reacts or messages.", true, Section.FUN),

    CODE_EXPIRY("Codes expire after 10 minutes", "Unused one-time codes stop working automatically.", true, Section.PRIVACY),
    ;

    val key: String get() = "feature_" + name.lowercase()

    enum class Section(val title: String) {
        CALL("Call"),
        WATCH("Watching"),
        FUN("Chat & reactions"),
        PRIVACY("Privacy"),
    }
}

data class AppSettings(
    val features: Map<Feature, Boolean> = Feature.entries.associateWith { it.defaultOn },
    val displayName: String = "",
    val signalingHost: String = DEFAULT_SIGNALING_HOST,
    val stunUrl: String = DEFAULT_STUN,
    val turnUrl: String = "",
    val turnUser: String = "",
    val turnPassword: String = "",
) {
    fun isOn(feature: Feature): Boolean = features[feature] ?: feature.defaultOn

    /** Features whose parent is off are effectively off too. */
    fun enabled(feature: Feature): Boolean = when (feature) {
        Feature.SELF_PREVIEW -> isOn(feature) && isOn(Feature.VIDEO_CALL)
        Feature.DRIFT_CORRECTION, Feature.SHARE_LINKS, Feature.STREAM_FILES -> isOn(feature) && isOn(Feature.PLAYBACK_SYNC)
        Feature.SPEAKERPHONE -> isOn(feature) && isOn(Feature.VOICE_CHAT)
        else -> isOn(feature)
    }

    fun with(feature: Feature, on: Boolean) = copy(features = features + (feature to on))

    companion object {
        const val DEFAULT_SIGNALING_HOST = "0.peerjs.com"
        const val DEFAULT_STUN = "stun:stun.l.google.com:19302"
    }
}
