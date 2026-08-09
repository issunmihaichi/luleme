package com.java.myapplication.data

/**
 * missav 域名白名单共享判定。
 * MainActivity（URL 校验）与 MissavWebViewFetcher（WebView 导航拦截）共用，
 * 避免两处重复维护导致新增镜像域名时漏改一处。
 *
 * 匹配规则（防御性扩展）：
 * - 精确匹配已知域名（missav.ws / missav.com）或其子域
 * - host 以 "missav." 开头的任意一级域（覆盖 missav 大量镜像站：missav.ai、missav.cc、
 *   missav.dm、missav.best 等，且新镜像无需改代码）；同时要求该 host 不包含更多层级
 *   （形如 evilmissav.com、missav.evil.com 的伪域名会被拒绝）
 */
object MissavUrls {

    private val ALLOWED_HOSTS = setOf("missav.ws", "missav.com")

    /** host 是否属于 missav 白名单 */
    fun isAllowedHost(host: String?): Boolean {
        if (host == null) return false
        val h = host.lowercase()
        // 精确或子域
        if (h in ALLOWED_HOSTS || ALLOWED_HOSTS.any { h.endsWith(".$it") }) return true
        // missav 前缀 + 单段顶级域（形如 missav.xxx）
        return h.startsWith("missav.") && !h.startsWith("missav..") && h.count { it == '.' } == 1
    }
}
