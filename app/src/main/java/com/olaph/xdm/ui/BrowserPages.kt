package com.olaph.xdm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaph.xdm.browser.BrowserPage
import com.olaph.xdm.browser.BrowserTab
import com.olaph.xdm.browser.BrowserViewModel
import com.olaph.xdm.engine.UrlInput

// ---- الصفحة الرئيسية / الاتصال السريع ----

@Composable
fun BrowserHome(vm: BrowserViewModel, tab: BrowserTab) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val recent = if (tab.incognito) emptyList() else history.take(10)

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tab.incognito) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.VisibilityOff, null, Modifier.size(40.dp))
                    Text("علامة تبويب للتصفح الخفي", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "لن يُحفظ سجل التصفح، وستُحذف ملفات تعريف الارتباط الخاصة بهذه الجلسة عند إغلاق آخر علامة تبويب خفية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("الاتصال السريع", style = MaterialTheme.typography.titleSmall)
        }

        if (bookmarks.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "أضف صفحة إلى الإشارات المرجعية من القائمة لتظهر هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(bookmarks, key = { "b${it.id}" }) { b ->
            Column(
                Modifier.clickable { vm.openUrl(b.url) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (UrlInput.displayHost(b.url) ?: b.title).take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Text(
                    b.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("الأخيرة", style = MaterialTheme.typography.titleSmall)
            }

            items(recent, key = { "h${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { h ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { vm.openUrl(h.url) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        h.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        h.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---- مبدّل علامات التبويب ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherPage(vm: BrowserViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("علامات التبويب (${vm.tabs.size})") },
                navigationIcon = {
                    IconButton(onClick = { vm.page = BrowserPage.TAB }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.closeAllTabs() }) {
                        Icon(Icons.Filled.DeleteSweep, "إغلاق جميع علامات التبويب")
                    }

                    IconButton(onClick = { vm.newTab(incognito = true) }) {
                        Icon(Icons.Filled.VisibilityOff, "علامة تبويب خفية جديدة")
                    }

                    IconButton(onClick = { vm.newTab() }) {
                        Icon(Icons.Filled.Add, "علامة تبويب جديدة")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vm.tabs.toList(), key = { it.id }) { tab ->
                val selected = tab.id == vm.currentId

                Card(
                    onClick = { vm.selectTab(tab.id) },
                    border = if (selected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (tab.incognito) {
                            MaterialTheme.colorScheme.inverseSurface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (tab.incognito) {
                            MaterialTheme.colorScheme.inverseOnSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (tab.incognito) {
                            Icon(Icons.Filled.VisibilityOff, null, Modifier.size(16.dp))
                        }

                        Text(
                            tab.displayTitle,
                            modifier = Modifier.weight(1f)
                                .padding(start = if (tab.incognito) 6.dp else 0.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                        )

                        IconButton(
                            onClick = { vm.closeTab(tab.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Close, "إغلاق علامة التبويب", Modifier.size(18.dp))
                        }
                    }

                    Text(
                        if (tab.showHome) "الرئيسية" else tab.url.orEmpty(),
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 16.dp,
                            top = 8.dp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ---- الإشارات المرجعية / سجل التصفح ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksPage(vm: BrowserViewModel) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإشارات المرجعية") },
                navigationIcon = {
                    IconButton(onClick = { vm.page = BrowserPage.TAB }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        if (bookmarks.isEmpty()) {
            EmptyMessage(padding, "لا توجد إشارات مرجعية حتى الآن")
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(bookmarks, key = { it.id }) { b ->
                    ListItem(
                        headlineContent = {
                            Text(
                                b.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                b.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.removeBookmark(b.id) }) {
                                Icon(Icons.Filled.Delete, "إزالة الإشارة المرجعية")
                            }
                        },
                        modifier = Modifier.clickable { vm.openUrl(b.url) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPage(vm: BrowserViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل التصفح") },
                navigationIcon = {
                    IconButton(onClick = { vm.page = BrowserPage.TAB }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { confirmClear = true },
                        enabled = history.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.DeleteSweep, "مسح سجل التصفح")
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            EmptyMessage(padding, "سجل التصفح فارغ")
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(history, key = { it.id }) { h ->
                    ListItem(
                        headlineContent = {
                            Text(
                                h.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                h.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.removeHistory(h.id) }) {
                                Icon(Icons.Filled.Close, "إزالة من سجل التصفح")
                            }
                        },
                        modifier = Modifier.clickable { vm.openUrl(h.url) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح سجل التصفح؟") },
            text = {
                Text("سيتم حذف جميع الإدخالات وعددها ${history.size}. ستبقى الإشارات المرجعية محفوظة.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearHistory()
                        confirmClear = false
                    }
                ) {
                    Text("مسح")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("إلغاء")
                }
            },
        )
    }
}

@Composable
private fun EmptyMessage(padding: PaddingValues, text: String) {
    Box(
        Modifier.padding(padding).fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
