# IBM MQ 禁用客户端认证配置说明

**适用于 IBM MQ 9.4 版本**

## 方法一：设置通道的 MCAUSER + CHLAUTH 规则（MQ 9.x 必需）

在 MQ 9.4 中，必须同时配置 MCAUSER 和 CHLAUTH 规则才能允许连接。

在 MQ 服务器上执行以下 MQSC 命令：

```bash
# 连接到队列管理器
runmqsc QM1

# 1. 设置通道的 MCAUSER 为 mqm（推荐）
ALTER CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) MCAUSER('mqm')

# 2. 查看当前的 CHLAUTH 规则
DISPLAY CHLAUTH(*)

# 3. 删除可能阻止连接的规则
SET CHLAUTH(SVR.CONN) TYPE(BLOCKUSER) USERLIST('*') ACTION(REMOVE)
SET CHLAUTH(SVR.CONN) TYPE(ADDRESSMAP) ACTION(REMOVE)

# 4. 添加允许规则 - 允许所有地址连接（MQ 9.x 必需）
SET CHLAUTH(SVR.CONN) TYPE(ADDRESSMAP) ADDRESS('*') MCAUSER('mqm') ACTION(ADD)

# 5. 刷新安全配置
REFRESH SECURITY TYPE(CONNAUTH)

# 6. 查看通道配置确认
DISPLAY CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) ALL
```

**说明：**
- `MCAUSER('mqm')` - 所有通过此通道连接的用户都将以 mqm 用户身份运行
- `MCAUSER('')` - 允许任何用户连接，使用连接用户的身份
- 需要队列管理器管理员权限

## 方法二：完全禁用 CHLAUTH（仅测试环境，MQ 9.4）

在 MQ 9.4 中，如果方法一不行，可以临时禁用 CHLAUTH：

```bash
runmqsc QM1

# 查看当前 CHLAUTH 状态
DISPLAY QMGR CHLAUTH

# 禁用 CHLAUTH（仅测试环境）
ALTER QMGR CHLAUTH(DISABLED)

# 刷新安全配置
REFRESH SECURITY TYPE(CONNAUTH)

# 确认配置
DISPLAY QMGR CHLAUTH
```

## 方法三：配置队列管理器的安全设置

```bash
runmqsc QM1

# 查看当前安全设置
DISPLAY QMGR SECURITY

# 如果启用了安全，可以临时禁用（不推荐）
ALTER QMGR CHLAUTH(DISABLED)
```

**注意：** 这会禁用所有通道的认证，存在安全风险。

## 方法四：使用 SSL/TLS 但禁用用户认证

如果使用 SSL 通道，可以配置为仅验证证书，不验证用户：

```bash
runmqsc QM1

# 设置通道的 SSL 认证为可选
ALTER CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) SSLCERTI(OPTIONAL)
```

## 完整配置示例（MQ 9.4 测试环境）

### 方案 A：配置 CHLAUTH 规则（推荐）

```bash
# 1. 连接到队列管理器
runmqsc QM1

# 2. 设置通道 MCAUSER
ALTER CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) MCAUSER('mqm')

# 3. 查看现有 CHLAUTH 规则
DISPLAY CHLAUTH(*)

# 4. 删除可能阻止的规则
SET CHLAUTH(SVR.CONN) TYPE(BLOCKUSER) USERLIST('*') ACTION(REMOVE)
SET CHLAUTH(SVR.CONN) TYPE(ADDRESSMAP) ACTION(REMOVE)

# 5. 添加允许规则
SET CHLAUTH(SVR.CONN) TYPE(ADDRESSMAP) ADDRESS('*') MCAUSER('mqm') ACTION(ADD)

# 6. 刷新安全配置
REFRESH SECURITY TYPE(CONNAUTH)

# 7. 确认配置
DISPLAY CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) ALL
DISPLAY CHLAUTH(SVR.CONN)

# 8. 退出
END
```

### 方案 B：完全禁用 CHLAUTH（如果方案 A 不行）

```bash
runmqsc QM1

# 禁用 CHLAUTH
ALTER QMGR CHLAUTH(DISABLED)

# 设置通道 MCAUSER
ALTER CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) MCAUSER('mqm')

# 刷新安全配置
REFRESH SECURITY TYPE(CONNAUTH)

# 确认
DISPLAY QMGR CHLAUTH
DISPLAY CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) ALL

END
```

## 验证配置

配置完成后，可以使用以下命令验证：

```bash
# 在客户端测试连接（不需要用户名密码）
java -cp target/mqdemo-1.0-jar-with-dependencies.jar mqsamples.ptop.SendMsg \
  Client QM1 10.10.92.33 1419 SVR.CONN LQ1 10 2
```

## 注意事项

⚠️ **安全警告：**
1. 禁用认证会降低系统安全性，仅建议在测试/开发环境使用
2. 生产环境应始终启用认证
3. 如果必须禁用，建议：
   - 使用防火墙限制访问
   - 仅对特定 IP 地址开放
   - 使用 VPN 或专用网络
   - 定期审计访问日志

## MQ 9.4 特定注意事项

1. **CHLAUTH 默认启用**：MQ 9.x 默认启用 CHLAUTH，必须配置允许规则
2. **CONNAUTH 可能也需要配置**：检查 `DISPLAY QMGR CONNAUTH`
3. **规则优先级**：BLOCKUSER 规则优先级高于 ADDRESSMAP
4. **必须刷新**：修改后必须执行 `REFRESH SECURITY TYPE(CONNAUTH)`

## 常见问题

### Q: 修改后仍然需要认证？（MQ 9.4）

A: 按以下步骤排查：

1. **确认 CHLAUTH 状态**：
   ```bash
   DISPLAY QMGR CHLAUTH
   # 如果显示 CHLAUTH(ENABLED)，需要添加允许规则或禁用
   ```

2. **检查所有 CHLAUTH 规则**：
   ```bash
   DISPLAY CHLAUTH(*)
   # 查看是否有 BLOCKUSER 规则阻止连接
   ```

3. **确认通道配置**：
   ```bash
   DISPLAY CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) ALL
   # 确认 MCAUSER 已设置
   ```

4. **刷新安全配置**（重要）：
   ```bash
   REFRESH SECURITY TYPE(CONNAUTH)
   ```

5. **检查 CONNAUTH**：
   ```bash
   DISPLAY QMGR CONNAUTH
   # 如果显示 CONNAUTH(ENABLED)，可能需要：
   ALTER QMGR CONNAUTH('')
   REFRESH SECURITY TYPE(CONNAUTH)
   ```

6. **查看错误日志**：
   ```bash
   dspmqerr -m QM1 -l
   # 或查看 AMQERR01.LOG 文件
   ```

### Q: 如何查看当前认证配置？
A: 
```bash
runmqsc QM1
DISPLAY CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) ALL
DISPLAY CHLAUTH(*)
DISPLAY QMGR SECURITY
```

### Q: 如何恢复认证？
A:
```bash
runmqsc QM1
ALTER CHANNEL(SVR.CONN) CHLTYPE(SVRCONN) MCAUSER('')
SET CHLAUTH(SVR.CONN) TYPE(ADDRESSMAP) ADDRESS('*') ACTION(REMOVE)
```

## 相关 MQSC 命令参考

- `ALTER CHANNEL` - 修改通道属性
- `DISPLAY CHANNEL` - 查看通道配置
- `SET CHLAUTH` - 设置通道认证规则
- `DISPLAY CHLAUTH` - 查看认证规则
- `REFRESH SECURITY` - 刷新安全配置
- `DISPLAY QMGR` - 查看队列管理器配置

7.5禁止客户端认证
ALTER QMGR CHLAUTH(DISABLED)   # 禁用整个队列管理器的通道认证记录功能
ALTER CHL(SVR.CONN) CHLTYPE(SVRCONN) MCAUSER('mqm')