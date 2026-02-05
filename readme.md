# HTTP代理的Android APP VPN

通过设置代理地址和端口、密码等参数

可以选择使用代理的应用程序

## 配置文件

可将 `httpproxy.json` 放入以下任一目录，从文件加载代理配置和默认代理应用包名：

- `Download/httpproxy.json`
- `Android/data/com.httpproxy.vpn/files/httpproxy.json`

JSON 格式示例：

```json
{
  "proxy": {
    "type": "http",
    "host": "127.0.0.1",
    "port": 1080,
    "username": "user",
    "password": "pass"
  },
  "defaultPackages": ["com.android.chrome", "com.tencent.mm", "com.smile.gifmaker","com.ss.android.ugc.aweme","com.xingin.xhs","mark.via"]
}
```

- `proxy`：代理配置，存在时覆盖应用内配置
- `defaultPackages`：默认走代理的包名列表（需已安装），会与内置浏览器和用户应用合并
