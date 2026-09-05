package com.sellernest.poreceiving

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sellernest.poreceiving.navigation.PoReceivingNavGraph
import com.sellernest.poreceiving.ui.theme.PoReceivingTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the whole nav graph (§7). `configChanges` in the
 * manifest keeps this Activity alive across rotation — screens restore from
 * ViewModel state (§2.1), never from Activity re-creation — so portrait and
 * landscape are both fully supported without a screen-specific relaunch (§3.1).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoReceivingTheme {
                PoReceivingNavGraph()
            }
        }
    }
}
