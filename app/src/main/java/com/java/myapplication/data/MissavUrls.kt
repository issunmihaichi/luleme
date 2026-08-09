package com.java.myapplication.data

/**
 * missav 域名白名单共享判定。
 * MainActivity（URL 校验）与 MissavWebViewFetcher（WebView 导航拦截）共用，
 * 避免两处重复维护导致新增镜像域名时漏改一处。
 */
object MissavUrls {

    private val ALLOWED_HOSTS = setOf("missav.ws", "missav.com")

    /** host 是否属于 missav 白名单（精确匹配或任意子域） */
    fun isAllowedHost(host: String?): Boolean =
        host != null && (host in ALLOWED_HOSTS || ALLOWED_HOSTS.any { host.endsWith(".$it") })
}
