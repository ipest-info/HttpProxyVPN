package com.httpproxy.vpn.vpn;

import com.httpproxy.vpn.proxy.UpstreamProxyClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地 HTTP 代理：监听 127.0.0.1，对 CONNECT 与普通 HTTP 请求通过上游代理转发。
 */
public class LocalProxyServer {

    private static final String CONNECT_OK = "HTTP/1.1 200 Connection established\r\n\r\n";
    private static final int LOCAL_PORT = 18080;
    private static final int SO_TIMEOUT = 0;

    private final UpstreamProxyClient upstream;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "LocalProxy-" + r.hashCode());
        t.setDaemon(true);
        return t;
    });

    public LocalProxyServer(UpstreamProxyClient upstream) {
        this(upstream, LOCAL_PORT);
    }

    public LocalProxyServer(UpstreamProxyClient upstream, int port) {
        this.upstream = upstream;
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new java.net.InetSocketAddress("127.0.0.1", port));
        executor.execute(this::acceptLoop);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }
        executor.shutdown();
    }

    private void acceptLoop() {
        while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(SO_TIMEOUT);
                executor.execute(() -> handleClient(client));
            } catch (IOException e) {
                if (running.get()) {
                    // log
                }
                break;
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            InputStream clientIn = client.getInputStream();
            OutputStream clientOut = client.getOutputStream();
            String firstLine = readLine(clientIn);
            if (firstLine == null || firstLine.isEmpty()) {
                client.close();
                return;
            }
            if (firstLine.toUpperCase().startsWith("CONNECT ")) {
                handleConnect(client, firstLine, clientIn, clientOut);
            } else {
                handleHttpRequest(client, firstLine, clientIn, clientOut);
            }
        } catch (Exception e) {
            try { client.close(); } catch (IOException ignored) { }
        }
    }

    private void handleConnect(Socket client, String firstLine, InputStream clientIn, OutputStream clientOut) throws IOException {
        String[] parts = firstLine.split("\\s+");
        if (parts.length < 2) {
            client.close();
            return;
        }
        String hostPort = parts[1];
        int colon = hostPort.lastIndexOf(':');
        String host = colon > 0 ? hostPort.substring(0, colon) : hostPort;
        int port = 443;
        if (colon > 0 && colon < hostPort.length() - 1) {
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException ignored) { }
        }
        consumeHeaders(clientIn);
        Socket upstreamSocket;
        try {
            upstreamSocket = upstream.connect(host, port);
        } catch (IOException e) {
            clientOut.write(("HTTP/1.1 502 Bad Gateway\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            clientOut.flush();
            client.close();
            return;
        }
        clientOut.write(CONNECT_OK.getBytes(StandardCharsets.UTF_8));
        clientOut.flush();
        relay(client, clientIn, clientOut, upstreamSocket);
    }

    private void handleHttpRequest(Socket client, String firstLine, InputStream clientIn, OutputStream clientOut) throws IOException {
        String host = null;
        int port = 80;
        StringBuilder headerBlock = new StringBuilder(firstLine).append("\r\n");
        String line;
        while ((line = readLine(clientIn)) != null && !line.isEmpty()) {
            if (line.toLowerCase().startsWith("host:")) {
                String hostVal = line.substring(5).trim();
                int colon = hostVal.indexOf(':');
                if (colon > 0) {
                    host = hostVal.substring(0, colon).trim();
                    try {
                        port = Integer.parseInt(hostVal.substring(colon + 1).trim());
                    } catch (NumberFormatException ignored) { }
                } else {
                    host = hostVal;
                }
            }
            headerBlock.append(line).append("\r\n");
        }
        headerBlock.append("\r\n");
        if (host == null) {
            host = parseHostFromRequestLine(firstLine);
            if (host == null) {
                client.close();
                return;
            }
        }
        Socket upstreamSocket;
        try {
            upstreamSocket = upstream.connect(host, port);
        } catch (IOException e) {
            clientOut.write(("HTTP/1.1 502 Bad Gateway\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            clientOut.flush();
            client.close();
            return;
        }
        String relativeRequestLine = toRelativeRequestLine(firstLine);
        if (relativeRequestLine != null) {
            headerBlock.replace(0, firstLine.length(), relativeRequestLine);
        }
        OutputStream upOut = upstreamSocket.getOutputStream();
        InputStream upIn = upstreamSocket.getInputStream();
        upOut.write(headerBlock.toString().getBytes(StandardCharsets.UTF_8));
        upOut.flush();
        relay(client, clientIn, clientOut, upstreamSocket);
    }

    private String parseHostFromRequestLine(String firstLine) {
        int start = firstLine.indexOf("http://");
        if (start < 0) return null;
        start += 7;
        int end = firstLine.indexOf('/', start);
        if (end < 0) end = firstLine.length();
        String hostPort = firstLine.substring(start, end).trim();
        int colon = hostPort.indexOf(':');
        return colon > 0 ? hostPort.substring(0, colon) : hostPort;
    }

    /** 将 "METHOD http://host/path HTTP/1.1" 转为 "METHOD /path HTTP/1.1"。 */
    private static String toRelativeRequestLine(String firstLine) {
        int scheme = firstLine.indexOf("http://");
        if (scheme < 0) return null;
        int pathStart = firstLine.indexOf('/', scheme + 7);
        if (pathStart < 0) return null;
        int methodEnd = firstLine.indexOf(' ');
        if (methodEnd < 0) return null;
        String method = firstLine.substring(0, methodEnd);
        int verStart = firstLine.lastIndexOf(' ');
        if (verStart < pathStart) return null;
        String version = firstLine.substring(verStart + 1);
        String path = firstLine.substring(pathStart, verStart).trim();
        if (path.isEmpty()) path = "/";
        return method + " " + path + " " + version;
    }

    private void consumeHeaders(InputStream in) throws IOException {
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) { }
    }

    private void relay(Socket client, InputStream clientIn, OutputStream clientOut, Socket upstreamSocket) {
        InputStream upIn;
        OutputStream upOut;
        try {
            upIn = upstreamSocket.getInputStream();
            upOut = upstreamSocket.getOutputStream();
        } catch (IOException e) {
            try { client.close(); upstreamSocket.close(); } catch (IOException ignored) { }
            return;
        }
        executor.execute(() -> copy(clientIn, upOut, client, upstreamSocket));
        copy(upIn, clientOut, upstreamSocket, client);
    }

    private void copy(InputStream from, OutputStream to, Socket closeA, Socket closeB) {
        byte[] buf = new byte[8192];
        try {
            int n;
            while ((n = from.read(buf)) != -1) {
                to.write(buf, 0, n);
                to.flush();
            }
        } catch (IOException ignored) { }
        finally {
            try { closeA.close(); } catch (IOException ignored) { }
            try { closeB.close(); } catch (IOException ignored) { }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                in.read();
                break;
            }
            if (c == '\n') break;
            sb.append((char) c);
        }
        return sb.toString();
    }
}
