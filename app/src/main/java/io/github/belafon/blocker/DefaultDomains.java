package io.github.belafon.blocker;

import java.util.Arrays;
import java.util.List;

/**
 * Websites blocked out of the box. They are added to the list once, on first start, so they show
 * up in the app and can be removed like any other entry (with the password, when locked).
 * Subdomains are blocked automatically, so "pornhub.com" also covers "www.pornhub.com" etc.
 */
final class DefaultDomains {
    static final List<String> DOMAINS = Arrays.asList(
            // Social media / video
            "x.com",
            "twitter.com",
            "t.co",
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "youtubei.googleapis.com", // used by the YouTube app

            // Adult sites
            "pornhub.com",
            "pornhub.org",
            "xvideos.com",
            "xvideos2.com",
            "xnxx.com",
            "xhamster.com",
            "xhamster.desi",
            "xhamster2.com",
            "xhamsterlive.com",
            "youporn.com",
            "redtube.com",
            "tube8.com",
            "spankbang.com",
            "eporner.com",
            "beeg.com",
            "tnaflix.com",
            "empflix.com",
            "motherless.com",
            "porn.com",
            "porntrex.com",
            "porntube.com",
            "pornone.com",
            "4tube.com",
            "fuq.com",
            "txxx.com",
            "upornia.com",
            "hclips.com",
            "hqporner.com",
            "drtuber.com",
            "nuvid.com",
            "sunporno.com",
            "ixxx.com",
            "thumbzilla.com",
            "youjizz.com",
            "slutload.com",
            "anysex.com",
            "porndig.com",
            "pornhat.com",
            "noodlemagazine.com",
            "daftsex.com",
            "erome.com",
            "fapello.com",
            "thothub.to",
            "porngifs.com",
            "sex.com",
            "brazzers.com",
            "realitykings.com",
            "bangbros.com",
            "naughtyamerica.com",
            "mofos.com",
            "digitalplayground.com",
            "teamskeet.com",
            "onlyfans.com",
            "fansly.com",
            "manyvids.com",
            "clips4sale.com",
            "chaturbate.com",
            "stripchat.com",
            "bongacams.com",
            "livejasmin.com",
            "cam4.com",
            "camsoda.com",
            "myfreecams.com",
            "flirt4free.com",
            "imlive.com",
            "rule34.xxx",
            "rule34.paheal.net",
            "e-hentai.org",
            "exhentai.org",
            "nhentai.net",
            "hanime.tv",
            "hentaihaven.xxx",
            "hentai2read.com",
            "literotica.com",
            "pornpics.com",
            "imagefap.com",
            "redgifs.com");

    private DefaultDomains() {}
}
