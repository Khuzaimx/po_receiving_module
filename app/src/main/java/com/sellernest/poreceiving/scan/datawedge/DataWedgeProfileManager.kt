package com.sellernest.poreceiving.scan.datawedge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §2.1: "Create and manage the app's DataWedge profile programmatically on
 * first launch so field deployment requires no per-device configuration."
 *
 * Every call here is a plain [Context.sendBroadcast] to an action DataWedge
 * listens for. On a device without DataWedge installed, nothing is listening,
 * so this is a silent, harmless no-op -- Android never throws for a broadcast
 * with zero receivers. That is also why this is safe to call on every app
 * launch rather than gating it on "first launch" with a persisted flag:
 * DataWedge's own CREATE_PROFILE/SET_CONFIG commands are idempotent, so
 * re-applying the same configuration is self-healing if the profile is ever
 * edited or removed by hand.
 */
@Singleton
class DataWedgeProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureProfileConfigured() {
        sendCreateProfile()
        sendAssociateApp()
        sendBarcodeDecoderConfig()
        sendIntentOutputConfig()
    }

    private fun sendCreateProfile() {
        sendApiCommand(DataWedgeConfig.EXTRA_CREATE_PROFILE, DataWedgeConfig.PROFILE_NAME)
    }

    /** Scopes the profile to this app so DataWedge only routes scans to it
     *  while it's the foreground app -- not to every app on the device. */
    private fun sendAssociateApp() {
        val appConfig = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        sendSetConfig(
            Bundle().apply {
                putString("PROFILE_NAME", DataWedgeConfig.PROFILE_NAME)
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE", "UPDATE")
                putParcelableArrayList("APP_LIST", arrayListOf(appConfig))
            },
        )
    }

    private fun sendBarcodeDecoderConfig() {
        val decoderParams = Bundle().apply {
            DataWedgeConfig.enabledDecoderParams.forEach { putBoolean(it, true) }
            DataWedgeConfig.disabledDecoderParams.forEach { putBoolean(it, false) }
        }
        sendSetConfig(
            Bundle().apply {
                putString("PROFILE_NAME", DataWedgeConfig.PROFILE_NAME)
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE", "UPDATE")
                putBundle(
                    "PLUGIN_CONFIG",
                    Bundle().apply {
                        putString("PLUGIN_NAME", "BARCODE")
                        putString("RESET_CONFIG", "true")
                        putBundle("PARAM_LIST", decoderParams)
                    },
                )
            },
        )
    }

    /** Configures DataWedge's INTENT output plugin to broadcast each scan to
     *  [DataWedgeConfig.SCAN_INTENT_ACTION], which [DataWedgeScanReceiver] listens for. */
    private fun sendIntentOutputConfig() {
        val intentParams = Bundle().apply {
            putString("intent_output_enabled", "true")
            putString("intent_action", DataWedgeConfig.SCAN_INTENT_ACTION)
            putString("intent_delivery", "2") // 2 = broadcast intent
        }
        sendSetConfig(
            Bundle().apply {
                putString("PROFILE_NAME", DataWedgeConfig.PROFILE_NAME)
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE", "UPDATE")
                putBundle(
                    "PLUGIN_CONFIG",
                    Bundle().apply {
                        putString("PLUGIN_NAME", "INTENT")
                        putString("RESET_CONFIG", "true")
                        putBundle("PARAM_LIST", intentParams)
                    },
                )
            },
        )
    }

    private fun sendSetConfig(config: Bundle) {
        sendApiCommand(DataWedgeConfig.EXTRA_SET_CONFIG, config)
    }

    private fun sendApiCommand(extraKey: String, extraValue: Any) {
        val intent = Intent(DataWedgeConfig.API_ACTION)
        when (extraValue) {
            is String -> intent.putExtra(extraKey, extraValue)
            is Bundle -> intent.putExtra(extraKey, extraValue)
            else -> error("Unsupported DataWedge API extra type: ${extraValue::class}")
        }
        context.sendBroadcast(intent)
    }
}
