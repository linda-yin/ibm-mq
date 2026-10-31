package mqsamples.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 封装 config.yml 的配置项，提供类型安全的读取方法与默认值。
 * 底层使用 SnakeYAML 解析得到的 Map 结构。
 */
public class AppConfig {

    private final Map<String, Object> root;

    public AppConfig(Map<String, Object> root) {
        this.root = root == null ? Collections.<String, Object>emptyMap() : root;
    }

    /** 运行模式 */
    public String getMode() {
        return str(root, "mode", "send");
    }

    // ---------------- MQ 连接配置 ----------------

    public String mqConnectionType() {
        return str(map("mq"), "connectionType", "Client");
    }

    public String mqQueueManager() {
        return str(map("mq"), "queueManager", "");
    }

    public String mqHost() {
        return str(map("mq"), "host", "");
    }

    public int mqPort() {
        return integer(map("mq"), "port", 1414);
    }

    public String mqChannel() {
        return str(map("mq"), "channel", "");
    }

    /** 用户名为空时返回 null，表示不认证 */
    public String mqUsername() {
        return nullableStr(map("mq"), "username");
    }

    /** 密码为空时返回 null */
    public String mqPassword() {
        return nullableStr(map("mq"), "password");
    }

    // ---------------- 点对点队列配置 ----------------

    public String queueName() {
        return str(map("queue"), "name", "");
    }

    public int queueMsgNum() {
        return integer(map("queue"), "msgNum", 1);
    }

    public int queueThreadNum() {
        return integer(map("queue"), "threadNum", 1);
    }

    public int queueMsgSize() {
        return integer(map("queue"), "msgSize", 0);
    }

    // ---------------- 请求/应答配置 ----------------

    public String requestQueue() {
        return str(map("request"), "requestQueue", "");
    }

    public String replyQueue() {
        return str(map("request"), "replyQueue", "");
    }

    public int requestMsgNum() {
        return integer(map("request"), "msgNum", 1);
    }

    public int requestThreadNum() {
        return integer(map("request"), "threadNum", 1);
    }

    // ---------------- JMS 发布订阅配置 ----------------

    public String pubsubTopic() {
        return str(map("pubsub"), "topic", "");
    }

    public int pubsubMsgNum() {
        return integer(map("pubsub"), "msgNum", 1);
    }

    public String pubsubSubscriberId() {
        return nullableStr(map("pubsub"), "subscriberId");
    }

    // ---------------- MQTT 配置 ----------------

    public String mqttBroker() {
        return str(map("mqtt"), "broker", "");
    }

    public int mqttMsgNum() {
        return integer(map("mqtt"), "msgNum", 1);
    }

    public int mqttInterval() {
        return integer(map("mqtt"), "interval", 1);
    }

    public String mqttSubscriberId() {
        return str(map("mqtt"), "subscriberId", "subscriber");
    }

    public List<String> mqttTopics() {
        return strList(map("mqtt"), "topics");
    }

    // ---------------- 内部工具方法 ----------------

    private Map<String, Object> map(String key) {
        Object v = root.get(key);
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        return Collections.emptyMap();
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        if (v == null) {
            return def;
        }
        return String.valueOf(v).trim();
    }

    private String nullableStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private int integer(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private List<String> strList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        List<String> result = new ArrayList<String>();
        if (v instanceof List) {
            for (Object o : (List) v) {
                if (o != null) {
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty()) {
                        result.add(s);
                    }
                }
            }
        }
        return result;
    }
}
