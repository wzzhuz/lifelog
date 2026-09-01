package com.zwz.lifelog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 使用说明。
 *
 * 存在的意义：应用里有几处信息不解释就只能靠猜——
 * 状态色代表什么、「判断基准」为什么有时是 30 天、倒填时间的入口在哪。
 *
 * ⚠️ 维护约定：改动状态计算阈值、记录入口或备份行为时，
 * **必须同步更新本页内容**。说明文案与功能脱节后，
 * 会变成错误信息，比没有说明更糟。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageGuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("使用说明") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ---------- 1. 状态色 ----------
            GuideSection(title = "卡片左侧的颜色条代表什么") {
                ColorRow(Color(0xFF16A34A), "绿色 · 新鲜", "刚做不久，还早")
                ColorRow(Color(0xFFE08C00), "黄色 · 快到了", "接近该做的时间")
                ColorRow(Color(0xFFE5484D), "红色 · 该做了", "已经到或超过判断基准")
                ColorRow(Color(0xFF9CA3AF), "灰色 · 待记录", "这个事件还没有任何记录")
            }

            // ---------- 2. 判断基准 ----------
            GuideSection(title = "「判断基准」是怎么来的") {
                Text(
                    "系统按顺序取第一个可用的：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                NumberItem(1, "你给这个事件设的「期望间隔」")
                NumberItem(2, "历史平均间隔（记录满 2 条后自动算出）")
                NumberItem(3, "都没有则兜底 30 天")
                Spacer(Modifier.height(10.dp))
                Text(
                    "第 2 条是本应用的关键：看病、同房这类事没有固定规律，" +
                        "你不必提前设间隔。记满两次后，系统会自己学会你的节奏，" +
                        "之后就按这个节奏判断该不该做。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ---------- 3. 记录入口 ----------
            GuideSection(title = "几个记录入口的区别") {
                EntryRow("桌面小组件", "点一下直接记当前时间", "高频项最快，可在桌面一键完成")
                EntryRow("首页「随手一点」", "点一下直接记当前时间", "钉选的常用事件")
                EntryRow("卡片右侧 ✓", "记当前时间", "通用兜底，两步")
                EntryRow(
                    "详情页「带备注记」",
                    "可选时间 + 文字 + 照片",
                    "**补录昨天、前天的事也走这里**——" +
                        "把时间改成实际发生的那天即可，不必是今天"
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "前三个入口只记「当前时间」，快到极致；" +
                        "需要写东西或补录时才进详情页。两类场景分开优化，不混在一起。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- 4. 搜索 ----------
            GuideSection(title = "医生问「上次检查什么时候」怎么办") {
                Text(
                    "在首页搜索框直接搜关键词即可。搜索会匹配三处：\n" +
                        "• 事件名\n" +
                        "• 记录备注全文\n" +
                        "• 分类标签",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "所以看病时顺手写一句备注（比如「社区医院，血常规正常」），" +
                        "以后搜「血常规」就能立刻翻出来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ---------- 5. 首页布局与排序 ----------
            GuideSection(title = "首页怎么排、怎么换样子") {
                Text(
                    "设置页「首页布局」可切换三种模式：\n" +
                        "• 紧凑：一屏约 8~9 个，信息精简\n" +
                        "• 舒适：一屏约 5~6 个，信息最完整\n" +
                        "• 按分类分组：同类事件归在一起，点分类名可折叠",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "紧凑与舒适模式下，**长按卡片可以拖动排序**，" +
                        "把常用的排到前面。松手后顺序自动保存。\n" +
                        "分组模式下也能拖，但**只能在同一个分类内**移动。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "分组模式下拖动仅限组内。跨组需要进详情页改分类标签——" +
                        "那是「移动」不是「排序」。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- 6. 归档 ----------
            GuideSection(title = "不用的事件怎么收起来") {
                Text(
                    "进事件详情页 → 右上角 ⋮ → 归档。\n" +
                        "归档后该事件不显示在首页，但**记录完整保留**。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "随时可在「设置 → 已归档事件」中查看、恢复或彻底删除。\n" +
                        "彻底删除会连带删除该事件的所有记录，删除前会二次确认。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ---------- 7. 模板 ----------
            GuideSection(title = "事件从哪来") {
                Text(
                    "新建事件有两个途径：\n" +
                        "• 首页右下角 + 号，从零创建\n" +
                        "• 设置页「模板」分组，从 34 个预置模板里挑",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "模板入口可以随时再来，不是只有第一次能导入。" +
                        "已经添加过的模板会置灰，不会重复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "也可以从文件导入模板（JSON 格式），" +
                        "适合一次性添加一批自定义事件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ---------- 6. 数据 ----------
            GuideSection(title = "数据存在哪、怎么备份") {
                Text(
                    "• 只存在手机本地，不联网、不上传、无账号\n" +
                        "• 设置页可导出 JSON / ZIP（含照片）/ CSV\n" +
                        "• 每次修改自动保留最近 4 份本地备份\n" +
                        "• 建议每月手动导出一次",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠️ 卸载应用或清理应用数据会丢失记录，导出是唯一的保险。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE5484D)
                )
            }

            // ---------- 6. 设计原则 ----------
            GuideSection(title = "这个应用不做什么") {
                Text(
                    "• 不做连续打卡、成就徽章、排行榜\n" +
                        "• 不发到期提醒通知\n" +
                        "• 不联网、不同步、不统计、无广告\n\n" +
                        "原因很简单：它是「上次什么时候做」的外部记忆，不是自律工具。" +
                        "晚两天换床单不是失败，应用不该制造焦虑。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GuideSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ColorRow(color: Color, label: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = color,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            modifier = Modifier
                .width(6.dp)
                .height(28.dp)
        ) {}
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumberItem(no: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "$no.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(22.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EntryRow(name: String, effect: String, tip: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(132.dp)
            )
            Text(
                effect,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val cleanTip = tip.replace("**", "")
        Text(
            cleanTip,
            style = MaterialTheme.typography.labelMedium,
            color = if (tip.contains("**")) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 0.dp, top = 2.dp)
        )
    }
}
