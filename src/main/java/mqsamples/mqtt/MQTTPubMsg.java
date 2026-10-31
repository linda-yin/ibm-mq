package mqsamples.mqtt;

import mqsamples.config.AppConfig;

import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
//此线程专门用于发布消息
class PubMessage extends Thread{
    private String topic       ;
    private String content      = "Message from MqttPublishSample";
    private String broker       ;
    private int qos             ;
    private String clientId     ;
    private int interval;
    private int msgNum = 1;
    private MqttClient sampleClient;
    PubMessage(String broker,String topic, int qos, String clientId,int msgNum,int interval){
        this.broker = broker;
        this.topic = topic;
        this.qos = qos;
        this.clientId = clientId;
        this.msgNum = msgNum;
        this.interval = interval;
    }
    public void  run(){
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            // brokerUrl为主机名，clientId即连接MQTT的客户端ID，一般以唯一标识符表示，MemoryPersistence设置clientid的保存形式，默认为以内存保存
            sampleClient = new MqttClient(broker, clientId, persistence);
            // MQTT的连接设置
            MqttConnectOptions connOpts = new MqttConnectOptions();
            // 设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，这里设置为true表示每次连接到服务器都以新的身份连接
            connOpts.setCleanSession(true);
            System.out.println("Begin to connecting to broker: "+broker);
            // 设置连接的用户名
            // options.setUserName(userName);
            // // 设置连接的密码
            // options.setPassword(passWord.toCharArray());
            // 设置超时时间 单位为秒
            connOpts.setConnectionTimeout(30);
            // 设置会话心跳时间 单位为秒 服务器会每隔1.5*20秒的时间向客户端发送个消息 判断客户端是否在线，但这个方法并没有重连的机制
            connOpts.setKeepAliveInterval(20);
            //建立连接
            sampleClient.connect(connOpts);
            System.out.println(">>>>>>>>>Connected！");
            String content1;
            for(int i=1;i<=msgNum;i++){
                content1 = "Message, sequenceNo:"+i+": "+content;
                System.out.println(content1);
                MqttMessage message = new MqttMessage(content1.getBytes());
                message.setQos(qos);
                sampleClient.publish(topic, message);
                try {
                    Thread.sleep(interval*1000);
                }catch (Exception e){
                    msgNum = i;
                    break;
                }
            }
            System.out.println(msgNum+" messages were published");
        } catch(MqttException me) {
            System.out.println("reason: "+me.getReasonCode());
            System.out.println("msg: "+me.getMessage());
            System.out.println("loc: "+me.getLocalizedMessage());
            System.out.println("cause: "+me.getCause());
            System.out.println("exception: "+me);
            me.printStackTrace();
        }finally {
            try {
                sampleClient.disconnect();
            }catch (Exception e){
                e.printStackTrace();
            }
            System.out.println("Disconnected");
        }
    }

}
public class MQTTPubMsg {
    String brokerUrl       = "tcp://172.16.173.94:1884";
    int msgNum = 1;
    int interval = 1;
    public void execute(AppConfig config){
        brokerUrl = config.mqttBroker();
        msgNum = config.mqttMsgNum();
        interval = config.mqttInterval();
        List<String> topics = config.mqttTopics();
        if (topics.isEmpty()) {
            System.err.println("配置错误：请设置 mqtt.topics");
            return;
        }
        PubMessage[] pubMessages = new PubMessage[topics.size()];
        for (int i = 0; i < topics.size(); i++) {
            pubMessages[i] = new PubMessage(brokerUrl, topics.get(i), 1, "publisher" + (i + 1), msgNum, interval);
        }
        //create a thread to publish message
        for (int i = 0; i < topics.size(); i++) {
            pubMessages[i].start();   //start  thread
        }

        try {
            for (int i = 0; i < topics.size(); i++) {
                pubMessages[i].join();   //start  thread
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
