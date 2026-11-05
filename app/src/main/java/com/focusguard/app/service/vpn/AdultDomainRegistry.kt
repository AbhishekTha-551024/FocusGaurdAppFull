package com.focusguard.app.service.vpn

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdultDomainRegistry
 * ===================
 * Authoritative registry of known adult domains and adult TLDs for DNS-level blocking.
 */
@Singleton
class AdultDomainRegistry @Inject constructor() {

    companion object {
        // Adult TLDs — any domain ending in these is blocked outright
        val ADULT_TLDS = setOf(".xxx", ".porn", ".adult", ".sex", ".cam", ".erotic")

        // Authoritative adult domain blocklist
        val ADULT_DOMAIN_BLOCKLIST: Set<String> = hashSetOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "stripchat.com", "chaturbate.com",
            "livejasmin.com", "bongacams.com", "camsoda.com", "cam4.com", "onlyfans.com",
            "fansly.com", "redtube.com", "youporn.com", "xhamster.com", "spankbang.com",
            "eporner.com", "tnaflix.com", "tube8.com", "brazzers.com", "bangbros.com",
            "naughtyamerica.com", "realitykings.com", "mofos.com", "twistys.com",
            "beeg.com", "hqporner.com", "porn.com", "sex.com", "hardsextube.com",
            "rule34.xxx", "e-hentai.org", "nhentai.net", "gelbooru.com", "danbooru.donmai.us",
            "luscious.net", "hitomi.la", "hanime.tv", "hentaihaven.xxx",
            "heavy-r.com", "motherless.com", "efukt.com", "xcafe.com", "youjizz.com",
            "empflix.com", "keezmovies.com", "extremetube.com", "4tube.com", "cliphunter.com",
            "porntube.com", "sunporno.com", "fapvid.com", "tubegalore.com", "porndoe.com",
            "javhd.com", "caribbeancom.com", "1pondo.tv", "tokyo-hot.com", "r18.com",
            "javlibrary.com", "javbus.com", "avgle.com", "missav.com", "jable.tv",
            "adultwork.com", "escortdirectory.com", "eros.com", "tryst.link", "skipthegames.com",
            "furaffinity.net", "e621.net", "inkbunny.net", "sofurry.com",
            "erome.com", "bdsmlr.com", "fetlife.com", "adultfriendfinder.com", "ashleymadison.com",
            "kink.com", "digitalplayground.com", "hustler.com", "penthouse.com", "playboy.com"
        )
    }

    fun isAdultDomain(name: String): Boolean {
        val lower = name.lowercase().trimEnd('.')
        // 1. Adult TLD check
        for (tld in ADULT_TLDS) {
            if (lower.endsWith(tld)) return true
        }
        // 2. Exact domain / subdomain check
        for (blocked in ADULT_DOMAIN_BLOCKLIST) {
            if (lower == blocked || lower.endsWith(".$blocked")) return true
        }
        return false
    }
}

