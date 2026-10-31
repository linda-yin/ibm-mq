package mqsamples;

import java.util.ArrayList;
import java.util.List;

/**
 * MQ 相关异常工具类。
 */
public final class MqErrorUtil {

    private MqErrorUtil() {
    }

    /**
     * 提取异常链中最有意义的消息。
     * IBM MQ 通常把真实原因（如 AMQ9204 连接被拒绝）包裹在多层 cause 中，
     * 这里优先返回包含 "AMQ" 的错误码消息，否则返回整条异常链的非空消息。
     */
    public static String rootCauseMessage(Throwable t) {
        if (t == null) {
            return "未知错误";
        }
        List<String> messages = new ArrayList<String>();
        Throwable cause = t;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && !msg.trim().isEmpty()) {
                msg = msg.trim();
                if (msg.contains("AMQ")) {
                    return msg;
                }
                if (!messages.contains(msg)) {
                    messages.add(msg);
                }
            }
            cause = (cause.getCause() != cause) ? cause.getCause() : null;
        }
        if (!messages.isEmpty()) {
            return String.join("；", messages);
        }
        return t.toString();
    }
}
