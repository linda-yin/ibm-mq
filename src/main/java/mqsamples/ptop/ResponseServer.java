package mqsamples.ptop;

import mqsamples.config.AppConfig;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;

import javax.jms.*;

class Server  extends Thread {
    private JMSContext jmsContext;
    private String requestQueue;
    private String replyQueue;
    private int threadNo;
    Server(int threadNo, JMSContext  jmsContext,String requestQueue, String replyQueue){
        this.jmsContext=jmsContext;
        this.requestQueue = requestQueue;
        this.replyQueue = replyQueue;
        this.threadNo = threadNo;
    }

    private Message produceMessage( String collId) throws JMSException {
        Message message= jmsContext.createTextMessage("I am the response message "+ ",collationID="+collId+",threadNo:"+threadNo);
        try {
            message.setJMSCorrelationID(collId);
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return  message;
    }
    private void   processMessage(Message message) throws JMSException {
        System.out.println("Received a request message:"+ message.getBody(String.class));
    }
    public void run(){
        Destination destination1 = jmsContext.createQueue(requestQueue);
        Destination destination2 = jmsContext.createQueue(replyQueue);
        JMSProducer producer = jmsContext.createProducer();
        Message message;
        JMSConsumer consumer = jmsContext.createConsumer(destination1);  //无条件接收请求消息
        int msgNum=0;
        while(true){
            try {
                message = consumer.receive();   // 阻塞接收请求消息.
                if(message!=null){
                    processMessage(message);
                    producer.send(destination2,produceMessage(message.getJMSCorrelationID()));
                }
                else break;
            }catch (JMSException e){
                e.printStackTrace();
            }
            msgNum++;
        }
        consumer.close();
    }
}

public class ResponseServer {
    private MQConnectionFactory factory;
    private JMSContext jmsContext;
    private String connType;
    private String qmgrName;
    private String hostName;
    private int port;
    private int threadNum = 1;
    private String channelName;
    private String requestQueue;
    private String replyQueue;
    private String username = null;
    private String password;
    private int retryTimeout;

    private boolean initFromConfig(AppConfig config) {
        connType = config.mqConnectionType().toUpperCase();
        qmgrName = config.mqQueueManager();
        hostName = config.mqHost();
        port = config.mqPort();
        channelName = config.mqChannel();
        requestQueue = config.requestQueue();
        replyQueue = config.replyQueue();
        threadNum = config.requestThreadNum();
        username = config.mqUsername();
        password = config.mqPassword();

        if (qmgrName == null || qmgrName.isEmpty()) {
            System.err.println("配置错误：请设置 mq.queueManager");
            return false;
        }
        if (requestQueue == null || requestQueue.isEmpty() || replyQueue == null || replyQueue.isEmpty()) {
            System.err.println("配置错误：请设置 request.requestQueue 和 request.replyQueue");
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
    private void createConnectionByClient() {
        try {
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
            if (username == null) {
                jmsContext = factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            } else {
                jmsContext = factory.createContext(username, password, JMSContext.AUTO_ACKNOWLEDGE);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private void createConnectionByLocal() {
        try {
            factory = new MQConnectionFactory();
            factory.setTransportType(WMQConstants.WMQ_CM_BINDINGS);
            factory.setQueueManager(qmgrName);
            if (username == null) {
                jmsContext = factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            } else {
                jmsContext = factory.createContext(username, password, JMSContext.AUTO_ACKNOWLEDGE);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private boolean createConnection() {
        try {
            if ("CLIENT".equals(connType)) {
                createConnectionByClient();
            } else {
                createConnectionByLocal();
            }
            return true;
        } catch (Exception e) {
            System.err.println("连接 MQ 失败: " + mqsamples.MqErrorUtil.rootCauseMessage(e));
            return false;
        }
    }

    public void execute(AppConfig config) {
        if (!initFromConfig(config)) {
            System.err.println("Parameter error!!");
            return;
        }
        if (!createConnection()) {
            return;
        }
        JMSContext[] myJmsContexts = new JMSContext[threadNum];
        Server[] servers = new Server[threadNum];
        for (int i = 0; i < threadNum; i++) {
            myJmsContexts[i] = jmsContext.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            servers[i] = new Server(i,myJmsContexts[i], requestQueue, replyQueue);  //创建一个消息发送线程
        }
        for (int i = 0; i < threadNum; i++) {
            servers[i].start();   //start  thread
        }

        try {
            for (int i = 0; i < threadNum; i++) {
                servers[i].join();   //start  thread
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jmsContext.close();
        }
    }

}