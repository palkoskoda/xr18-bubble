package sk.palko.xr18bubble;

import android.content.Context;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small, deliberately limited X AIR UDP client. Every socket operation runs on one worker.
 * No command queue is persisted, retried after a timeout, or carried across connections.
 * UDP and X AIR offer no authentication or request IDs: use a trusted mixer Wi-Fi network.
 */
public final class MixerClient {
    public interface Listener {
        void onStatus(String status, boolean connected);
        void onValue(String path, Object value);
        void onDiscovered(String ip, String name, String model);
        /** MeterProtocol bank order; dB values are owned by this callback. */
        default void onMeters(String bank, float[] db) {}
        /** A bank was disabled, disconnected or stopped producing fresh frames. */
        default void onMetersCleared(String bank) {}
    }

    private static final long TIMEOUT_MS = 4000;
    private static final long FRESH_MS = 3500;
    private static final long DISCOVERY_MS = 2200;
    private static final Set<String> MODELS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("XR18", "MR18", "X18")));
    // true means a float fader; false means an integer boolean switch.
    private final Map<String, Boolean> paths = new LinkedHashMap<>();
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor io;
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicLong discoveryEpoch = new AtomicLong();
    private final AtomicLong meterEpoch = new AtomicLong();
    private volatile boolean metersEnabled;
    private volatile Set<String> requiredPaths = Collections.emptySet();
    private volatile Readiness readiness;
    private final byte[] receiveBuffer = new byte[65507];
    private volatile boolean closed;
    private volatile boolean connected;
    private volatile long connectedEpoch = -1;
    private volatile long lastReply;
    private volatile DatagramSocket activeSocket;
    private volatile DatagramSocket discoverySocket;
    // Fields below are exclusively owned by io.
    private Session session;
    private Discovery discovery;

    public MixerClient(Listener listener) {
        this(null, listener);
    }

    /** Creates a client whose status messages follow the app's selected locale. */
    public MixerClient(Context context, Listener listener) {
        if (listener == null) throw new IllegalArgumentException("Listener is required");
        this.context = context;
        this.listener = listener;
        for (XAirProtocol.Control control : XAirProtocol.CONTROLS) {
            if (control.faderPath != null) paths.put(control.faderPath, true);
            paths.put(control.mutePath, false);
        }
        io = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "xr18-osc");
            thread.setDaemon(true);
            return thread;
        });
        io.setRemoveOnCancelPolicy(true);
        io.scheduleWithFixedDelay(this::tick, 0, 20, TimeUnit.MILLISECONDS);
    }

    /** Selects this explicit mixer; discovery never selects or connects automatically. */
    public void connect(String host, Network network) {
        if (closed) return;
        long token = epoch.incrementAndGet();
        connected = false;
        readiness = null;
        clearMeters(token);
        closeSocket(activeSocket); // Wake an in-flight receive before the worker changes session.
        execute(() -> startSession(host, network, token));
    }

    /** Read-only /xinfo broadcast. Results are deduplicated by source IP for this search. */
    public void discover(Network network) {
        if (closed) return;
        long token = discoveryEpoch.incrementAndGet();
        closeSocket(discoverySocket);
        execute(() -> startDiscovery(network, token));
    }

    /**
     * Sends only known control values after that path has been synchronized in this session.
     * There is intentionally no automatic resend: the next GET/echo confirms the device value.
     */
    public void set(String path, Object value) {
        final Object wire = validatedValue(path, value);
        if (wire == null || closed || !isReady()) return;
        final long token = epoch.get();
        final Set<String> selection = requiredPaths;
        execute(() -> {
            Session current = session;
            if (!isCurrent(current) || current.token != token || selection != requiredPaths || !isReady() || !current.handshaken) return;
            long now = SystemClock.elapsedRealtime();
            Long fresh = current.fresh.get(path);
            if (fresh == null || now - fresh > FRESH_MS || now - lastReply > TIMEOUT_MS) return;
            try {
                send(current.socket, current.address, path, wire);
                send(current.socket, current.address, path); // Read back, without optimistic UI state.
            } catch (IOException failure) {
                disconnect(current, "connection_lost");
            }
        });
    }

    public boolean isConnected() {
        return !closed && connected && connectedEpoch == epoch.get()
                && SystemClock.elapsedRealtime() - lastReply <= TIMEOUT_MS;
    }

    /** Sets the controls that must have fresh values before a write is allowed. */
    public void setRequiredPaths(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) {
            requiredPaths = Collections.emptySet();
        } else {
            requiredPaths = Collections.unmodifiableSet(new HashSet<>(paths));
        }
        readiness = null;
        execute(() -> computeReady(session));
    }

    /** True only while the connection and all required control values are fresh. */
    public boolean isReady() {
        Readiness current = readiness;
        return isConnected() && current != null && current.selection == requiredPaths
                && current.token == epoch.get() && SystemClock.elapsedRealtime() <= current.until;
    }

    /** Subscribe only while the overlay is expanded; cached meters never cross sessions. */
    public void setMetersEnabled(boolean enabled) {
        if (closed || metersEnabled == enabled) return;
        metersEnabled = enabled;
        long meterToken = meterEpoch.incrementAndGet();
        long token = epoch.get();
        execute(() -> {
            Session current = session;
            if (isCurrent(current)) {
                current.lastMetersRequest = 0;
                current.lastChannelMeters = 0;
                current.lastDcaMeters = 0;
            }
            publishMetersCleared(token, meterToken, MeterProtocol.CHANNEL_BANK);
            publishMetersCleared(token, meterToken, MeterProtocol.DCA_BANK);
        });
    }

    /** Terminal close: create a new client if another session is needed. */
    public void close() {
        closed = true;
        connected = false;
        readiness = null;
        epoch.incrementAndGet();
        meterEpoch.incrementAndGet();
        discoveryEpoch.incrementAndGet();
        closeSocket(activeSocket);
        closeSocket(discoverySocket);
        io.shutdownNow();
    }

    private void startSession(String host, Network network, long token) {
        if (closed || token != epoch.get()) return;
        if (session != null) closeSocket(session.socket);
        session = null;
        status(token, "connecting_mixer", false);
        DatagramSocket socket = null;
        try {
            if (host == null || host.trim().isEmpty()) throw new IOException("Empty host");
            String target = host.trim();
            InetAddress address = network == null ? InetAddress.getByName(target) : network.getByName(target);
            if (address.isAnyLocalAddress() || address.isMulticastAddress()
                    || address.isLoopbackAddress() || "255.255.255.255".equals(address.getHostAddress())) {
                throw new IOException("Unicast mixer address required");
            }
            socket = new DatagramSocket(null);
            socket.bind(new InetSocketAddress(0));
            if (network != null) network.bindSocket(socket);
            socket.connect(address, XAirProtocol.PORT);
            socket.setSoTimeout(1);
            if (closed || token != epoch.get()) { socket.close(); return; }
            Session created = new Session(token, socket, address, target, network);
            session = created;
            activeSocket = socket;
            send(socket, address, "/xinfo");
            created.lastInfo = created.started;
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            closeSocket(socket);
            if (token == epoch.get()) {
                activeSocket = null;
                session = null;
                connected = false;
                status(token, "mixer_failed", false);
                retry(host, network, token);
            }
        }
    }

    private void tick() {
        if (closed) return;
        Session current = session;
        if (isCurrent(current)) {
            try {
                for (int i = 0; i < 128; i++) {
                    DatagramPacket packet = receive(current.socket);
                    if (packet == null) break;
                    // Connected UDP already filters peers; check explicitly as well.
                    if (packet.getPort() != XAirProtocol.PORT || !packet.getAddress().equals(current.address)) continue;
                    MeterProtocol.Frame meter = MeterProtocol.decode(packet.getData(), packet.getLength());
                    if (meter != null) {
                        handleMeter(current, meter);
                        continue;
                    }
                    for (Osc.Message message : Osc.decode(packet.getData(), packet.getLength())) {
                        handle(current, message);
                    }
                }
                if (isCurrent(current)) maintain(current);
            } catch (IOException failure) {
                if (isCurrent(current)) disconnect(current, "connection_lost");
            }
        }
        pollDiscovery();
    }

    private void handle(Session current, Osc.Message message) throws IOException {
        if (!isCurrent(current)) return;
        long now = SystemClock.elapsedRealtime();
        if ("/xinfo".equals(message.address)) {
            String[] identity = identity(message);
            if (identity == null) return;
            if (!current.handshaken) {
                current.handshaken = true;
                current.name = identity[0];
                current.model = identity[1];
                current.lastValid = now;
                status(current.token, "syncing_mixer", false, current.model);
                send(current.socket, current.address, "/xremote");
                current.lastRemote = now;
                queryAll(current);
                current.lastPoll = now;
            }
            return;
        }
        if (!current.handshaken || message.args.size() != 1) return;
        Object value = validatedValue(message.address, message.args.get(0));
        if (value == null) return;
        current.fresh.put(message.address, now);
        current.lastValid = now;
        lastReply = now;
        computeReady(current);
        if (!connected || connectedEpoch != current.token) {
            connectedEpoch = current.token;
            connected = true;
            status(current.token, "connected_mixer", true, current.model, current.name);
        }
        // The UI clears its cache on disconnected statuses. Announce the recovered session
        // before publishing its first value, so a newly synchronized value is never erased.
        value(current.token, message.address, value);
    }

    private void maintain(Session current) throws IOException {
        long now = SystemClock.elapsedRealtime();
        if (now - current.lastValid > TIMEOUT_MS) {
            disconnect(current, current.handshaken ? "mixer_timeout" : "mixer_unsupported");
            return;
        }
        if (!current.handshaken) {
            if (now - current.lastInfo >= 1000) {
                send(current.socket, current.address, "/xinfo");
                current.lastInfo = now;
            }
            return;
        }
        if (metersEnabled) {
            if (current.lastMetersRequest == 0 || now - current.lastMetersRequest >= MeterProtocol.RENEW_MS) {
                sendMeterSubscription(current, MeterProtocol.CHANNEL_BANK);
                sendMeterSubscription(current, MeterProtocol.DCA_BANK);
                current.lastMetersRequest = now;
            }
            long meterToken = meterEpoch.get();
            if (current.lastChannelMeters != 0 && now - current.lastChannelMeters > MeterProtocol.STALE_MS) {
                current.lastChannelMeters = 0;
                publishMetersCleared(current.token, meterToken, MeterProtocol.CHANNEL_BANK);
            }
            if (current.lastDcaMeters != 0 && now - current.lastDcaMeters > MeterProtocol.STALE_MS) {
                current.lastDcaMeters = 0;
                publishMetersCleared(current.token, meterToken, MeterProtocol.DCA_BANK);
            }
        }
        if (now - current.lastRemote >= 4000) {
            send(current.socket, current.address, "/xremote");
            current.lastRemote = now;
        }
        if (now - current.lastPoll >= 1000) {
            queryAll(current);
            current.lastPoll = now;
        }
    }

    private void queryAll(Session current) throws IOException {
        for (String path : paths.keySet()) send(current.socket, current.address, path);
    }

    /** Runs on the I/O worker; Session.fresh is owned by that worker. */
    private void computeReady(Session current) {
        if (!isCurrent(current) || !current.handshaken) {
            readiness = null;
            return;
        }
        Set<String> required = requiredPaths;
        if (required.isEmpty()) {
            readiness = new Readiness(required,current.token,Long.MAX_VALUE);
            return;
        }
        long until = Long.MAX_VALUE;
        long now = SystemClock.elapsedRealtime();
        for (String path : required) {
            Long fresh = current.fresh.get(path);
            if (fresh == null) {
                readiness = null;
                return;
            }
            until = Math.min(until, fresh + FRESH_MS);
        }
        readiness = new Readiness(required,current.token,until < now ? 0 : until);
    }

    private void disconnect(Session current, String reasonKey) {
        if (!isCurrent(current)) return;
        // Advance the command/callback generation even for automatic recovery. Otherwise a
        // pre-timeout SET waiting on io could be replayed after the handshake succeeds again.
        long retryToken = current.token + 1;
        if (!epoch.compareAndSet(current.token, retryToken)) return;
        connected = false;
        readiness = null;
        clearMeters(retryToken);
        current.fresh.clear();
        closeSocket(current.socket);
        activeSocket = null;
        session = null;
        status(retryToken, reasonKey, false, true);
        retry(current.host, current.network, retryToken);
    }

    private void retry(String host, Network network, long token) {
        if (closed || token != epoch.get()) return;
        try {
            io.schedule(() -> {
                if (!closed && token == epoch.get()) startSession(host, network, token);
            }, 1200, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException closing) { /* Closed concurrently. */ }
    }

    private void startDiscovery(Network network, long token) {
        if (closed || token != discoveryEpoch.get()) return;
        if (discovery != null) closeSocket(discovery.socket);
        discovery = null;
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(null);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(0));
            if (network != null) network.bindSocket(socket);
            socket.setSoTimeout(1);
            if (closed || token != discoveryEpoch.get()) { socket.close(); return; }
            discovery = new Discovery(token, socket);
            discoverySocket = socket;
            Set<InetAddress> destinations = new HashSet<>();
            destinations.add(InetAddress.getByName("255.255.255.255"));
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface item = interfaces.nextElement();
                    if (!item.isUp() || item.isLoopback()) continue;
                    for (InterfaceAddress address : item.getInterfaceAddresses()) {
                        if (address.getBroadcast() != null) destinations.add(address.getBroadcast());
                    }
                }
            }
            // A bound Android Network routes packets only through that network. Some directed
            // broadcasts may fail on unrelated interfaces; continue with the other destinations.
            for (InetAddress destination : destinations) {
                try { send(socket, destination, "/xinfo"); } catch (IOException ignored) { }
            }
        } catch (IOException | SecurityException failure) {
            closeSocket(socket);
            if (token == discoveryEpoch.get()) { discovery = null; discoverySocket = null; }
        }
    }

    private void pollDiscovery() {
        Discovery search = discovery;
        if (search == null) return;
        if (search.token != discoveryEpoch.get() || SystemClock.elapsedRealtime() > search.until) {
            closeSocket(search.socket);
            if (discovery == search) { discovery = null; discoverySocket = null; }
            return;
        }
        try {
            for (int i = 0; i < 32; i++) {
                DatagramPacket packet = receive(search.socket);
                if (packet == null) break;
                if (packet.getPort() != XAirProtocol.PORT) continue;
                for (Osc.Message message : Osc.decode(packet.getData(), packet.getLength())) {
                    if (!"/xinfo".equals(message.address)) continue;
                    String[] identity = identity(message);
                    if (identity == null) continue;
                    String ip = packet.getAddress().getHostAddress();
                    if (search.seen.add(ip)) {
                        main.post(() -> {
                            if (!closed && search.token == discoveryEpoch.get()) {
                                listener.onDiscovered(ip, identity[0], identity[1]);
                            }
                        });
                    }
                }
            }
        } catch (IOException failure) {
            closeSocket(search.socket);
            if (discovery == search) { discovery = null; discoverySocket = null; }
        }
    }

    private DatagramPacket receive(DatagramSocket socket) throws IOException {
        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        try { socket.receive(packet); return packet; }
        catch (SocketTimeoutException noPacket) { return null; }
    }

    private static void send(DatagramSocket socket, InetAddress address, String path, Object... args)
            throws IOException {
        byte[] bytes = Osc.encode(path, args);
        socket.send(new DatagramPacket(bytes, bytes.length, address, XAirProtocol.PORT));
    }

    private Object validatedValue(String path, Object value) {
        Boolean fader = paths.get(path);
        if (fader == null) return null;
        if (fader) {
            if (!(value instanceof Float)) return null;
            float number = (Float) value;
            return Float.isNaN(number) || Float.isInfinite(number) || number < 0f || number > 1f ? null : number;
        }
        if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        if (!(value instanceof Integer)) return null;
        int number = (Integer) value;
        return number == 0 || number == 1 ? number : null;
    }

    /** /xinfo is: advertised IP, device name, model, firmware (all strings). */
    private static String[] identity(Osc.Message message) {
        if (message.args.size() < 3 || !(message.args.get(0) instanceof String)
                || !(message.args.get(1) instanceof String) || !(message.args.get(2) instanceof String)) return null;
        String model = ((String) message.args.get(2)).trim().toUpperCase(Locale.ROOT);
        if (!MODELS.contains(model)) return null;
        String name = ((String) message.args.get(1)).trim();
        if (name.isEmpty()) name = model;
        return new String[] { name, model };
    }

    private boolean isCurrent(Session current) {
        return !closed && current != null && current == session && current.token == epoch.get();
    }

    private void status(long token, String key, boolean state, Object... args) {
        final String message = localizedStatus(key, args);
        main.post(() -> { if (!closed && token == epoch.get()) listener.onStatus(message, state); });
    }

    private String localizedStatus(String key, Object... args) {
        boolean reconnect = args.length == 1 && args[0] instanceof Boolean && (Boolean) args[0];
        if (reconnect) {
            return localized(key) + " · " + localized("reconnecting");
        }
        return localized(key, args);
    }

    private String localized(String key, Object... args) {
        return context == null ? key : L.s(context, key, args);
    }

    private void value(long token, String path, Object value) {
        main.post(() -> { if (!closed && token == epoch.get()) listener.onValue(path, value); });
    }

    private void handleMeter(Session current, MeterProtocol.Frame frame) {
        if (!isCurrent(current) || !current.handshaken || !metersEnabled) return;
        long now = SystemClock.elapsedRealtime();
        if (MeterProtocol.CHANNEL_BANK.equals(frame.bank)) current.lastChannelMeters = now;
        else current.lastDcaMeters = now;
        long meterToken = meterEpoch.get();
        main.post(() -> {
            if (!closed && metersEnabled && current.token == epoch.get()
                    && meterToken == meterEpoch.get()
                    && SystemClock.elapsedRealtime() - now <= MeterProtocol.STALE_MS) {
                listener.onMeters(frame.bank, frame.db);
            }
        });
    }

    private void sendMeterSubscription(Session current, String bank) throws IOException {
        byte[] packet = MeterProtocol.subscription(bank);
        current.socket.send(new DatagramPacket(packet, packet.length, current.address, XAirProtocol.PORT));
    }

    private void clearMeters(long token) {
        long meterToken = meterEpoch.incrementAndGet();
        publishMetersCleared(token, meterToken, MeterProtocol.CHANNEL_BANK);
        publishMetersCleared(token, meterToken, MeterProtocol.DCA_BANK);
    }

    private void publishMetersCleared(long token, long meterToken, String bank) {
        main.post(() -> {
            if (!closed && token == epoch.get() && meterToken == meterEpoch.get()) listener.onMetersCleared(bank);
        });
    }

    private void execute(Runnable task) {
        if (closed) return;
        try { io.execute(task); } catch (RejectedExecutionException closing) { /* Closed concurrently. */ }
    }

    private static void closeSocket(DatagramSocket socket) {
        if (socket != null) socket.close();
    }

    private static final class Readiness {
        final Set<String> selection;
        final long token,until;
        Readiness(Set<String> selection,long token,long until){this.selection=selection;this.token=token;this.until=until;}
    }

    private static final class Session {
        final long token;
        final DatagramSocket socket;
        final InetAddress address;
        final String host;
        final Network network;
        final long started = SystemClock.elapsedRealtime();
        final Map<String, Long> fresh = new HashMap<>();
        long lastValid = started;
        long lastInfo;
        long lastRemote;
        long lastPoll;
        long lastMetersRequest;
        long lastChannelMeters;
        long lastDcaMeters;
        boolean handshaken;
        String name;
        String model;
        Session(long token, DatagramSocket socket, InetAddress address, String host, Network network) {
            this.token = token;
            this.socket = socket;
            this.address = address;
            this.host = host;
            this.network = network;
        }
    }

    private static final class Discovery {
        final long token;
        final DatagramSocket socket;
        final long until = SystemClock.elapsedRealtime() + DISCOVERY_MS;
        final Set<String> seen = new HashSet<>();
        Discovery(long token, DatagramSocket socket) { this.token = token; this.socket = socket; }
    }
}
