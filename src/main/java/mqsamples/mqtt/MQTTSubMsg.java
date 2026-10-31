package mqsamples.mqtt;

import mqsamples.config.AppConfig;

import java.util.List;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Created by user on 2019/5/15.
 */
class SubMessage extends Thread{
    private String brokerUrl ;
    private String clientId  ;
    private String  topic ;
    private int qos ;
    // private String userName = "admin";
    // private String passWord = "password";

    SubMessage(String brokerUrl,String clientId,String topic,int qos){
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.topic = topic;
        this.qos = qos;
    }
    public void run()  {
        try {
            // brokerUrl为主机名，clientId即连接MQTT的客户端ID，一般以唯一标识符表示，MemoryPersistence设置clientid的保存形式，默认为以内存保存
           MqttClient mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            // MQTT的连接设置
            MqttConnectOptions options = new MqttConnectOptions();
            // 设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，这里设置为true表示每次连接到服务器都以新的身份连接
            options.setCleanSession(true);
            // 设置连接的用户名
            // options.setUserName(userName);
            // // 设置连接的密码
            // options.setPassword(passWord.toCharArray());
            // 设置超时时间 单位为秒
            options.setConnectionTimeout(30);
            // 设置会话心跳时间 单位为秒 服务器会每隔30秒的时间向客户端发送个消息 判断客户端是否在线，但这个方法并没有重连的机制
            options.setKeepAliveInterval(20);
            // 设置回调
            mqttClient.setCallback(new PushCallback());

            // setWill方法，如果项目中需要知道客户端是否掉线可以调用该方法。设置最终端口的通知消息
            // options.setWill(topic, "close".getBytes(), 2, true);
            //建立连接
            mqttClient.connect(options);
//        String[] topicFilter = new String[2];
//        topicFilter[0] = "/sports/football";
//        topicFilter[1] = "/sports/basketball";
//        Qos = new int[2];
//        Qos[0] = 1;
//        Qos[1] = 2;
//        mqttClient.subscribe(topicFilter,Qos);
            mqttClient.subscribe(topic, qos);
        }catch (MqttException e){
            e.printStackTrace();
        }
    }


}
class PushCallback implements MqttCallback {

    public void connectionLost(Throwable cause) {
        // 连接丢失后，一般在这里面进行重连
        System.out.println("连接断开，可以做重连");
    }

    public void messageArrived(String mqttTopic, MqttMessage mqttMessage) throws Exception {
        // subscribe后得到的消息会执行到这里面
        System.out.println("接收消息主题:"+mqttTopic );
        System.out.println("接收消息Qos:" + mqttMessage.getQos());
        System.out.println("接收消息内容:" + new String(mqttMessage.getPayload()));
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
        System.out.println("deliveryComplete---------" + token.isComplete());
    }
}
public class MQTTSubMsg {
    private String brokerUrl       = "tcp://172.16.170.12:1884";
    private int qos = 1;
    private int[] Qos = null;
    private String[] topicFilter  = null;

    public void  execute(AppConfig config){
        brokerUrl = config.mqttBroker();
        String subscriberId = config.mqttSubscriberId();
        List<String> topics = config.mqttTopics();
        if (topics.isEmpty()) {
            System.err.println("配置错误：请设置 mqtt.topics");
            return;
        }
        SubMessage[] subMessages = new SubMessage[topics.size()];
        for (int i = 0; i < topics.size(); i++) {
            // 多个主题时，为每个订阅线程分配唯一的 clientId，避免 MQTT 客户端 ID 冲突
            String clientId = (topics.size() > 1) ? subscriberId + "-" + (i + 1) : subscriberId;
            subMessages[i] = new SubMessage(brokerUrl, clientId, topics.get(i), 1);
        }
        //create a thread to subscribe message
        for (int i = 0; i < topics.size(); i++) {
            subMessages[i].start();   //start  thread
        }

        try {
            for (int i = 0; i < topics.size(); i++) {
                subMessages[i].join();   //start  thread
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
