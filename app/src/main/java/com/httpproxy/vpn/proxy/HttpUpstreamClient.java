package com.httpproxy.vpn.proxy;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 代理上游客户端：CONNECT + Proxy-Authorization Basic 认证。
 */
public class HttpUpstreamClient implements UpstreamProxyClient {

    private final String proxyHost;
    private final int proxyPort;
    private final String username;
    private final String password;
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int SO_TIMEOUT = 0;

    public HttpUpstreamClient(String proxyHost, int proxyPort, String username, String password) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    @Override
    public Socket connect(String targetHost, int targetPort) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(proxyHost, proxyPort), CONNECT_TIMEOUT);
        socket.setSoTimeout(SO_TIMEOUT);

        StringBuilder req = new StringBuilder();
        req.append("CONNECT ").append(targetHost).append(":").append(targetPort).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(targetHost).append(":").append(targetPort).append("\r\n");
        if (!username.isEmpty() || !password.isEmpty()) {
            String cred = username + ":" + password;
            String auth = Base64.encodeToString(cred.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            req.append("Proxy-Authorization: Basic ").append(auth).append("\r\n");
        }
        req.append("Connection: keep-alive\r\n\r\n");

        OutputStream out = socket.getOutputStream();
        out.write(req.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String statusLine = reader.readLine();
        if (statusLine == null) {
            socket.close();
            throw new IOException("HTTP proxy: no response");
        }
        int code = parseStatusCode(statusLine);
        if (code < 200 || code >= 300) {
            socket.close();
            throw new IOException("HTTP proxy CONNECT failed: " + statusLine);
        }
        // 消费剩余 headers 直到空行
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // skip header lines
        }
        return socket;
    }

    private static int parseStatusCode(String statusLine) {
        try {
            int firstSpace = statusLine.indexOf(' ');
            if (firstSpace < 0) return 0;
            int secondSpace = statusLine.indexOf(' ', firstSpace + 1);
            if (secondSpace < 0) secondSpace = statusLine.length();
            return Integer.parseInt(statusLine.substring(firstSpace + 1, secondSpace).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
