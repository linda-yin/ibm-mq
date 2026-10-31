# IBM MQ 启用客户端认证配置说明

**适用于 IBM MQ 9.4 版本**

本文档介绍如何为 IBM MQ 启用客户端「用户名 / 密码」认证，让客户端连接时必须提供正确的用户名和密码。

---

## 一、认证原理

IBM MQ 的用户名密码认证通过 **CONNAUTH（连接认证）** 实现，最常用的类型是 **IDPWOS**：

- `IDPWOS`：使用**操作系统账号**校验客户端提供的用户名 / 密码
- `IDPWLDAP`：使用 **LDAP 目录**校验（生产环境常用，需额外配置 LDAP 服务器）

应用侧已经支持：`config.yml` 中的 `mq.username` / `mq.password` 会通过
`factory.createContext(username, password, ...)` 传给 MQ 进行校验。

---

## 二、前提条件

- 队列管理器已启动（如 `QM1`）
- 具有队列管理器管理员权限（`mqm` 组或管理员账号）
- 能在 MQ 服务器上创建操作系统用户
- 已确定要使用的连接用户（如 `mqapp`）及密码

---

## 三、配置步骤

### 步骤 1：在 MQ 服务器上创建操作系统用户并设置密码

```bash
# Linux / Unix
sudo useradd mqapp
sudo passwd mqapp            # 设置密码，例如 Mqapp@123

# Windows（若 MQ 装在 Windows 上）
net user mqapp Mqapp@123 /add
```

> 注意：Linux 上该账号密码不能为空，账号不能处于锁定 / 过期状态，否则认证会被拒绝。

### 步骤 2：给该用户授权

**方式 A（测试环境，最简单）：加入 mqm 组，拥有全部权限**

```bash
# Linux / Unix
sudo usermod -aG mqm yinzm

# Windows
net localgroup mqm mqapp /add
```

**方式 B（生产推荐）：用 setmqaut 精确授权**

```bash
# 队列管理器权限
setmqaut -m QM1 -t qmgr -p yinzm +connect +inq

# 队列权限（点对点）
setmqaut -m QM1 -n LQ1 -t queue -p yinzm +put +get +inq +browse

# 主题权限（发布订阅）
setmqaut -m QM1 -n '/financial/deposit' -t topic -p yinzm +pub +sub
setmqaut -m QM1 -n '/manufacture/#' -t topic -p yinzm +pub +sub
```

### 步骤 3：启用连接认证（CONNAUTH + IDPWOS）

进入 `runmqsc QM1` 管理界面，执行：

```mqsc
-- 定义认证信息对象：IDPWOS = 用操作系统账号校验用户名密码
DEFINE AUTHINFO('USE.OS.AUTHINFO') AUTHTYPE(IDPWOS) ADOPTCTX(YES) REPLACE

-- 让队列管理器使用该认证
ALTER QMGR CONNAUTH('USE.OS.AUTHINFO')

-- 确保 CHLAUTH 是启用状态（若之前禁用过，需改回）
ALTER QMGR CHLAUTH(ENABLED)

-- 刷新安全配置（必须执行，否则不生效）
REFRESH SECURITY TYPE(CONNAUTH)
```

> `ADOPTCTX(YES)` 含义：认证通过后，MQ 使用「认证通过的那个用户」（即 mqapp）的身份做后续授权判断，所以步骤 2 的授权必须正确。
>
> 若设为 `ADOPTCTX(NO)`，则认证通过后仍使用通道的 `MCAUSER` 身份做授权，与传入的用户名无关。

### 步骤 4：配置通道 CHLAUTH（可选，生产推荐）

```mqsc
-- 把客户端提供的用户 mqapp 映射为 mqapp 身份
SET CHLAUTH('SVR.CONN') TYPE(USERMAP) CLNTUSER('yinzm') USERSRC(MAP) MCAUSER('yinzm') ACTION(REPLACE)

-- 阻止匿名 / 空白用户连接
SET CHLAUTH('SVR.CONN') TYPE(BLOCKUSER) USERLIST('nobody') ACTION(REPLACE)

REFRESH SECURITY TYPE(CONNAUTH)
```

### 步骤 5：应用侧配置 config.yml

```yaml
mq:
  connectionType: Client
  queueManager: QM1
  host: 10.10.92.33
  port: 1419
  channel: SVR.CONN
  username: mqapp          # 步骤 1 创建的用户名
  password: Mqapp@123      # 步骤 1 设置的密码
```

### 步骤 6：验证

```bash
# 客户端运行（能成功连接并收发消息即为认证成功）
java -jar target/mqdemo-1.0.jar config.yml
```

服务端确认认证已启用：

```mqsc
runmqsc QM1
DISPLAY QMGR CONNAUTH        # 应显示 CONNAUTH(USE.OS.AUTHINFO)
DISPLAY AUTHINFO(*)          # 查看认证信息对象
DISPLAY CHLAUTH(*)           # 查看通道认证规则
DISPLAY CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) ALL
```

---

## 四、常见问题排查

### Q1：连接报 2035（MQRC_NOT_AUTHORIZED / AMQ4036）

**原因：** 用户认证已通过，但没有对应资源的访问权限。

**解决：**
```bash
# 检查用户权限
dspmqaut -m QM1 -t qmgr -p mqapp
dspmqaut -m QM1 -n LQ1 -t queue -p mqapp

# 补充授权（加入 mqm 组 或 setmqaut）
setmqaut -m QM1 -t qmgr -p mqapp +connect +inq
setmqaut -m QM1 -n LQ1 -t queue -p mqapp +put +get +inq +browse
REFRESH SECURITY TYPE(CONNAUTH)
```

### Q2：连接报「用户名或密码无效 / 用户不存在」

**原因：** IDPWOS 校验失败——操作系统里没有该用户，或密码错误，或账号被锁定/过期。

**解决：**
- 确认操作系统用户存在且密码正确
- 确认账号未锁定、未过期、密码非空（Linux）
- 查看服务器错误日志确认具体原因：`dspmqerr -m QM1` 或查看 `AMQERR01.LOG`

### Q3：连接报「未提供用户名和密码」（AMQ5542 之类）

**原因：** 客户端没有提供用户名密码，但队列管理器要求认证。

**解决：**
- 确认 `config.yml` 里 `mq.username` / `mq.password` 已填写且不为空
- 确认应用代码走的是 `createContext(username, password, ...)` 分支

### Q4：改了配置但不生效

**原因：** 没有刷新安全配置，或 CHLAUTH 仍处于禁用状态。

**解决：**
```mqsc
ALTER QMGR CHLAUTH(ENABLED)
REFRESH SECURITY TYPE(CONNAUTH)
```

### Q5：之前按《MQ_禁用认证配置.md》做过禁用，如何恢复认证？

```mqsc
runmqsc QM1

-- 恢复 CHLAUTH
ALTER QMGR CHLAUTH(ENABLED)

-- 清理可能放行匿名连接的规则
SET CHLAUTH(SVR.CONN) TYPE(ADDRESSMAP) ADDRESS('*') ACTION(REMOVE)
SET CHLAUTH(SVR.CONN) TYPE(BLOCKUSER) USERLIST('*') ACTION(REMOVE)

-- 恢复通道 MCAUSER（如需要）
ALTER CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) MCAUSER('')

-- 启用 CONNAUTH
DEFINE AUTHINFO('USE.OS.AUTHINFO') AUTHTYPE(IDPWOS) ADOPTCTX(YES) REPLACE
ALTER QMGR CONNAUTH('USE.OS.AUTHINFO')

REFRESH SECURITY TYPE(CONNAUTH)
```

---

## 五、相关 MQSC 命令参考

| 命令 | 作用 |
|------|------|
| `DEFINE AUTHINFO` | 定义认证信息对象 |
| `ALTER QMGR CONNAUTH` | 设置队列管理器使用的连接认证 |
| `DISPLAY QMGR CONNAUTH` | 查看连接认证配置 |
| `DISPLAY AUTHINFO(*)` | 查看所有认证信息对象 |
| `ALTER QMGR CHLAUTH` | 启用 / 禁用通道认证 |
| `SET CHLAUTH` / `DISPLAY CHLAUTH` | 配置 / 查看通道认证规则 |
| `setmqaut` / `dspmqaut` | 授权 / 查看用户权限 |
| `REFRESH SECURITY TYPE(CONNAUTH)` | 刷新安全配置 |

---

## 六、注意事项

1. **安全建议**：生产环境应始终启用认证，并通过 `setmqaut` 按最小权限原则授权，不要直接加入 `mqm` 组。

2. **必须刷新**：修改 CONNAUTH / CHLAUTH / 权限后必须执行 `REFRESH SECURITY TYPE(CONNAUTH)`。

3. **IDPWOS 依赖操作系统账号**：删除、锁定或修改 OS 账号会影响 MQ 连接认证。

4. **生产环境建议使用 LDAP**：账号多、需要集中管理时，将 `AUTHTYPE(IDPWOS)` 改为 `AUTHTYPE(IDPWLDAP)` 并配置 LDAP 服务器。

5. **客户端密码传输**：IDPWOS 认证在未启用 TLS 时，用户名密码默认以**明文**在网络传输（MQ 会对密码做简单混淆，但非强加密）。生产环境建议同时启用 TLS（SSLCIPH）。

6. **错误日志**：认证类问题优先查看 MQ 服务器错误日志 `AMQERR01.LOG`（`dspmqerr -m QM1`），日志中会给出具体的 AMQ 错误码和原因。
