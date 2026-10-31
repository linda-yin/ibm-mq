package mqsamples;

import mqsamples.config.AppConfig;
import mqsamples.config.ConfigLoader;
import mqsamples.mqtt.MQTTPubMsg;
import mqsamples.mqtt.MQTTSubMsg;
import mqsamples.ptop.MsgListener;
import mqsamples.ptop.RecvMsg;
import mqsamples.ptop.RequestClient;
import mqsamples.ptop.ResponseServer;
import mqsamples.ptop.SendMsg;
import mqsamples.pubsub.PubMsg;
import mqsamples.pubsub.SubMsg;

/**
 * 统一入口。通过 config.yml 中的 mode 字段决定运行哪种消息模式。
 */
public class Main {

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = ConfigLoader.load(args);
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.err.println("用法：java -jar target/mqdemo-1.0.jar config.yml");
            System.exit(1);
            return;
        }

        String mode = config.getMode() == null ? "" : config.getMode().trim().toLowerCase();
        System.out.println("运行模式: " + mode);

        switch (mode) {
            case "send":
                new SendMsg().execute(config);
                break;
            case "receive":
                new RecvMsg().execute(config);
                break;
            case "listen":
                new MsgListener().execute(config);
                break;
            case "request":
                new RequestClient().execute(config);
                break;
            case "response":
                new ResponseServer().execute(config);
                break;
            case "publish":
                new PubMsg().execute(config);
                break;
            case "subscribe":
                new SubMsg().execute(config);
                break;
            case "mqtt-publish":
                new MQTTPubMsg().execute(config);
                break;
            case "mqtt-subscribe":
                new MQTTSubMsg().execute(config);
                break;
            default:
                System.err.println("未知运行模式: " + mode);
                System.err.println("可选值: send / receive / listen / request / response / "
                        + "publish / subscribe / mqtt-publish / mqtt-subscribe");
                System.exit(1);
        }
    }
}
