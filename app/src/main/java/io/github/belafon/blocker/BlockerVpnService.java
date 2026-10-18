package io.github.belafon.blocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local VPN that captures only DNS traffic. The system is told to use a fake DNS server
 * (10.111.222.2) and only that address is routed into the VPN, so all other traffic goes
 * directly to the network as usual. Queries for blocked domains get an NXDOMAIN answer,
 * everything else is forwarded to the real DNS servers of the underlying network.
 */
public class BlockerVpnService extends VpnService {
    private static final String TAG = "BlockerVpn";

    public static final String ACTION_START = "io.github.belafon.blocker.START";
    public static final String ACTION_STOP = "io.github.belafon.blocker.STOP";

    private static final String VPN_ADDRESS = "10.111.222.1";
    private static final String FAKE_DNS = "10.111.222.2";
    private static final String[] FALLBACK_DNS = {"1.1.1.1", "8.8.8.8"};
    private static final int DNS_TIMEOUT_MS = 2500;
    private static final int MAX_UPSTREAM_TRIES = 3;

    private static final String CHANNEL_STATUS = "status_min";
    private static final String OLD_CHANNEL_STATUS = "status";
    private static final String CHANNEL_ALERTS = "alerts";
    private static final int NOTIFICATION_STATUS = 1;
    private static final int NOTIFICATION_TURNED_OFF = 2;

    private static volatile boolean running;
    private static volatile BlockerVpnService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService forwarder = Executors.newFixedThreadPool(4);
    private DnsLoop loop;
    private volatile Set<String> domains;
    private volatile List<Upstream> upstreamCache;
    private volatile long upstreamCacheTime;
    private volatile Upstream lastGoodUpstream;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (sp, key) -> {
        if (Prefs.isDomainsKey(key)) domains = Prefs.getDomains(this);
    };

    public static boolean isRunning() {
        return running;
    }

    /**
     * Whether "Block connections without VPN" is on (only known while the filter runs, API 29+). That
     * setting only lets traffic through the VPN, but this VPN carries DNS only, so with it on
     * nothing else can reach the internet.
     */
    public static boolean isLockdown() {
        BlockerVpnService service = instance;
        return running && service != null && Build.VERSION.SDK_INT >= 29 && service.isLockdownEnabled();
    }

    /** Whether the user turned on "Always-on VPN" for Blocker (only known while the filter runs, API 29+). */
    public static boolean isAlwaysOnVpn() {
        BlockerVpnService service = instance;
        return running && service != null && Build.VERSION.SDK_INT >= 29 && service.isAlwaysOn();
    }

    public static void start(Context c) {
        Prefs.setFilterEnabled(c, true);
        c.startForegroundService(new Intent(c, BlockerVpnService.class).setAction(ACTION_START));
    }

    /** Only works when the filter was already disabled in Prefs, so a stray intent can't stop it. */
    public static void stop(Context c) {
        Prefs.setFilterEnabled(c, false);
        if (running) c.startService(new Intent(c, BlockerVpnService.class).setAction(ACTION_STOP));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        domains = Prefs.getDomains(this);
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefsListener);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Stay a foreground service for as long as the filter runs. Without that, Android (and
        // especially MIUI/HyperOS) stops the service soon after the app is closed. The
        // notification uses a minimal channel, and the user can hide it in system settings.
        goForeground();

        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            if (!Prefs.isFilterEnabled(this)) shutdown();
            return START_NOT_STICKY;
        }
        // A null intent is a sticky restart after the process was killed.
        if (intent == null && !Prefs.isFilterEnabled(this)) {
            shutdown();
            return START_NOT_STICKY;
        }
        // ACTION_START, or android.net.VpnService when the system starts us as "Always-on VPN".
        if (!startLoop()) {
            showTurnedOffAlert();
            shutdown();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        // The user tapped "Disconnect" or another VPN app took over.
        stopLoop();
        if (!Prefs.hasPassword(this)) {
            // Not locked: respect the user's choice.
            Prefs.setFilterEnabled(this, false);
            shutdown();
            return;
        }
        handler.postDelayed(() -> {
            if (Prefs.isFilterEnabled(this) && startLoop()) return;
            showTurnedOffAlert();
            shutdown();
        }, 1000);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        stopLoop();
        forwarder.shutdownNow();
        instance = null;
        super.onDestroy();
    }

    private boolean startLoop() {
        if (loop != null) return true;
        if (prepare(this) != null) return false; // the user hasn't allowed the VPN (anymore)

        Builder builder = new Builder()
                .setSession(getString(R.string.vpn_session))
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(FAKE_DNS)
                .addRoute(FAKE_DNS, 32)
                .setBlocking(false)
                .setConfigureIntent(mainActivityIntent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false);
        try {
            // Our own upstream DNS queries must not loop back into the VPN.
            builder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        ParcelFileDescriptor tun;
        try {
            tun = builder.establish();
        } catch (RuntimeException e) {
            Log.e(TAG, "establish failed", e);
            return false;
        }
        if (tun == null) return false;

        getSystemService(NotificationManager.class).cancel(NOTIFICATION_TURNED_OFF);
        loop = new DnsLoop(tun);
        new Thread(loop, "dns-filter").start();
        running = true;
        return true;
    }

    private void stopLoop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
        running = false;
    }

    private void shutdown() {
        stopLoop();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // --- DNS handling ---

    private final class DnsLoop implements Runnable {
        private final ParcelFileDescriptor tun;
        private final FileOutputStream out;
        private volatile boolean stopped;

        DnsLoop(ParcelFileDescriptor tun) {
            this.tun = tun;
            this.out = new FileOutputStream(tun.getFileDescriptor());
        }

        void stop() {
            stopped = true;
        }

        @Override
        public void run() {
            FileDescriptor fd = tun.getFileDescriptor();
            StructPollfd pollFd = new StructPollfd();
            pollFd.fd = fd;
            pollFd.events = (short) OsConstants.POLLIN;
            StructPollfd[] pollFds = {pollFd};
            byte[] buf = new byte[32767];
            try {
                while (!stopped) {
                    pollFd.revents = 0;
                    // Poll with a timeout so stop() takes effect within a second.
                    if (Os.poll(pollFds, 1000) <= 0) continue;
                    if ((pollFd.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0) break;
                    int len;
                    try {
                        len = Os.read(fd, buf, 0, buf.length);
                    } catch (ErrnoException e) {
                        if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue;
                        throw e;
                    }
                    if (len > 0) handlePacket(buf, len);
                }
            } catch (ErrnoException | IOException e) {
                if (!stopped) Log.e(TAG, "DNS loop failed", e);
            } finally {
                try {
                    tun.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handlePacket(byte[] pkt, int len) {
            if (len < 28 || (pkt[0] >> 4 & 0xF) != 4 || pkt[9] != 17) return; // IPv4 UDP only
            int udp = (pkt[0] & 0x0F) * 4;
            if (len < udp + 8 || DnsPackets.u16(pkt, udp + 2) != 53) return;
            int dnsStart = udp + 8;
            int dnsEnd = Math.min(len, udp + DnsPackets.u16(pkt, udp + 4));
            if (dnsEnd <= dnsStart) return;

            byte[] query = Arrays.copyOfRange(pkt, dnsStart, dnsEnd);
            byte[] clientIp = Arrays.copyOfRange(pkt, 12, 16);
            byte[] serverIp = Arrays.copyOfRange(pkt, 16, 20);
            int clientPort = DnsPackets.u16(pkt, udp);

            String name = DnsPackets.questionName(query);
            if (name != null && DnsPackets.isBlocked(name, domains)) {
                byte[] answer = DnsPackets.nxdomain(query);
                if (answer != null) reply(serverIp, clientIp, clientPort, answer);
                return;
            }
            try {
                forwarder.execute(() -> {
                    byte[] answer = forward(query);
                    if (answer != null) reply(serverIp, clientIp, clientPort, answer);
                });
            } catch (RuntimeException ignored) {
                // Executor already shut down.
            }
        }

        private void reply(byte[] serverIp, byte[] clientIp, int clientPort, byte[] answer) {
            byte[] packet = DnsPackets.udpPacket(serverIp, 53, clientIp, clientPort, answer);
            synchronized (out) {
                try {
                    out.write(packet);
                } catch (IOException ignored) {
                    // VPN was closed meanwhile.
                }
            }
        }
    }

    private static final class Upstream {
        final Network network; // null = default route
        final InetAddress address;

        Upstream(Network network, InetAddress address) {
            this.network = network;
            this.address = address;
        }
    }

    private byte[] forward(byte[] query) {
        List<Upstream> candidates = new ArrayList<>(upstreams());
        Upstream good = lastGoodUpstream;
        if (good != null && candidates.remove(good)) candidates.add(0, good);

        int tries = 0;
        for (Upstream upstream : candidates) {
            if (tries++ >= MAX_UPSTREAM_TRIES) break;
            try (DatagramSocket socket = new DatagramSocket()) {
                if (upstream.network != null) upstream.network.bindSocket(socket);
                protect(socket);
                socket.setSoTimeout(DNS_TIMEOUT_MS);
                socket.send(new DatagramPacket(query, query.length, upstream.address, 53));
                byte[] buf = new byte[4096];
                DatagramPacket response = new DatagramPacket(buf, buf.length);
                socket.receive(response);
                lastGoodUpstream = upstream;
                return Arrays.copyOf(buf, response.getLength());
            } catch (IOException e) {
                if (upstream == lastGoodUpstream) lastGoodUpstream = null;
            }
        }
        return null;
    }

    /** DNS servers of the real networks (Wi-Fi first), then public fallbacks. Cached for 30 s. */
    private List<Upstream> upstreams() {
        long now = SystemClock.elapsedRealtime();
        List<Upstream> cached = upstreamCache;
        if (cached != null && now - upstreamCacheTime < 30_000) return cached;

        List<Upstream> list = new ArrayList<>();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        List<Network> networks = new ArrayList<>(Arrays.asList(cm.getAllNetworks()));
        networks.sort(Comparator.comparingInt(n -> networkRank(cm, n)));
        for (Network network : networks) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
            LinkProperties lp = cm.getLinkProperties(network);
            if (lp == null) continue;
            for (InetAddress address : lp.getDnsServers()) list.add(new Upstream(network, address));
        }
        for (String ip : FALLBACK_DNS) {
            try {
                list.add(new Upstream(null, InetAddress.getByName(ip)));
            } catch (UnknownHostException ignored) {
            }
        }
        upstreamCache = list;
        upstreamCacheTime = now;
        return list;
    }

    private static int networkRank(ConnectivityManager cm, Network network) {
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return 10;
        int rank = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 0 : 2;
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) rank++;
        return rank;
    }

    // --- Notifications ---

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        // Channel importance can't change after creation, so the old LOW channel is replaced.
        nm.deleteNotificationChannel(OLD_CHANNEL_STATUS);
        NotificationChannel status = new NotificationChannel(
                CHANNEL_STATUS, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN);
        status.setShowBadge(false);
        nm.createNotificationChannel(status);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ALERTS, getString(R.string.alert_channel), NotificationManager.IMPORTANCE_HIGH));
    }

    private PendingIntent mainActivityIntent() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification statusNotification() {
        // Deliberately empty: Android requires a notification for a foreground service, but it
        // doesn't need to say anything. Users can turn off Blocker's notifications completely
        // and the filter keeps running.
        return new Notification.Builder(this, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(mainActivityIntent())
                .setShowWhen(false)
                .setOngoing(true)
                .build();
    }

    private void goForeground() {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_STATUS, statusNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_STATUS, statusNotification());
        }
    }

    private void showTurnedOffAlert() {
        if (!Prefs.isFilterEnabled(this)) return;
        Notification n = new Notification.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_off_title))
                .setContentText(getString(R.string.notification_off_text))
                .setContentIntent(mainActivityIntent())
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_TURNED_OFF, n);
    }
}
