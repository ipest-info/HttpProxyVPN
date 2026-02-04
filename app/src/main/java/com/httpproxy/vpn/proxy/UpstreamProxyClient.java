package com.httpproxy.vpn.proxy;

import java.io.IOException;
import java.net.Socket;

/**
 * 上游代理客户端抽象：根据配置连接 HTTP 或 SOCKS5 代理，建立到目标 host:port 的隧道。
 */
public interface UpstreamProxyClient {

    /**
     * 通过上游代理连接到目标 host:port，返回已建立好的 Socket（可用于双向转发）。
     *
     * @param targetHost 目标主机
     * @param targetPort 目标端口
     * @return 与目标建立好的连接（经代理转发）
     * @throws IOException 连接或代理协议失败时抛出
     */
    Socket connect(String targetHost, int targetPort) throws IOException;
}
