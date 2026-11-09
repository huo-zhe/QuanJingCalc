package com.rshock.quanjing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rshock.quanjing.util.HistoryStore
import androidx.compose.runtime.remember

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val records = remember { HistoryStore.load(ctx) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("历史记录（${records.size}）", fontSize = 20.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("返回") }
        }
        Spacer(Modifier.height(12.dp))

        if (records.isEmpty()) {
            Text("暂无记录", fontSize = 14.sp)
        } else {
            LazyColumn {
                items(records) { r ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(r.time, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline)
                            Text("词条：${r.tags.replace("\t", " / ")}",
                                fontSize = 13.sp)
                            Text("选择：${r.result.replace("\t", " / ")}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}