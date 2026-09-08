package uz.komil.mediapro.net

import android.net.Uri

object AdBlocker {
    private val AD_HOSTS = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adnxs.com",
        "adservice.google.com",
        "popcash.net",
        "exoclick.com",
        "adsterra.com",
        "trafficfactory.biz",
        "propellerads.com",
        "adroll.com",
        "clickadu.com",
        "outbrain.com",
        "taboola.com",
        "mgid.com",
        "revcontent.com",
        "admob.com",
        "inmobi.com",
        "unityads.unity3d.com",
        "applovin.com",
        "vungle.com",
        "chartboost.com",
        "an.yandex.ru",
        "adriver.ru",
        "begun.ru",
        "cpaexchange.ru"
    )

    private val BETTING_HOSTS = listOf(
        "1xbet",
        "melbet",
        "mostbet",
        "pin-up",
        "bet365"
    )

    private val AD_PATH_SEGMENTS = listOf(
        "/ads/",
        "/ad/",
        "/ad.js",
        "/ads.js",
        "/banner/",
        "/banners/",
        "/popunder",
        "/popup.js"
    )

    fun isAd(url: String): Boolean {
        val lower = url.lowercase()
        return try {
            val uri = Uri.parse(lower)
            val host = uri.host ?: ""
            if (host.isEmpty()) return false

            // 1. Exact host or subdomain of known ad network
            if (AD_HOSTS.any { host == it || host.endsWith(".$it") }) return true

            // 2. Betting/gambling host check with specific exclusion for legitimate sites (e.g. vulkan graphics API)
            if (BETTING_HOSTS.any { host.contains(it) }) return true
            if (host.contains("vulkan")) {
                val isLegitVulkan = host == "vulkan.org" || host.endsWith(".vulkan.org") ||
                        host == "vulkan-tutorial.com" || host.endsWith(".vulkan-tutorial.com") ||
                        host.contains("khronos")
                if (!isLegitVulkan && (host.contains("casino") || host.contains("bet") || host.contains("club") || host.contains("slot") || host.contains("777") || host.startsWith("vulkan-") || host.startsWith("vulkan."))) {
                    return true
                }
            }

            // 3. Path segments targeting ads (not generic words in whole URL like "advertising" in article titles)
            val path = uri.path ?: ""
            AD_PATH_SEGMENTS.any { path.contains(it) }
        } catch (_: Throwable) {
            false
        }
    }

    const val JS_AD_BLOCK = """
        (function() {
            var css = '.ad, .ads, .advertisement, [id*="google_ads"], iframe[src*="ads"], .banner-ads, [class*="banner"], [class*="popup"], [id*="banner"], [id*="popup"], .adsbygoogle { display: none !important; visibility: hidden !important; height: 0 !important; max-height: 0 !important; pointer-events: none !important; }';
            var s = document.createElement('style');
            s.appendChild(document.createTextNode(css));
            (document.head || document.documentElement).appendChild(s);
            window.open = function() { return null; };
        })();
    """
}
