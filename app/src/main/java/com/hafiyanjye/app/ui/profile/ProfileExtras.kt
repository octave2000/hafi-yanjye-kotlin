package com.hafiyanjye.app.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hafiyanjye.app.ui.push.PushSettings

/** The profile's push switch for this phone (ui/push), in the Notifications section. */
@Composable
fun ProfileExtras(modifier: Modifier = Modifier) {
    PushSettings(modifier)
}
