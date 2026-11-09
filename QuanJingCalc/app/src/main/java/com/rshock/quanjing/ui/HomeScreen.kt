package com.rshock.quanjing.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rshock.quanjing.auto.ClickAccessibilityService
import com.rshock.quanjing.core.HeroData
import com.rshock.quanjing.core.PropheticWords
import com.rshock.quanjing.util.ConfigStore

@Composable
fun HomeScreen(
    onStartBall: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val cfg = ConfigStore.load(ctx)
    val hasOverlay = Settings.canDrawOverlays(ctx)
    val hasAccess = ClickAccessibilityService.instance != null

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("全境征才助手", fontSize = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text("v2.0", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(32.dp))

        // 状态
        StatusRow("悬浮窗权限", hasOverlay)
        StatusRow("无障碍服务", hasAccess)
        StatusRow("校准", cfg.isFullComplete)
        StatusRow("角色数据", HeroData.dataset.isNotEmpty())
        StatusRow("表数据", PropheticWords.words.isNotEmpty())
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onStartBall,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("启动悬浮球", fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("设置", fontSize = 16.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "提示：\n" +
                    "1. 先确保 5 个权限/状态都是 ✅\n" +
                    "2. 默认校准可能不适配需重新校准\n" +
                    "3. 悬浮球：短按运行/长按小菜单/拖到底部中央关闭\n" +
                    "4. 在下不是学编程的，免费的大肥鱼做的，可能有bug，有问题进群：933396955,密码:QAQ",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun StatusRow(name: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ok) "✅" else "❌", fontSize = 16.sp)
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 15.sp)
    }
}