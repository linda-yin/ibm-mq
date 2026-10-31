package mqsamples.pubsub;

import mqsamples.config.AppConfig;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;

import javax.jms.*;

/**
 * 订阅消息：使用 config.yml 中的 subscriberId 作为订阅者 ID（持久订阅名），
 * 先订阅指定主题，然后阻塞接收并打印消息内容。
 */
class Subscriber extends Thread {
    private JMSContext jmsContext;
    private String subscriberId;
    private String topic;

    Subscriber(JMSContext jmsContext, String subscriberId, String topic){
        this.jmsContext = jmsContext;
        this.subscriberId = subscriberId;
        this.topic = topic;
    }

    public void run(){
        try {
            Topic destination = jmsContext.createTopic(topic);
            // subscriberId 作为订阅者 ID（持久订阅名），先订阅主题
            JMSConsumer consumer = jmsContext.createDurableConsumer(destination, subscriberId);
            System.out.println("订阅者 " + subscriberId + " 已订阅主题: " + topic);

            int msgNum = 0;
            while (true){
                // 阻塞接收消息，返回 null 时结束
                Message message = consumer.receive();
                if(message != null){
                    msgNum++;
                    System.out.println("[" + subscriberId + " 收到第 " + msgNum + " 条消息] " + message.getBody(String.class));
                } else {
                    break;
                }
            }
            consumer.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}

public class SubMsg {
    private MQConnectionFactory factory;
    private JMSContext jmsContext;
    private String connType;
    private String qmgrName;
    private String hostName;
    private int port;
    private String channelName;
    private String username=null;
    private String password;
    private int retryTimeout;
    private String subscriberId;

    private boolean initFromConfig(AppConfig config){
        connType = config.mqConnectionType().toUpperCase();
        qmgrName = config.mqQueueManager();
        hostName = config.mqHost();
        port = config.mqPort();
        channelName = config.mqChannel();
        username = config.mqUsername();
        password = config.mqPassword();
        subscriberId = config.pubsubSubscriberId();

        if (qmgrName == null || qmgrName.isEmpty()) {
            System.err.println("配置错误：请设置 mq.queueManager");
            return false;
        }
        if (subscriberId == null || subscriberId.isEmpty()) {
            System.err.println("配置错误：请设置 pubsub.subscriberId");
            return false;
        }
        if ("CLIENT".equals(connType)
                && (hostName == null || hostName.isEmpty() || channelName == null || channelName.isEmpty())) {
            System.err.println("配置错误：Client 模式需要设置 mq.host 和 mq.channel");
            return false;
        }
        return true;
    }

    //创建应用与队列管理器之间的连接
    private void createConnectionByClient(){
        try{
            factory = new MQConnectionFactory();
            factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
//           if client want to reconncect when there is a failure happens on network
//            factory.setClientReconnectOptions(WMQConstants.WMQ_CLIENT_RECONNECT);
//            factory.setClientReconnectTimeout(retryTimeout);
//            factory.setConnectionNameList("host1(port1),host2(port2)");
            factory.setQueueManager(qmgrName);
            factory.setHostName(hostName);
            factory.setPort(port);
            factory.setChannel(channelName);
            if(subscriberId != null && !subscriberId.isEmpty()){
                factory.setClientID(subscriberId);
            }
            if(username==null){
                jmsContext = factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            }else{
                jmsContext = factory.createContext(username,password,JMSContext.AUTO_ACKNOWLEDGE);
            }
        }catch (JMSException e){
            e.printStackTrace();
        }
    }
    private  void createConnectionByLocal(){
        try{
            factory = new MQConnectionFactory();
            factory.setTransportType(WMQConstants.WMQ_CM_BINDINGS);
            factory.setQueueManager(qmgrName);
            if(subscriberId != null && !subscriberId.isEmpty()){
                factory.setClientID(subscriberId);
            }
            if(username==null){
                jmsContext = factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            }else{
                jmsContext = factory.createContext(username,password,JMSContext.AUTO_ACKNOWLEDGE);
            }
        }catch (JMSException e){
            e.printStackTrace();
        }
    }
    private  boolean createConnection(){
        try {
            if ("CLIENT".equals(connType)){
                createConnectionByClient();
            }else{
                createConnectionByLocal();
            }
            return true;
        } catch (Exception e) {
            System.err.println("连接 MQ 失败: " + mqsamples.MqErrorUtil.rootCauseMessage(e));
            return false;
        }
    }

    public void execute(AppConfig config) {
        if(!initFromConfig(config)){
            System.err.println("Parameter error!!!");
            return;
        }
        String topic = config.pubsubTopic();
        if (topic == null || topic.isEmpty()) {
            System.err.println("配置错误：请设置 pubsub.topic");
            return;
        }
        if (!createConnection()) {
            return;
        }

        JMSContext myJmsContext = jmsContext.createContext(JMSContext.AUTO_ACKNOWLEDGE);
        Subscriber subscriber = new Subscriber(myJmsContext, subscriberId, topic);
        subscriber.start();
        try {
            subscriber.join();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            jmsContext.close();
        }
    }
}
