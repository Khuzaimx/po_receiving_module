package com.sellernest.poreceiving.scan.datawedge

/**
 * Zebra DataWedge Intent API constants (§4.1, §2.1). DataWedge has no normal
 * Android API -- it is configured entirely by sending it broadcast Intents,
 * and it reports scans the same way, via its own broadcast back to this app.
 *
 * These action/extra/param names are Zebra's documented DataWedge Intent API
 * as of recent DataWedge versions. **This is the one integration in the app
 * that cannot be exercised without real Zebra hardware** -- verify the exact
 * decoder parameter names against a real device's exported profile XML
 * (DataWedge app > profile > Export) or Zebra's current DataWedge Intent API
 * guide before field deployment.
 */
object DataWedgeConfig {
    const val API_ACTION = "com.symbol.datawedge.api.ACTION"

    const val EXTRA_CREATE_PROFILE = "com.symbol.datawedge.api.CREATE_PROFILE"
    const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"

    const val PROFILE_NAME = "PoReceiving"

    /** The action this app's own receiver listens for scan results on --
     *  configured into the profile's INTENT output plugin, not a DataWedge
     *  constant itself. */
    const val SCAN_INTENT_ACTION = "com.sellernest.poreceiving.action.SCAN"
    const val SCAN_INTENT_KEY_DATA = "com.symbol.datawedge.data_string"
    const val SCAN_INTENT_KEY_SYMBOLOGY = "com.symbol.datawedge.label_type"

    /** §4.3: exactly these symbologies enabled, matching CameraX/ML Kit (M2.4)
     *  one for one. */
    val enabledDecoderParams = listOf(
        "decoder_code128",
        "decoder_code39",
        "decoder_code93",
        "decoder_codabar",
        "decoder_ean13",
        "decoder_ean8",
        "decoder_upca",
        "decoder_upce0",
        "decoder_interleaved2of5",
        "decoder_qrcode",
        "decoder_datamatrix",
        "decoder_pdf417",
    )

    /** Decoders DataWedge commonly ships enabled by default that §4.3 does not
     *  list -- explicitly turned off so "and no others" actually holds. */
    val disabledDecoderParams = listOf(
        "decoder_aztec",
        "decoder_maxicode",
        "decoder_rss14",
        "decoder_rssexpanded",
        "decoder_msi",
        "decoder_code11",
        "decoder_d2of5",
        "decoder_upce1",
        "decoder_tlc39",
    )
}
