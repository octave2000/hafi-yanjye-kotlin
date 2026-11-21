package com.hafiyanjye.app.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.ui.LocalContainer
import com.hafiyanjye.app.ui.browse.browseGraph
import com.hafiyanjye.app.ui.chat.chatGraph
import com.hafiyanjye.app.ui.cvstudio.cvStudioGraph
import com.hafiyanjye.app.ui.find.findGraph
import com.hafiyanjye.app.ui.kit.Badge
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.listings.listingsGraph
import com.hafiyanjye.app.ui.match.matchGraph
import com.hafiyanjye.app.ui.nav.LocalMe
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.onboarding.onboardingGraph
import com.hafiyanjye.app.ui.pings.pingsGraph
import com.hafiyanjye.app.ui.profile.profileGraph
import com.hafiyanjye.app.ui.rentals.rentalsGraph
import com.hafiyanjye.app.ui.talent.talentGraph
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import com.hafiyanjye.app.ui.trip.tripGraph

private data class Tab(val route: String, val label: String, @DrawableRes val icon: Int)

// Same tabs as the web's bottom bar (components/shell/bottom-nav.tsx).
private val HirerTabs = listOf(
    Tab(Routes.Find, "Find help", R.drawable.ic_search),
    Tab(Routes.Jobs, "My jobs", R.drawable.ic_briefcase_business),
    Tab(Routes.Rentals, "Rentals", R.drawable.ic_key_round),
    Tab(Routes.Chat, "Chat", R.drawable.ic_messages_square),
    Tab(Routes.Profile, "Profile", R.drawable.ic_user_round),
)

private val WorkerTabs = listOf(
    Tab(Routes.Pings, "Pings", R.drawable.ic_radar),
    Tab(Routes.Listings, "Listings", R.drawable.ic_store),
    Tab(Routes.Chat, "Chat", R.drawable.ic_messages_square),
    Tab(Routes.Cv, "CV", R.drawable.ic_file_user),
    Tab(Routes.Profile, "Profile", R.drawable.ic_user_round),
)

/**
 * The signed-in app. Tab roots sit between the brand bar and the bottom tab
 * bar; pushed screens (a request, a chat thread, a form) take the whole
 * screen and bring their own top bar (kit DetailScaffold).
 */
@Composable
fun MainShell(me: Me) {
    val reducedMotion = rememberReducedMotion()
    // Switching side starts over on that side's first tab with a fresh back stack.
    key(me.role) {
        val tabs = if (me.role == Role.Worker) WorkerTabs else HirerTabs
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val current = entry?.destination?.route ?: tabs.first().route
        val onTab = current in Routes.Tabs

        // A notification tap: open its screen once the shell is up, on top of the home tab.
        val pending = LocalContainer.current.pendingRoute
        LaunchedEffect(nav) {
            pending.collect { route ->
                if (route != null) {
                    pending.value = null
                    if (route in Routes.Tabs) {
                        nav.openTab(route)
                    } else {
                        nav.navigate(route) { launchSingleTop = true }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalMe provides me) {
            Column(Modifier.fillMaxSize().background(Hafi.Canvas)) {
                AnimatedVisibility(
                    visible = onTab,
                    enter = expandVertically(tween(if (reducedMotion) 0 else 180)),
                    exit = shrinkVertically(tween(if (reducedMotion) 0 else 150)),
                ) {
                    TopBar(me, onBell = { nav.navigate(Routes.Notifications) { launchSingleTop = true } })
                }
                NavHost(
                    navController = nav,
                    startDestination = tabs.first().route,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    enterTransition = { fadeIn(tween(if (reducedMotion) 0 else 180)) },
                    exitTransition = { fadeOut(tween(if (reducedMotion) 0 else 120)) },
                ) {
                    findGraph(nav)
                    matchGraph(nav)
                    browseGraph(nav)
                    tripGraph(nav)
                    pingsGraph(nav)
                    onboardingGraph(nav)
                    chatGraph(nav)
                    rentalsGraph(nav)
                    listingsGraph(nav)
                    talentGraph(nav)
                    cvStudioGraph(nav)
                    profileGraph(nav)
                }
                AnimatedVisibility(
                    visible = onTab,
                    enter = expandVertically(tween(if (reducedMotion) 0 else 180)),
                    exit = shrinkVertically(tween(if (reducedMotion) 0 else 150)),
                ) {
                    BottomBar(tabs, current, onSelect = nav::openTab)
                }
            }
        }
    }
}

/** Tabs keep their own state; pressing the current one again does nothing. */
private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TopBar(me: Me, onBell: () -> Unit) {
    val unread by LocalContainer.current.badges.notificationsUnread.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .drawBehind {
                drawLine(Hafi.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(28.dp))
        Text("Hafi Yanjye", style = HafiType.Subheading, modifier = Modifier.weight(1f))
        // Which side of the app this is, in the client's words.
        Badge("Job ${me.role.label}", Tone.Accent)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = SemanticsRole.Button, onClick = onBell)
                .semantics {
                    contentDescription = if (unread > 0) "Notifications, $unread unread" else "Notifications"
                },
            contentAlignment = Alignment.Center,
        ) {
            HIcon(R.drawable.ic_bell, size = 22.dp, tint = Hafi.Fg)
            if (unread > 0) CountDot(unread, Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 8.dp))
        }
    }
}

/** A small count on an icon ("3", "9+"). The number is also in the control's spoken name. */
@Composable
private fun CountDot(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(16.dp).background(Hafi.Accent).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 9) "9+" else count.toString(),
            style = HafiType.Micro.copy(letterSpacing = HafiType.Caption.letterSpacing),
            color = Hafi.AccentFg,
        )
    }
}

@Composable
private fun BottomBar(tabs: List<Tab>, current: String, onSelect: (String) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val chatUnread by LocalContainer.current.badges.chatUnread.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .drawBehind { drawLine(Hafi.Line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .selectableGroup(),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == current
            val unread = if (tab.route == Routes.Chat) chatUnread else 0
            val marker by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(if (reducedMotion) 0 else 200),
                label = "tab marker",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .selectable(selected = selected, role = SemanticsRole.Tab, onClick = { onSelect(tab.route) })
                    .semantics { if (unread > 0) contentDescription = "${tab.label}, $unread unread" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The marker and the bolder label show the current tab, not colour alone.
                Box(
                    Modifier
                        .width(40.dp)
                        .height(3.dp)
                        .graphicsLayer { scaleX = marker }
                        .background(Hafi.Accent),
                )
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        HIcon(tab.icon, size = 22.dp, tint = if (selected) Hafi.AccentStrong else Hafi.FgMuted)
                        if (unread > 0) {
                            CountDot(unread, Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp))
                        }
                    }
                    Text(
                        tab.label,
                        style = HafiType.Micro.copy(
                            letterSpacing = HafiType.Caption.letterSpacing,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (selected) Hafi.AccentStrong else Hafi.FgMuted,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
