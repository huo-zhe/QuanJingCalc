package com.rshock.quanjing.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rshock.quanjing.auto.ClickAccessibilityService
import com.rshock.quanjing.core.HeroData
import com.rshock.quanjing.core.PropheticWords
import com.rshock.quanjing.util.ConfigStore
import com.rshock.quanjing.util.HistoryStore
import com.rshock.quanjing.util.SettingsStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background

@Composable
fun SettingsScreen(
    onReCalibrateFull: () -> Unit,
    onReCalibrateAuto: () -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
    onShowHistory: () -> Unit,
    onDone: () -> Unit
) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(SettingsStore.mode) }
    var ocrZoom by remember { mutableStateOf(SettingsStore.ocrZoom) }
    var debug by remember { mutableStateOf(SettingsStore.debugMode) }
    var ballAlpha by remember { mutableStateOf(SettingsStore.ballAlpha) }
    var ballSize by remember { mutableStateOf(SettingsStore.ballSizeDp) }
    var ballShape by remember { mutableStateOf(SettingsStore.ballShape) }
    var delayTag by remember { mutableStateOf(SettingsStore.delayTag) }
    var delayStart by remember { mutableStateOf(SettingsStore.delayStart) }

    val cfg = remember { ConfigStore.load(ctx) }

    Box(Modifier.fillMaxSize()) {
        // 1. 底层滚动内容
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(60.dp)) // 给顶部固定栏留空间

        // ============ 1. 基础与校准 ============
        ExpandableSection("运行与校准", defaultExpanded = true) {
            // 权限状态
            PermissionRow("悬浮窗权限", Settings.canDrawOverlays(ctx)) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            PermissionRow("无障碍服务", ClickAccessibilityService.instance != null) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Spacer(Modifier.height(8.dp))

            // 校准
            Text(if (cfg.isFullComplete) "✅ 已校准（招募条件）" else "❌ 未校准（招募条件）", fontSize = 14.sp)
            Text(if (cfg.isAutoComplete) "✅ 已校准（自动点位）" else "❌ 未校准（自动点位）", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = onReCalibrateFull, modifier = Modifier.weight(1f)) { Text("校准招募条件") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onReCalibrateAuto, modifier = Modifier.weight(1f)) { Text("校准自动点位") }
            }
            Text("默认校准可能不适配，需重新校准,全自动是用颜色判断的，别弄错了Ciallo～(∠・ω< )⌒★", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)

            Spacer(Modifier.height(8.dp))

            // 运行模式
            Text("运行模式", fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == "ball", onClick = { mode = "ball"; SettingsStore.mode = "ball" })
                Text("悬浮球模式", fontSize = 14.sp)
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = mode == "panel", onClick = { mode = "panel"; SettingsStore.mode = "panel" })
                Text("结果面板模式", fontSize = 14.sp)
            }

            // OCR 放大
            Text("OCR 放大倍数", fontSize = 16.sp)
            Row {
                listOf(1.5f, 2.0f, 3.0f, 4.0f).forEach { z ->
                    FilterChip(
                        selected = ocrZoom == z, onClick = { ocrZoom = z; SettingsStore.ocrZoom = z },
                        label = { Text("${z}x") }, modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        // ============ 2. 全自动延迟 ============
        ExpandableSection("点击延迟（毫秒）") {
            DelayInput("词条连点间隔", delayTag) { delayTag = it; SettingsStore.delayTag = it }
            DelayInput("开始招募延迟", delayStart) { delayStart = it; SettingsStore.delayStart = it }
        }

        // ============ 3. 悬浮球外观 ============
        ExpandableSection("悬浮球外观") {
            Text("大小：$ballSize dp", fontSize = 14.sp)
            Slider(value = ballSize.toFloat(), onValueChange = { ballSize = it.toInt(); SettingsStore.ballSizeDp = ballSize; FloatWindow.get(ctx).rebuild() }, valueRange = 36f..96f)
            Text("透明度：${(ballAlpha * 100).toInt()}%", fontSize = 14.sp)
            Slider(value = ballAlpha, onValueChange = { ballAlpha = it; SettingsStore.ballAlpha = ballAlpha; FloatWindow.get(ctx).rebuild() }, valueRange = 0.2f..1.0f)
            Row {
                FilterChip(selected = ballShape == "circle", onClick = { ballShape = "circle"; SettingsStore.ballShape = "circle"; FloatWindow.get(ctx).rebuild() }, label = { Text("圆形") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = ballShape == "square", onClick = { ballShape = "square"; SettingsStore.ballShape = "square"; FloatWindow.get(ctx).rebuild() }, label = { Text("方形") })
            }
        }

            // ============ 4. 数据与调试 ============
            ExpandableSection("数据管理与调试") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = debug, onCheckedChange = { debug = it; SettingsStore.debugMode = it })
                    Spacer(Modifier.width(8.dp))
                    Text("调试模式（显示OCR结果）", fontSize = 14.sp)
                }

                Spacer(Modifier.height(8.dp))
                Text("角色数：${HeroData.dataset.size} / 表行数：${PropheticWords.words.size}", fontSize = 12.sp)
                Row {
                    OutlinedButton(onClick = onImportJson, modifier = Modifier.weight(1f)) { Text("导入 JSON") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) { Text("导出 JSON") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { HeroData.load(ctx); PropheticWords.load(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("重新加载数据") }
            }
        // ============ 5. 历史记录 ============
        ExpandableSection("历史记录") {
            Button(onClick = onShowHistory, modifier = Modifier.fillMaxWidth()) { Text("查看最近 50 次") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { HistoryStore.clear(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("清空历史") }
        }
        }

        // 2. 顶部固定悬浮栏
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设置", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Button(onClick = onDone) {
                Text("保存退出")
            }
        }
    }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (granted) "✅" else "❌", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!granted) {
            TextButton(onClick = onClick) { Text("去授权") }
        }
    }
}
@Composable
private fun DelayInput(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    // value 外部变化时同步
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.width(100.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.filter { c -> c.isDigit() }.take(5)
                text.toIntOrNull()?.let { v -> onChange(v) }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        )
        Spacer(Modifier.width(8.dp))
        Text("ms", fontSize = 14.sp)
    }

}
@Composable
private fun ExpandableSection(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 标题行（点击切换展开/收起）
        Row(
            Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(if (expanded) "▲ 收起" else "▼ 展开", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }

        // 内容区
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp)) {
                content()
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}