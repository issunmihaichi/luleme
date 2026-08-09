package com.java.myapplication.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONTokener

/**
 * 用系统 WebView 抓取 missav 页面渲染后的 HTML。
 *
 * 为什么不用 Jsoup：missav 视频页是 JS 渲染 + WAF 反爬（Cloudflare 类），静态 HTTP 请求
 * 会被 403 或返回空壳页。WebView 有完整浏览器指纹 + JS 执行能力，能通过 WAF 挑战、
 * 等前端渲染完成后读取 DOM（outerHTML），再交给 [MissavScraper.parseHtml] 解析。
 *
 * 使用方式：必须在主线程调用 [fetch]，结果通过回调返回（HTML 或 null）。
 */
class MissavWebViewFetcher(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var onResult: ((String?) -> Unit)? = null
    private var attempts = 0
    private var extracting = false // 防止重定向多次 onPageFinished 并发 tryExtract
    private val pendingFinish = Runnable { finish(null) }

    /** 是否正在抓取（供调用方决定是否提示"稍后再试"） */
    val isBusy: Boolean get() = webView != null

    companion object {
        private const val TAG = "MissavWebViewFetcher"
        private const val TIMEOUT_MS = 25_000L      // 总超时
        private const val RENDER_WAIT_MS = 4_000L   // onPageFinished 后等 JS 渲染
        private const val RETRY_WAIT_MS = 3_000L     // 内容为空时再等
        private const val MAX_ATTEMPTS = 3
    }

    /** 在主线程调用；onResult 收到渲染后的页面 HTML（失败/超时为 null） */
    @SuppressLint("SetJavaScriptEnabled")
    fun fetch(url: String, onResult: (String?) -> Unit) {
        if (webView != null) {
            Log.w(TAG, "already fetching, ignore $url")
            return
        }
        this.onResult = onResult
        attempts = 0

        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            // 使用系统默认 UA（真实浏览器指纹，利于通过 WAF）
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    mainHandler.postDelayed({ tryExtract() }, RENDER_WAIT_MS)
                }
            }
        }
        // 挂载到 Activity decor（1x1 不可见即可正常渲染 JS）
        val decor = (context as? Activity)?.window?.decorView as? ViewGroup
        if (decor != null) {
            decor.addView(wv, ViewGroup.LayoutParams(1, 1))
        }
        webView = wv
        mainHandler.postDelayed(pendingFinish, TIMEOUT_MS)
        wv.loadUrl(url)
    }

    private fun tryExtract() {
        val wv = webView ?: return
        if (extracting) return
        extracting = true
        wv.evaluateJavascript(
            "(function(){return document.documentElement.outerHTML;})()"
        ) { htmlJson ->
            extracting = false
            val html = decodeJsString(htmlJson)
            if (html == null || looksLikeChallengePage(html)) {
                attempts++
                if (attempts < MAX_ATTEMPTS) {
                    mainHandler.postDelayed({ tryExtract() }, RETRY_WAIT_MS)
                } else {
                    finish(html?.takeIf { !looksLikeChallengePage(it) })
                }
            } else {
                finish(html)
            }
        }
    }

    /** WAF 挑战页/空壳页特征（Cloudflare "Just a moment"、ThisAV 空壳首页等） */
    private fun looksLikeChallengePage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("just a moment") ||
            lower.contains("cf-challenge") ||
            lower.contains("cf_chl") ||
            // 空壳首页特征：无视频元数据
            (!lower.contains("missav") && !lower.contains("品番") &&
                !lower.contains("女優") && !lower.contains("ジャンル") && lower.length < 20_000)
    }

    /** evaluateJavascript 返回值是 JSON 编码字符串（带引号与转义），还原为原始 HTML */
    private fun decodeJsString(json: String?): String? {
        if (json.isNullOrBlank() || json == "null") return null
        return try {
            JSONTokener(json).nextValue()?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "decodeJsString failed")
            null
        }
    }

    /** 释放 WebView（Activity 销毁时调用，防泄漏） */
    fun cancel() {
        mainHandler.post {
            finish(null)
        }
    }

    private fun finish(html: String?) {
        mainHandler.removeCallbacks(pendingFinish)
        webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
        onResult?.invoke(html)
        onResult = null
    }
}
