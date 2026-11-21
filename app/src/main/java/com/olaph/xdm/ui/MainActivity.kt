package com.olaph.xdm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaph.xdm.XdmApp
import com.olaph.xdm.browser.BrowserPage
import com.olaph.xdm.browser.BrowserViewModel
import com.olaph.xdm.service.DownloadService

// A FragmentActivity because BiometricPrompt (private folder unlock) needs one.
class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private val browserVm: BrowserViewModel by viewModels()

    private val permissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            requestRuntimePermissions(this, permissions::launch)
            handle(intent)
        }

        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                XdmRoot(vm, browserVm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as XdmApp).container.onAppVisible()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_PRIVATE ->
                vm.openPrivateRequest.value = true

            ACTION_OPEN_DOWNLOAD ->
                intent.getLongExtra(
                    DownloadService.EXTRA_ID, -1
                ).takeIf { it > 0 }?.let {
                    vm.openDownload(it)
                }

            ACTION_REFRESH_LINK ->
                intent.getLongExtra(
                    DownloadService.EXTRA_ID, -1
                ).takeIf { it > 0 }?.let {
                    vm.requestLinkRefresh(it)
                }
        }
    }

    companion object {
        const val ACTION_OPEN_PRIVATE =
            "com.olaph.xdm.OPEN_PRIVATE"

        const val ACTION_OPEN_DOWNLOAD =
            "com.olaph.xdm.OPEN_DOWNLOAD"

        const val ACTION_REFRESH_LINK =
            "com.olaph.xdm.REFRESH_LINK"
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.checkClipboard(this)
    }
}

internal fun requestRuntimePermissions(
    activity: ComponentActivity,
    launch: (Array<String>) -> Unit
) {
    val wanted = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT < 29) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.filter {
        ContextCompat.checkSelfPermission(
            activity, it
        ) != PackageManager.PERMISSION_GRANTED
    }

    if (wanted.isNotEmpty()) {
        launch(wanted.toTypedArray())
    }
}

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_BROWSER = "browser"
private const val ROUTE_PRIVATE = "private"

@Composable
fun XdmRoot(vm: MainViewModel, browser: BrowserViewModel) {

    val settings by vm.settings.collectAsStateWithLifecycle()
    val showSheet by vm.showAddSheet.collectAsStateWithLifecycle()
    val sheetPrefill by vm.addSheetPrefill.collectAsStateWithLifecycle()
    val vaultReady by vm.vaultReady.collectAsStateWithLifecycle()
    val openPrivate by vm.openPrivateRequest.collectAsStateWithLifecycle()

    XdmTheme(settings.theme) {

        var route by rememberSaveable {
            mutableStateOf(ROUTE_LIST)
        }

        var backRoute by rememberSaveable {
            mutableStateOf(ROUTE_LIST)
        }

        var detailId by rememberSaveable {
            mutableLongStateOf(-1L)
        }

        LaunchedEffect(openPrivate) {
            if (openPrivate) {
                vm.openPrivateRequest.value = false
                route = ROUTE_PRIVATE
            }
        }

        val openDownload by vm.openDownloadRequest.collectAsStateWithLifecycle()

        LaunchedEffect(openDownload) {
            val id = openDownload ?: return@LaunchedEffect

            vm.openDownloadRequest.value = null
            detailId = id
            backRoute = ROUTE_LIST
            route = ROUTE_DETAIL
        }

        DisposableEffect(browser) {
            browser.downloadHandler = {
                vm.openAddSheet(it)
            }

            onDispose {
                browser.downloadHandler = null
            }
        }

        DisposableEffect(browser) {

            browser.refreshHandler = { id, capture ->
                vm.offerReplacement(
                    id,
                    capture.url,
                    capture.headers,
                    capture.pageUrl
                )

                route = ROUTE_LIST
            }

            browser.autoRefreshHandler = { id, capture, probe ->
                vm.applyRefreshedLink(
                    id,
                    capture.url,
                    capture.headers,
                    capture.pageUrl,
                    probe
                )

                route = ROUTE_LIST
            }

            onDispose {
                browser.refreshHandler = null
                browser.autoRefreshHandler = null
            }
        }

        val refreshRequest by vm.linkRefreshRequest.collectAsStateWithLifecycle()

        LaunchedEffect(refreshRequest) {
            val d = refreshRequest ?: return@LaunchedEffect

            vm.linkRefreshRequest.value = null
            browser.startRefresh(d)
            route = ROUTE_BROWSER
        }

        val changeUrlFor by vm.changeUrlFor.collectAsStateWithLifecycle()

        changeUrlFor?.let { d ->
            ChangeUrlDialog(
                d,
                onDismiss = {
                    vm.changeUrlFor.value = null
                },
                onCheck = { url ->
                    vm.changeUrlFor.value = null
                    vm.offerReplacement(d.id, url)
                }
            )
        }

        val replacement by vm.replacement.collectAsStateWithLifecycle()

        replacement?.let {
            ReplacementDialog(
                it,
                onConfirm = vm::confirmReplacement,
                onDismiss = vm::dismissReplacement
            )
        }

        when (route) {

            ROUTE_DETAIL -> {
                BackHandler {
                    route = backRoute
                }

                DetailScreen(
                    vm,
                    detailId,
                    onBack = {
                        route = backRoute
                    }
                )
            }

            ROUTE_SETTINGS -> {
                BackHandler {
                    route = backRoute
                }

                SettingsScreen(
                    vm,
                    onBack = {
                        route = backRoute
                    }
                )
            }

            ROUTE_PRIVATE -> {
                BackHandler {
                    route = ROUTE_LIST
                }

                PrivateScreen(
                    onBack = {
                        route = ROUTE_LIST
                    }
                )
            }

            else -> {

                val inBrowser = route == ROUTE_BROWSER

                BackHandler(enabled = inBrowser) {
                    if (!browser.handleBack()) {
                        route = ROUTE_LIST
                    }
                }

                val showNav =
                    !inBrowser || browser.page == BrowserPage.TAB

                Scaffold(
                    bottomBar = {

                        if (showNav) {
                            NavigationBar {

                                NavigationBarItem(
                                    selected = !inBrowser,
                                    onClick = {
                                        route = ROUTE_LIST
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Filled.Download,
                                            null
                                        )
                                    },
                                    label = {
                                        Text("التنزيلات")
                                    }
                                )

                                NavigationBarItem(
                                    selected = inBrowser,
                                    onClick = {
                                        route = ROUTE_BROWSER
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Filled.Public,
                                            null
                                        )
                                    },
                                    label = {
                                        Text("المتصفح")
                                    }
                                )
                            }
                        }
                    }
                ) { padding ->

                    Box(
                        Modifier
                            .padding(padding)
                            .consumeWindowInsets(padding)
                    ) {

                        if (inBrowser) {

                            BrowserScreen(
                                browser,
                                onOpenSettings = {
                                    backRoute = ROUTE_BROWSER
                                    route = ROUTE_SETTINGS
                                }
                            )

                        } else {

                            ListScreen(
                                vm,

                                onOpenDetail = {
                                    detailId = it
                                    backRoute = ROUTE_LIST
                                    route = ROUTE_DETAIL
                                },

                                onOpenSettings = {
                                    backRoute = ROUTE_LIST
                                    route = ROUTE_SETTINGS
                                },

                                onOpenPrivate = {
                                    route = ROUTE_PRIVATE
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showSheet) {

            AddDownloadSheet(
                container = vm.container,
                prefill = sheetPrefill,
                defaultConnections = settings.connections,
                privateAvailable = vaultReady,
                defaultPrivate = settings.savePrivateByDefault,
                onDismiss = vm::closeAddSheet,
                onAdded = vm::closeAddSheet,
                onYoutube = vm::openYoutube,
                onOpenExisting = vm::openDownload
            )
        }

        DisposableEffect(browser) {

            browser.youtubeHandler = {
                vm.openYoutube(it)
            }

            onDispose {
                browser.youtubeHandler = null
            }
        }

        val youtubeLink by vm.youtubeLink.collectAsStateWithLifecycle()
        val context = LocalContext.current

        youtubeLink?.let { link ->

            YoutubeSheet(
                container = vm.container,
                link = link,
                defaultQuality = settings.youtubeQuality,
                connections = settings.connections,
                privateAvailable = vaultReady,
                defaultPrivate = settings.savePrivateByDefault,

                onDismiss = vm::closeYoutube,

                onDone = {
                    vm.closeYoutube()
                    Toast.makeText(
                        context,
                        it,
                        Toast.LENGTH_SHORT
                    ).show()
                },

                onOpenExisting = vm::openDownload
            )
        }
    }
}
