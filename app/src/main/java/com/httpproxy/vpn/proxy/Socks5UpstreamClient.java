package com.httpproxy.vpn.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * SOCKS5 上游代理客户端：握手 + 用户名/密码认证（RFC 1929）+ CONNECT。
 */
public class Socks5UpstreamClient implements UpstreamProxyClient {

    private static final int VERSION = 0x05;
    private static final int METHOD_NO_AUTH = 0x00;
    private static final int METHOD_USERNAME_PASSWORD = 0x02;
    private static final int CMD_CONNECT = 0x01;
    private static final int ATYP_DOMAIN = 0x03;
    private static final int AUTH_VERSION = 0x01;

    private final String proxyHost;
    private final int proxyPort;
    private final String username;
    private final String password;
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int SO_TIMEOUT = 0;

    public Socks5UpstreamClient(String proxyHost, int proxyPort, String username, String password) {
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

        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        // Method negotiation: support no-auth and username/password
        boolean useAuth = !username.isEmpty() || !password.isEmpty();
        out.write(VERSION);
        out.write(1);
        out.write(useAuth ? METHOD_USERNAME_PASSWORD : METHOD_NO_AUTH);
        out.flush();

        byte[] methodResp = readExactly(in, 2);
        if (methodResp[0] != VERSION) {
            socket.close();
            throw new IOException("SOCKS5: invalid version in method response");
        }
        int chosen = methodResp[1] & 0xff;
        if (chosen == METHOD_USERNAME_PASSWORD && useAuth) {
            byte[] user = username.getBytes(StandardCharsets.UTF_8);
            byte[] pass = password.getBytes(StandardCharsets.UTF_8);
            if (user.length > 255 || pass.length > 255) {
                socket.close();
                throw new IOException("SOCKS5: username/password too long");
            }
            out.write(AUTH_VERSION);
            out.write(user.length);
            out.write(user);
            out.write(pass.length);
            out.write(pass);
            out.flush();
            byte[] authResp = readExactly(in, 2);
            if (authResp[0] != AUTH_VERSION || authResp[1] != 0x00) {
                socket.close();
                throw new IOException("SOCKS5: authentication failed");
            }
        } else if (chosen == 0xff) {
            socket.close();
            throw new IOException("SOCKS5: no acceptable method");
        }

        // CONNECT request: domain name
        byte[] hostBytes = targetHost.getBytes(StandardCharsets.UTF_8);
        if (hostBytes.length > 255) {
            socket.close();
            throw new IOException("SOCKS5: host too long");
        }
        out.write(VERSION);
        out.write(CMD_CONNECT);
        out.write(0x00);
        out.write(ATYP_DOMAIN);
        out.write(hostBytes.length);
        out.write(hostBytes);
        out.write((targetPort >> 8) & 0xff);
        out.write(targetPort & 0xff);
        out.flush();

        byte[] connectResp = readExactly(in, 4);
        if (connectResp[0] != VERSION || connectResp[1] != 0x00) {
            socket.close();
            throw new IOException("SOCKS5: CONNECT failed reply");
        }
        int atyp = connectResp[3] & 0xff;
        if (atyp == 0x01) {
            readExactly(in, 6); // IPv4 + port
        } else if (atyp == 0x03) {
            int len = in.read() & 0xff;
            readExactly(in, len + 2);
        } else if (atyp == 0x04) {
            readExactly(in, 18);
        } else {
            socket.close();
            throw new IOException("SOCKS5: unknown address type");
        }
        return socket;
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r <= 0) throw new IOException("SOCKS5: unexpected EOF");
            off += r;
        }
        return buf;
    }
}
