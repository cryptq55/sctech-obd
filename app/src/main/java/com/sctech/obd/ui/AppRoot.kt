package com.sctech.obd.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sctech.obd.BuildConfig
import com.sctech.obd.ObdApp
import com.sctech.obd.R
import com.sctech.obd.core.ConnectionState
import com.sctech.obd.core.EcuState
import com.sctech.obd.core.ObdSession
import com.sctech.obd.data.AppPrefs
import com.sctech.obd.data.ThemeMode
import com.sctech.obd.ui.theme.Sct

private enum class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    HOME("home", R.string.tab_home, R.drawable.ic_home),
    DTC("dtc", R.string.tab_dtc, R.drawable.ic_warning),
    VEHICLE("vehicle", R.string.tab_vehicle, R.drawable.ic_car),
}

private const val ROUTE_ABOUT = "about"

@Composable
fun ObdAppRoot(app: ObdApp) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    // Negative responses from the ECU (e.g. "service not supported")
    LaunchedEffect(Unit) {
        ObdSession.ecuErrors.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = Sct.colors.bg,
        topBar = { TopBar(app.prefs, onAbout = { nav.navigate(ROUTE_ABOUT) { launchSingleTop = true } }) },
        bottomBar = { if (currentRoute != ROUTE_ABOUT) Column {
            HorizontalDivider(thickness = 1.dp, color = Sct.colors.hairline)
            NavigationBar(containerColor = Sct.colors.bg, tonalElevation = 0.dp) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label), fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Sct.colors.textPrimary,
                            selectedTextColor = Sct.colors.textPrimary,
                            indicatorColor = Sct.colors.surface2,
                            unselectedIconColor = Sct.colors.textTertiary,
                            unselectedTextColor = Sct.colors.textTertiary,
                        ),
                    )
                }
            }
        } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(app.prefs, onOpenTroubleCodes = { nav.switchTab(Tab.DTC.route) })
            }
            composable(Tab.DTC.route) { DtcScreen(app.dtcRepository) }
            composable(Tab.VEHICLE.route) { VehicleScreen() }
            composable(ROUTE_ABOUT) { AboutScreen(app.dtcRepository, onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun TopBar(prefs: AppPrefs, onAbout: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Sct.colors.topBarTop, Sct.colors.bg)))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.sctech_wordmark),
            contentDescription = "SCTech",
            colorFilter = ColorFilter.tint(Sct.colors.textPrimary),
            modifier = Modifier.height(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "OBD · v${BuildConfig.VERSION_NAME}",
            color = Sct.colors.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        ConnectionPill()
        OverflowMenu(prefs, onAbout)
    }
}

@Composable
private fun ConnectionPill() {
    val connection by ObdSession.connection.collectAsStateWithLifecycle()
    val ecu by ObdSession.ecuState.collectAsStateWithLifecycle()
    val c = Sct.colors
    val (label, color, pulsing) = when (val state = connection) {
        ConnectionState.Disconnected -> Triple(R.string.pill_disconnected, c.textSecondary, false)
        is ConnectionState.Connecting -> Triple(R.string.pill_connecting, c.warning, true)
        is ConnectionState.Failed -> Triple(R.string.pill_error, c.danger, false)
        is ConnectionState.Connected -> when (ecu) {
            EcuState.READY -> Triple(if (state.demo) R.string.pill_demo else R.string.pill_ready, c.success, false)
            EcuState.NO_RESPONSE, EcuState.ERROR -> Triple(R.string.pill_no_response, c.danger, false)
            else -> Triple(R.string.pill_searching, c.warning, true)
        }
    }
    StatusPill(stringResource(label), color, pulsing)
}

@Composable
private fun OverflowMenu(prefs: AppPrefs, onAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current by prefs.themeMode.collectAsStateWithLifecycle()

    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(R.drawable.ic_more),
                contentDescription = stringResource(R.string.menu_more),
                tint = Sct.colors.textSecondary,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Sct.colors.surface1,
        ) {
            Eyebrow(stringResource(R.string.theme_title), Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            listOf(
                ThemeMode.DARK to R.string.theme_dark,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.SYSTEM to R.string.theme_system,
            ).forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label), color = Sct.colors.textPrimary) },
                    leadingIcon = {
                        RadioButton(
                            selected = mode == current,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Sct.colors.accent,
                                unselectedColor = Sct.colors.textTertiary,
                            ),
                        )
                    },
                    onClick = {
                        prefs.setThemeMode(mode)
                        open = false
                    },
                )
            }
            HorizontalDivider(thickness = 1.dp, color = Sct.colors.hairline, modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.about_title), color = Sct.colors.textPrimary) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = Sct.colors.textSecondary)
                },
                onClick = {
                    open = false
                    onAbout()
                },
            )
        }
    }
}
