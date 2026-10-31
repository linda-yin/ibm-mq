# MQ Demo 使用说明

## 项目简介

这是一个基于 IBM MQ 的消息队列演示项目，支持点对点（P2P）、发布订阅（Pub/Sub）和 MQTT 三种消息模式。项目使用 Maven 构建，通过 `java -jar` 方式运行，所有运行参数统一在 `config.yml` 配置文件中设置。

## 环境要求

- JDK 1.8 或更高版本
- Maven 3.x
- IBM MQ 客户端库（已包含在 `lib` 目录下）

## 构建项目

### 1. 准备依赖库

确保 `lib` 目录下包含以下 jar 文件：
- `jms.jar` - JMS API
- `com.ibm.mq.allclient.jar` - IBM MQ 客户端库
- `mqtt-client-0.4.0.jar` - MQTT 客户端
- `mqtt-utility-0.4.0.jar` - MQTT 工具类

### 2. 编译和打包

```bash
# 编译项目
mvn clean compile

# 打包项目（包含所有依赖，生成可执行 jar）
mvn clean package
```

打包完成后，在 `target` 目录下会生成可执行 jar 包（均已配置 `Main-Class`，可直接用 `java -jar` 运行）：

- `mqdemo-1.0.jar` - shade 插件打包的可执行 jar（包含所有依赖）
- `mqdemo-1.0-jar-with-dependencies.jar` - assembly 插件打包的可执行 jar（包含所有依赖）

两个 jar 均可用 `java -jar` 直接运行，任选其一即可。

## 配置文件（config.yml）

应用通过 `config.yml` 配置文件设置运行参数，`config.yml` 为**必须参数**：

```bash
java -jar target/mqdemo-1.0.jar config.yml
```

也可以使用 `-Dconfig` 系统属性指定配置文件路径：

```bash
java -Dconfig=/path/to/config.yml -jar target/mqdemo-1.0.jar
```

未指定配置文件或文件不存在时，程序会报错并退出。

### 配置项说明

```yaml
# 运行模式（必填），可选值见下文「运行模式」
mode: send

# MQ 连接配置（Client / Local 通用）
mq:
  connectionType: Client        # Client（客户端连接）或 Local（本地绑定连接）
  queueManager: QM1             # 队列管理器名称
  host: 172.16.170.12           # MQ 服务器地址（仅 Client 模式）
  port: 40001                   # MQ 服务器端口（仅 Client 模式）
  channel: SVR.CONN             # 通道名称（仅 Client 模式）
  username:                     # 用户名（可选，留空表示不认证）
  password:                     # 密码（可选）

# 点对点队列配置（mode 为 send / receive / listen 时使用）
queue:
  name: LQ1                     # 队列名称
  msgNum: 10                    # 消息数量（send 使用）
  threadNum: 2                  # 线程数量
  msgSize: 0                    # 消息大小（字节），0 表示默认 JSON 消息格式

# 请求/应答配置（mode 为 request / response 时使用）
request:
  requestQueue: QL01            # 请求队列
  replyQueue: QL02              # 应答队列
  msgNum: 10                    # 消息数量（仅 request 使用）
  threadNum: 2                  # 线程数量（request / response 使用）

# JMS 发布订阅配置（mode 为 publish / subscribe 时使用）
pubsub:
  topic: /financial/deposit     # 主题（publish / subscribe 使用，主题名需包含 financial 或 manufacture）
  msgNum: 10                    # 发布的消息数量（仅 publish 使用）
  subscriberId: subscriber-1    # 订阅者 ID（仅 subscribe 使用）

# MQTT 配置（mode 为 mqtt-publish / mqtt-subscribe 时使用）
mqtt:
  broker: tcp://172.16.173.94:1884   # MQTT broker 地址
  msgNum: 10                    # 每个主题发布的消息数量（仅 mqtt-publish 使用）
  interval: 1                   # 消息发布间隔，单位秒（仅 mqtt-publish 使用）
  subscriberId: subscriber-1    # 订阅者 ID（仅 mqtt-subscribe 使用）
  topics:                       # 主题列表（发布/订阅共用）
    - /light/hall
    - /light/restroom
```

## 运行模式

通过修改 `config.yml` 中的 `mode` 字段切换运行模式。

| mode | 说明 | 使用的配置段 |
|------|------|-------------|
| `send` | 点对点：发送消息 | `mq` + `queue` |
| `receive` | 点对点：同步接收消息 | `mq` + `queue` |
| `listen` | 点对点：异步监听接收消息 | `mq` + `queue` |
| `request` | 请求/应答：请求客户端 | `mq` + `request` |
| `response` | 请求/应答：响应服务端 | `mq` + `request` |
| `publish` | JMS 发布订阅：发布消息 | `mq` + `pubsub` |
| `subscribe` | JMS 发布订阅：订阅消息 | `mq` + `pubsub` |
| `mqtt-publish` | MQTT：发布消息 | `mqtt` |
| `mqtt-subscribe` | MQTT：订阅消息 | `mqtt` |

### 点对点发送消息（send）

```bash
# 修改 config.yml：mode: send，并按需配置 queue.name / msgNum / threadNum / msgSize
java -jar target/mqdemo-1.0.jar config.yml
```

**消息大小说明：**
- `msgSize = 0` 或不设置：使用默认消息格式（生成员工信息 JSON）
- `msgSize > 0`：生成指定大小的消息，内容由字母、数字和特殊字符组成

### 点对点同步接收消息（receive）

```bash
# 修改 config.yml：mode: receive
java -jar target/mqdemo-1.0.jar config.yml
```

`receive` 模式在 10 秒内接收不到消息时会自动退出，并在退出时打印接收总数与耗时。

### 点对点异步监听接收消息（listen）

```bash
# 修改 config.yml：mode: listen
java -jar target/mqdemo-1.0.jar config.yml
```

`listen` 模式会持续监听队列，按任意键后回车退出。

### 请求/应答（request / response）

```bash
# 先启动服务端：mode: response
java -jar target/mqdemo-1.0.jar config.yml

# 再启动客户端：mode: request
java -jar target/mqdemo-1.0.jar config.yml
```

### 发布订阅模式（JMS Pub/Sub）

发布和订阅均针对单个主题，主题通过 `pubsub.topic` 配置；订阅时，订阅者 ID 通过 `pubsub.subscriberId` 配置。

发布的消息内容会根据主题名自动生成：
- 主题包含 `financial`：生成金融交易消息（字段参考 `msgcontent/financial.json`）
- 主题包含 `manufacture`：生成制造/进出口消息（字段参考 `msgcontent/manufacture.json`）
- 主题不含 `financial` 或 `manufacture`：提示无效主题并退出

**发布消息（publish）：**

```bash
# 修改 config.yml：mode: publish，并按需配置 pubsub.topic / pubsub.msgNum
java -jar target/mqdemo-1.0.jar config.yml
```

输出示例（`pubsub.topic` 为 `/financial/deposit`）：

```
运行模式: publish
10 messages have been sent to topic(/financial/deposit)
```

发布的单条消息内容示例（金融交易）：

```json
{"messageId":"MSG20260928101530001","messageType":"BANK_DEPOSIT_WITHDRAWAL","timestamp":"2026-09-28 10:15:30.123","transactionType":"DEPOSIT","amount":"10000.00","currency":"CNY","accountNo":"6222020200000000001","accountName":"张三","accountType":"PERSONAL","bankCode":"ICBC","status":"SUCCESS"}
```

**订阅消息（subscribe）：**

```bash
# 修改 config.yml：mode: subscribe，并按需配置 pubsub.topic / pubsub.subscriberId
java -jar target/mqdemo-1.0.jar config.yml
```

输出示例：

```
运行模式: subscribe
Topic: /financial/deposit
Received a published message: {"messageId":"MSG20260928101530001",...}
...
```

**使用建议：** 先启动 `subscribe` 订阅消息，再启动 `publish` 发布消息，避免消息丢失。

### MQTT 发布订阅模式

MQTT 模式使用 `tcp://host:port` 形式的 broker 地址，主题通过 `mqtt.topics` 配置，订阅者 ID 通过 `mqtt.subscriberId` 配置。

**MQTT 发布（mqtt-publish）：**

```bash
# 修改 config.yml：mode: mqtt-publish，并按需配置 mqtt.broker / mqtt.msgNum / mqtt.interval / mqtt.topics
java -jar target/mqdemo-1.0.jar config.yml
```

**MQTT 订阅（mqtt-subscribe）：**

```bash
# 修改 config.yml：mode: mqtt-subscribe，并按需配置 mqtt.broker / mqtt.subscriberId / mqtt.topics
java -jar target/mqdemo-1.0.jar config.yml
```

## 功能特性

1. **统一配置**：
   - 所有运行参数通过 `config.yml` 配置，无需记忆复杂的命令行参数
   - 通过 `mode` 字段一键切换运行模式

2. **消息计数器**：
   - 发送端：每个线程完成时显示该线程发送的消息数，所有线程完成后显示总发送数
   - 接收端：每条消息显示当前接收计数，程序退出时显示总接收数

3. **多线程支持**：
   - 支持多线程并发发送和接收消息
   - 线程安全的计数器实现

4. **多种消息类型**：
   - TextMessage（文本消息）
   - BytesMessage（字节消息）
   - ObjectMessage（对象消息）

5. **连接方式**：
   - Client 模式：通过网络连接到远程 MQ 服务器
   - Local 模式：通过本地绑定连接到本地队列管理器

6. **发布订阅**：
   - 发布/订阅主题通过配置文件指定（单个主题）
   - 订阅者 ID 通过配置文件指定
   - 根据主题自动生成对应类型的 JSON 消息（financial / manufacture）

## 注意事项

1. 确保 MQ 服务器已启动并配置正确
2. 确保队列、通道和主题已创建并配置正确
3. 如果使用认证，在 `config.yml` 中设置用户名和密码
4. `listen` 模式会持续运行直到按任意键退出
5. `receive` 模式会在 10 秒无消息后自动退出

## 故障排查

### 常见错误

1. **缺少配置文件参数 / 配置文件不存在**

   **错误信息：** `缺少配置文件参数。用法：java -jar target/mqdemo-1.0.jar config.yml` 或 `配置文件不存在: /path/to/config.yml`

   **解决方案：** 在启动命令中显式指定 `config.yml` 路径，例如 `java -jar target/mqdemo-1.0.jar config.yml`，并确保该文件存在。

2. **配置错误提示（如：请设置 mq.queueManager）**

   **原因：** 当前 `mode` 所需的配置项缺失。

   **解决方案：** 根据提示补齐 `config.yml` 中对应的配置项。

3. **连接失败**：
   - 检查 `mq.host` 和 `mq.port` 是否正确
   - 检查网络连接是否正常
   - 检查 `mq.channel` 通道名称是否正确

4. **认证失败**：
   - 检查 `mq.username` 和 `mq.password` 是否正确
   - 检查用户是否有访问队列的权限

5. **队列不存在**：
   - 检查 `queue.name` 队列名称是否正确
   - 确认队列已在 MQ 中创建

6. **编码问题**：
   - 项目已配置 UTF-8 编码，如仍有问题请检查系统编码设置

## 项目结构

```
MQDemo/
├── lib/                          # 依赖库目录
│   ├── jms.jar
│   ├── com.ibm.mq.allclient.jar
│   ├── mqtt-client-0.4.0.jar
│   └── mqtt-utility-0.4.0.jar
├── src/main/java/mqsamples/
│   ├── Main.java                 # 统一入口，根据 mode 分发
│   ├── config/                   # 配置加载
│   │   ├── AppConfig.java        # 配置项封装
│   │   └── ConfigLoader.java     # config.yml 加载
│   ├── ptop/                     # 点对点消息模式
│   │   ├── SendMsg.java          # 发送消息
│   │   ├── MsgListener.java      # 异步接收消息
│   │   ├── RecvMsg.java          # 同步接收消息
│   │   ├── RequestClient.java    # 请求客户端
│   │   ├── ResponseServer.java   # 响应服务器
│   │   └── ApplicationData.java  # 应用数据对象
│   ├── pubsub/                   # 发布订阅模式
│   │   ├── PubMsg.java           # 发布消息
│   │   └── SubMsg.java           # 订阅消息
│   └── mqtt/                     # MQTT 模式
│       ├── MQTTPubMsg.java       # MQTT 发布
│       └── MQTTSubMsg.java       # MQTT 订阅
├── config.yml                    # 运行时配置文件（必须参数）
├── pom.xml                       # Maven 配置文件
└── README.md                     # 本文件
```

## 版本信息

- 项目版本：1.0
- Java 版本：1.8
- 编码：UTF-8
