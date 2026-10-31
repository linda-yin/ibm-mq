package mqsamples.ptop;

import mqsamples.config.AppConfig;

import java.util.concurrent.atomic.AtomicLong;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;


/**
 * MessageSender is responsible for sending message to a specific queue.
 */
class MessageReceiver  extends Thread {
    private JMSContext jmsContext;
    private String queueName;
    private  int threadNo;
    private static final AtomicLong totalReceivedCount = new AtomicLong(0);  // 所有线程接收的总消息计数
    private static volatile long firstMessageTime = 0;  // 第一条消息的接收时间（毫秒）
    private static final Object timeLock = new Object();  // 用于同步第一条消息时间的设置
    
    MessageReceiver(int threadNo,JMSContext  jmsContext,String queueName){
        this.jmsContext=jmsContext;
        this.queueName = queueName;
        this.threadNo = threadNo;
    }

    private void   processMessage(Message message) throws JMSException {
        // 递增全局计数器
        long count = totalReceivedCount.incrementAndGet();
        
        // 记录第一条消息的接收时间
        if (count == 1) {
            synchronized (timeLock) {
                if (firstMessageTime == 0) {
                    firstMessageTime = System.currentTimeMillis();
                    System.out.println("开始接收消息，第一条消息时间: " + firstMessageTime);
                }
            }
        }
        
        // 每收到一条 TextMessage，打印消息内容

        
        if(message instanceof TextMessage){
            TextMessage textMessage = (TextMessage) message;
            System.out.println("收到文本消息: " + textMessage.getText());
        }else if(message instanceof BytesMessage){
            System.out.println("[消息计数: " + count + "] Thread "+threadNo+" received a message, messageLen: " + message.getBody(byte[].class).length);
        }else{
            System.out.println("[消息计数: " + count + "] Thread "+threadNo+" wrong message type");
        }
    }
    
    public void run(){
        Destination destination = jmsContext.createQueue(queueName);
        JMSConsumer consumer = jmsContext.createConsumer(destination);
        while (true){
            try {
                // if no message can be received in 10 seconds, message receiving stops
                Message message=consumer.receive(10*1000);
                if(message!=null){
                    processMessage(message);  // 计数器在 processMessage 中递增
                }else break;

            }catch (JMSException e){
                e.printStackTrace();
            }
        }
        System.out.println("线程 " + threadNo + " 完成，队列: " + queueName);
    }
    
    public static long getTotalReceivedCount() {
        return totalReceivedCount.get();
    }
    
    public static long getFirstMessageTime() {
        return firstMessageTime;
    }
}

public class RecvMsg  {
    private MQConnectionFactory factory;
    private JMSContext jmsContext;
    private String connType;
    private String qmgrName;
    private String hostName;
    private int port;
    private int threadNum=1;
    private String channelName;
    private String queueName;
    private String username=null;
    private String password;
    private int retryTimeout;

    private boolean initFromConfig(AppConfig config){
        connType = config.mqConnectionType().toUpperCase();
        qmgrName = config.mqQueueManager();
        hostName = config.mqHost();
        port = config.mqPort();
        channelName = config.mqChannel();
        queueName = config.queueName();
        threadNum = config.queueThreadNum();
        username = config.mqUsername();
        password = config.mqPassword();

        if (qmgrName == null || qmgrName.isEmpty()) {
            System.err.println("配置错误：请设置 mq.queueManager");
            return false;
        }
        if (queueName == null || queueName.isEmpty()) {
            System.err.println("配置错误：请设置 queue.name");
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
        if (!createConnection()) {
            return;
        }
        JMSContext[]  myJmsContext = new JMSContext[threadNum];
        MessageReceiver[] messageReceivers = new MessageReceiver[threadNum];
        for(int i=0;i<threadNum;i++) {
            myJmsContext[i] = jmsContext.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            messageReceivers[i] = new MessageReceiver(i, myJmsContext[i], queueName);  //创建一个消息接收线程
        }
        for(int i=0;i<threadNum;i++){
            messageReceivers[i].start();   //start  thread
        }

        try {
            for(int i=0;i<threadNum;i++){
                messageReceivers[i].join();   //start  thread
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            long totalCount = MessageReceiver.getTotalReceivedCount();
            System.out.println("所有线程完成，总共接收消息数: " + totalCount);
            
            // 打印总耗时
            if (MessageReceiver.getFirstMessageTime() > 0) {
                long currentTime = System.currentTimeMillis();
                long totalElapsedTime = currentTime - MessageReceiver.getFirstMessageTime();
                System.out.println("从第一条消息到程序结束，总耗时: " + totalElapsedTime + " 毫秒");
            }
            
            jmsContext.close();
        }
    }

}
