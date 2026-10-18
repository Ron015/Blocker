package io.github.belafon.blocker;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Minimal IPv4/UDP/DNS parsing and building, just enough for the DNS filter. */
final class DnsPackets {
    /**
     * Encrypted DNS providers. Blocking their hostnames stops browsers from switching to
     * DNS-over-HTTPS behind the filter's back. "use-application-dns.net" is Firefox's canary
     * domain: when it doesn't resolve, Firefox turns off its automatic DoH.
     */
    static final Set<String> ALWAYS_BLOCKED = new HashSet<>(Arrays.asList(
            "use-application-dns.net",
            "dns.google",
            "dns.google.com",
            "cloudflare-dns.com",
            "one.one.one.one",
            "dns.quad9.net",
            "doh.opendns.com",
            "dns.adguard.com",
            "dns.adguard-dns.com",
            "dns.nextdns.io",
            "doh.cleanbrowsing.org",
            "dns.mullvad.net",
            "doh.dns.sb",
            "dns.alidns.com",
            "doh.pub",
            "dns.controld.com"));

    private DnsPackets() {}

    /** True if the name or any of its parent domains is in the set. */
    static boolean isBlocked(String name, Set<String> domains) {
        String n = name;
        while (true) {
            if (domains.contains(n) || ALWAYS_BLOCKED.contains(n)) return true;
            int dot = n.indexOf('.');
            if (dot < 0) return false;
            n = n.substring(dot + 1);
        }
    }

    static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    static void put16(byte[] b, int off, int value) {
        b[off] = (byte) (value >> 8);
        b[off + 1] = (byte) value;
    }

    /** Returns the offset just after the first question, or -1 if the message is malformed. */
    private static int questionEnd(byte[] q) {
        if (q.length < 12 || u16(q, 4) < 1) return -1;
        int pos = 12;
        while (true) {
            if (pos >= q.length) return -1;
            int len = q[pos] & 0xFF;
            pos++;
            if (len == 0) break;
            if ((len & 0xC0) != 0) return -1;
            pos += len;
        }
        pos += 4; // QTYPE + QCLASS
        return pos <= q.length ? pos : -1;
    }

    /** The domain asked for in the first question, lower case, or null. */
    static String questionName(byte[] q) {
        if (questionEnd(q) < 0) return null;
        StringBuilder sb = new StringBuilder();
        int pos = 12;
        int len;
        while ((len = q[pos] & 0xFF) != 0) {
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(q, pos + 1, len, StandardCharsets.US_ASCII));
            pos += len + 1;
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** An NXDOMAIN ("no such domain") answer to the query, or null if the query is malformed. */
    static byte[] nxdomain(byte[] query) {
        int end = questionEnd(query);
        if (end < 0) return null;
        byte[] r = Arrays.copyOf(query, end);
        r[2] = (byte) (0x80 | (query[2] & 0x79)); // QR=1, keep opcode and RD
        r[3] = (byte) 0x83;                       // RA=1, RCODE=3 (NXDOMAIN)
        put16(r, 4, 1);
        put16(r, 6, 0);
        put16(r, 8, 0);
        put16(r, 10, 0);
        return r;
    }

    /** Wraps a DNS payload in IPv4 + UDP headers. */
    static byte[] udpPacket(byte[] srcIp, int srcPort, byte[] dstIp, int dstPort, byte[] payload) {
        int total = 28 + payload.length;
        byte[] p = new byte[total];
        p[0] = 0x45;          // IPv4, 20 byte header
        put16(p, 2, total);
        p[6] = 0x40;          // don't fragment
        p[8] = 64;            // TTL
        p[9] = 17;            // UDP
        System.arraycopy(srcIp, 0, p, 12, 4);
        System.arraycopy(dstIp, 0, p, 16, 4);
        put16(p, 10, ipChecksum(p, 20));
        put16(p, 20, srcPort);
        put16(p, 22, dstPort);
        put16(p, 24, 8 + payload.length);
        // UDP checksum 0 = not used, which is allowed for IPv4.
        System.arraycopy(payload, 0, p, 28, payload.length);
        return p;
    }

    private static int ipChecksum(byte[] b, int len) {
        int sum = 0;
        for (int i = 0; i < len; i += 2) sum += u16(b, i);
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return ~sum & 0xFFFF;
    }
}
