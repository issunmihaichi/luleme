package com.java.myapplication.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * missav 页面解析结果（仅保留需求字段：名字/品番/女優/ジャンル，省去監督、社团名、日期、メーカー）。
 */
data class MissavMeta(
    val title: String = "",      // 名字（页面标题）
    val code: String = "",       // 品番，如 SNOS-275-UNCENSORED-LEAK
    val actress: String = "",    // 女優
    val genres: List<String> = emptyList() // ジャンル标签
)

/**
 * 抓取 missav 视频页并解析元数据。
 * 解析策略（逐级 fallback）：JSON-LD(VideoObject) → og meta → 页面标签区块(dt/dd, th/td) → 正则。
 * 任何一步失败都安全返回 null，不影响正常记录流程。
 */
object MissavScraper {

    private const val TAG = "MissavScraper"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val CODE_REGEX =
        Regex("\\b([A-Z0-9]{2,10}-\\d{2,7}(?:-[A-Za-z0-9]+)*)\\b", RegexOption.IGNORE_CASE)

    /** 同步阻塞抓取解析，应在 IO 线程调用；失败记录日志并返回 null */
    fun parse(url: String): MissavMeta? = try {
        val doc = Jsoup.connect(url)
            .timeout(10_000)
            .userAgent(USER_AGENT)
            .referrer("https://missav.ws/")
            .followRedirects(false) // 保持 host 白名单语义，不跟随到白名单外域名
            .get()
        buildMeta(doc)
    } catch (e: Exception) {
        Log.w(TAG, "missav scrape failed", e)
        null
    }

    private fun buildMeta(doc: Document): MissavMeta {
        val jsonLd = parseJsonLd(doc)
        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        val ogDesc = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim().orEmpty()
        val h1 = doc.selectFirst("h1")?.text()?.trim().orEmpty()

        val title = jsonLd?.optString("name")?.takeIf { it.isNotBlank() }
            ?: ogTitle.ifBlank { h1 }

        val actress = jsonLd?.optJSONArray("actor")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val it = arr.opt(i)
                when (it) {
                    is JSONObject -> it.optString("name")
                    else -> it?.toString()
                }
            }.firstOrNull { it.isNotBlank() }
        } ?: findLabeled(doc, listOf("女優", "出演者", "出演"))
            .flatMap { it.split('/') }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val genres = jsonLd?.optJSONArray("genre")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }?.takeIf { it.isNotEmpty() } ?: findLabeled(doc, listOf("ジャンル", "タグ", "カテゴリ", "カテゴリー"))
            .flatMap { it.split('/') }
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val code = findLabeled(doc, listOf("品番", "番号", "ID"))
            .firstNotNullOfOrNull { CODE_REGEX.find(it)?.groupValues?.get(1)?.uppercase() }
            ?: CODE_REGEX.find(title.ifBlank { ogDesc })?.groupValues?.get(1)?.uppercase()
            ?: ""

        return MissavMeta(
            title = title.ifBlank { ogTitle }.trim(),
            code = code,
            actress = actress,
            genres = genres.distinct()
        )
    }

    /** 解析页面内 JSON-LD，返回第一个 VideoObject/Movie 对象（兼容对象与数组两种形态） */
    private fun parseJsonLd(doc: Document): JSONObject? {
        doc.select("script[type=application/ld+json]").forEach { script ->
            val data = script.data().trim()
            if (data.isEmpty()) return@forEach
            // 先按数组处理（多对象/单元素），再按单对象处理
            val obj = try {
                JSONArray(data).let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        arr.opt(i) as? JSONObject
                    }.firstOrNull { it.opt("@type")?.toString()?.lowercase()?.contains("videoobject") == true }
                        ?: arr.optJSONObject(0)
                }
            } catch (e: Exception) {
                try {
                    JSONObject(data)
                } catch (e2: Exception) {
                    null
                }
            } ?: return@forEach
            val type = obj.opt("@type")?.toString()?.lowercase() ?: ""
            if (type.contains("videoobject") || type.contains("movie") || obj.has("genre")) {
                return obj
            }
        }
        return null
    }

    /**
     * 在页面的 dt/dd、th/td 标签区块中查找指定标签对应的值文本列表。
     * 兼容 missav 常见的 <dl><dt>女優</dt><dd><a>河北彩花</a></dd></dl> 结构。
     */
    private fun findLabeled(doc: Document, labels: List<String>): List<String> {
        val result = mutableListOf<String>()

        doc.select("dt, th").forEach { keyEl ->
            val key = keyEl.text().trim()
            if (labels.any { key.contains(it) }) {
                val valueEl = keyEl.nextElementSibling()
                if (valueEl != null && valueEl.tagName() in setOf("dd", "td")) {
                    val value = valueEl.text().trim()
                    if (value.isNotBlank()) result.add(value)
                }
            }
        }
        return result.distinct()
    }
}
