package com.zwz.lifelog.data

import com.zwz.lifelog.domain.model.Template
import org.json.JSONArray
import org.json.JSONObject

/** 外部模板文件的类型标记。 */
const val TEMPLATE_FILE_TYPE = "lifelog-templates"

/** 解析后的外部模板文件。 */
data class TemplateFile(
    val version: Int,
    val templates: List<Template>
)

/**
 * 外部模板文件的解析结果。
 *
 * 刻意不用 Result：调用方需要区分「文件根本不是模板」和「模板内容有问题」，
 * 两者的提示文案不一样，混在一起会给用户一个说不清的错误。
 */
sealed class TemplateParseResult {
    data class Ok(val file: TemplateFile) : TemplateParseResult()
    data class WrongType(val actualType: String?) : TemplateParseResult()
    data class BadFormat(val detail: String) : TemplateParseResult()
}

/**
 * 解析外部模板 JSON。
 *
 * 格式：
 * ```json
 * {
 *   "type": "lifelog-templates",
 *   "version": 1,
 *   "templates": [
 *     { "name": "吃药", "emoji": "💊", "targetDays": 1, "tag": "健康" }
 *   ]
 * }
 * ```
 *
 * 为什么必须有 `type`：数据备份文件和模板文件都是 JSON，
 * 不靠类型标记就只能猜结构，猜错会把模板当数据导入。
 */
object TemplateFileParser {

    fun parse(text: String): TemplateParseResult {
        val root: JSONObject = try {
            JSONObject(text)
        } catch (e: Exception) {
            return TemplateParseResult.BadFormat("不是合法的 JSON：${e.message}")
        }

        val type = root.optString("type", "").trim()
        if (type.isBlank()) {
            return TemplateParseResult.WrongType(null)
        }
        if (!type.equals(TEMPLATE_FILE_TYPE, ignoreCase = true)) {
            return TemplateParseResult.WrongType(type)
        }

        val arr: JSONArray = root.optJSONArray("templates")
            ?: return TemplateParseResult.BadFormat("缺少 templates 数组")

        val list = mutableListOf<Template>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name", "").trim()
            if (name.isBlank()) continue          // 没名字的模板直接跳过

            // targetDays 可能为 null 也可能干脆不写，两种都视为「无固定节奏」。
            // 注意不能用 optInt 的默认值 0 来判断：0 天没有意义，
            // 这里把「没写」和「写了 0」都归为 null。
            val td: Int? = if (o.has("targetDays") && !o.isNull("targetDays")) {
                o.optInt("targetDays", 0).takeIf { it > 0 }
            } else null

            list.add(
                Template(
                    name = name,
                    emoji = o.optString("emoji", "\uD83D\uDCCC").ifBlank { "\uD83D\uDCCC" },
                    targetDays = td,
                    tag = o.optString("tag", "").ifBlank { "自定义" }
                )
            )
        }

        if (list.isEmpty()) {
            return TemplateParseResult.BadFormat("模板列表为空，或每条模板都缺少 name")
        }

        return TemplateParseResult.Ok(
            TemplateFile(
                version = root.optInt("version", 1),
                templates = list
            )
        )
    }
}
