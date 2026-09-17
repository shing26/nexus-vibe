> 现场：本机 Docker 29.5.3 上的常开栈 `nexus-vibe`，`alert-bridge` 已配好真实飞书 webhook。
> 结论：`open.feishu.cn` 有 20 个 A 记录，解析顺序里排第一的那个（`202.168.180.27`）**能完成 TCP
> 握手、然后丢掉 TLS 握手**。`urllib.request.urlopen` 只认 TCP 那一步是否成功，所以它认定了这个地址
> 就不再换，于是**只要 DNS 把这个地址排在第一，每一条告警都投不出去**，错误是
> `_ssl.c:993: The handshake operation timed out`。桥现在逐个地址重试。

# 告警桥的「死边缘」事故

## 一、现象

2026-09-17 跑 OPS-1 的负向检查（故意用错密钥，期望桥把飞书的拒绝**报出来**而不是吞掉）时，它没有
报出 `19021`，而是每次都报网络层失败，五次重试全一样：

```
RuntimeError: forward to https://open.feishu.cn/open-apis/bot/v2/hook/*** failed:
              <urlopen error _ssl.c:993: The handshake operation timed out>
```

同一时刻宿主机上 `Test-NetConnection open.feishu.cn -Port 443` → `TcpTestSucceeded=True`，
`Invoke-WebRequest https://open.feishu.cn/` → `HTTP 404`（`/` 没有页面，属正常）。也就是说这一跳
**从宿主机是通的、从容器不是**，看起来像容器网络问题。

它不是。逐个地址量：

```bash
docker exec nexus-alert-bridge python -c "
import socket, ssl, time
for ip in ['202.168.180.27','202.168.180.23','203.132.39.50']:
    t0=time.time()
    s=socket.create_connection((ip,443), timeout=8)
    w=ssl.create_default_context().wrap_socket(s, server_hostname='open.feishu.cn')
    print(ip, 'TLS', round(time.time()-t0,3), 's', w.version())
"
```

| 地址 | TCP | TLS 握手 |
|------|-----|----------|
| `202.168.180.27` | 成功（0.001 s） | **超时** |
| `202.168.180.23` | 成功 | 0.036 s，TLSv1.3 |
| `203.132.39.50` | 成功 | 0.036 s，TLSv1.3 |

差别不在容器，在**边缘节点**：那一个地址把 TLS 握手黑洞掉了，邻居 36 毫秒答完。

## 二、根因

`socket.create_connection()` 会遍历 `getaddrinfo` 的全部结果——但只在 **TCP** 连不上时才换下一个。
`202.168.180.27` 的 TCP 是**成功**的，于是连接被交给了 TLS 层，而 TLS 那一层没有这套回退：

```bash
docker exec nexus-alert-bridge python -c "
import socket
infos = socket.getaddrinfo('open.feishu.cn', 443, type=socket.SOCK_STREAM)
print([a[0] for _,_,_,_,a in infos][:3])
"
# ['202.168.180.27', '202.168.162.165', '202.168.180.28']   ← urlopen 只试第一个
```

同一个容器里，把**旧路径**和**新路径**并排跑一次（2026-09-17，同一分钟）：

| 路径 | 结果 | 耗时 |
|------|------|------|
| `urllib.request.urlopen(...)`（修之前的那一行） | FAILED：`_ssl.c:993: The handshake operation timed out` | 5.03 s |
| `alert_bridge.send(...)`（修之后） | 送达：`{"StatusCode":0,"StatusMessage":"success","code":0,...}` | 2.05 s |

复现命令：

```bash
docker exec nexus-alert-bridge python -c "
import sys, time, json, urllib.request
sys.path.insert(0, '/bridge')
import alert_bridge as b
body = b.build_body({'state':'alerting','title':'before/after','alerts':[]}, int(time.time()), b.WEBHOOK_SECRET)
print('first address:', b._resolved_addresses('open.feishu.cn', 443)[0])
try:
    req = urllib.request.Request(b.WEBHOOK_URL, data=json.dumps(body).encode(),
                                 headers={'Content-Type':'application/json'}, method='POST')
    urllib.request.urlopen(req, timeout=5.0).read(); print('old path: delivered')
except Exception as e:
    print('old path FAILED:', e)
print('new path:', b.send(body)[:60])
"
```

新路径那 2.05 s 里，前面 1.5 s 花在那个死地址上（`CONNECT_TIMEOUT_SECONDS`），然后换到邻居，几十毫秒完成。

**这条对告警桥是致命的而不是烦人的**：`open.feishu.cn` 的 A 记录顺序是轮转的，所以这个故障不是「某天全挂」，
而是「某些时段全挂」——观测上像一条偶尔丢的告警，正是这个文件开头写要消灭的那种失败。

## 三、修法与回归

- `send()` 不再交给 `urlopen`：自己解析地址、逐个尝试。
- `_PinnedHTTPSConnection` 把连接钉在某一个地址上，但**主机名照旧**送给 `server_hostname`，
  所以 SNI 与证书校验对象没变（有测试钉住这一点：钉地址不许顺手关掉校验）。
- `timeout` 仍是**整次调用**的预算；`CONNECT_TIMEOUT_SECONDS = 1.5` 是单个地址的上限，默认 5 s
  的调用因此能试三个地址。握手放在 `connect()` 里，所以它被这个上限管住；而**已经握手成功、只是答得慢**
  的收件人拿到剩下的全部时间——那种情况重试等于把同一条告警投两次。
- 新增 `AddressFallbackTest`（5 条）：第一个地址不可达时换下一个、全部不可达时报出真实错误而不是占位符、
  同一地址只试一次、`wrap_socket` 确实在 `connect()` 里（否则死地址仍会吃掉整次调用，而测试照样绿）、
  钉地址没有关掉证书校验。

验证：`cd docker/observability/alert-bridge && python -m unittest test_alert_bridge` → **28 条通过**。

## 四、负向检查的结论（OPS-1 要的那一条）

修完之后，故意用错密钥的那次调用**报出了飞书的真实拒绝**：

```bash
docker exec nexus-alert-bridge python -c "
import sys; sys.path.insert(0, '/bridge')
import alert_bridge as b
print(b.deliver({'state':'alerting','title':'negative check','alerts':[]}, secret='deliberately-wrong-secret'))
"
# RuntimeError: Feishu took the connection but refused the alert
#   (code=19021 msg=sign match fail or timestamp is not within one hour from current time)
```

`19021` 的文案自带歧义（「密钥错**或**时钟偏超过一小时」），所以这里需要排除时钟那一半：同一分钟内、
用**正确**密钥发同一条载荷，应答是 `{"StatusCode":0,...,"code":0,"msg":"success"}` 并且真的进了群
（2026-09-17 02:30:31，容器时钟）。同一个时钟既能成功签名，那错密钥那次拿到的 `19021` 就只能是签名不符。

## 五、这次没有证明的

- **没有一条单测复现了 TLS 握手黑洞本身。** 要把「TCP 成功、TLS 永不返回」做进单测就需要一个真 TLS
  服务端和证书，成本超过这条修复的价值。因此单测钉的是新机制（遍历、上限、校验没被关掉），
  **判别性的证据是上面那两张实机对照表**，不是测试。
- 「20 个地址里死一个」是**某一时刻某一个地址**的观测。修复能扛几个死地址是算出来的（5 s 预算 ÷ 1.5 s
  = 三次尝试），没有造出多个死地址来验。
- `202.168.180.27` 从别的网络（别的 ISP、CI runner）是否也黑洞，没有测；换网络后这个地址可能恢复正常，
  也可能换成另一个地址出问题。
- 告警桥镜像在本机是**手工重打**的：Docker Hub 不可达（`registry-1.docker.io` 取 `python:3.12-alpine`
  的 manifest 超时），所以本机用 `docker create` + `docker cp` + `docker commit` 把改后的文件塞进
  `nexus-alert-bridge:local` 再重建容器。**提交的 Dockerfile 由 CI 的 `alert-bridge` job 构建**，
  这跟仓库里 app / web 两个镜像的既有口径一致。
- 桥的日志时间戳是 UTC，而栈里其余服务设了 `TZ=Asia/Shanghai`。签名用的是绝对时间所以不受影响，
  但事后对时间线要在两个时区之间换算。这条只是记下来，本轮没有改。
