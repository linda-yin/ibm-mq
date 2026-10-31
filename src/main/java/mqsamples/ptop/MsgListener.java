package mqsamples.ptop;

import mqsamples.config.AppConfig;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;

/**
 * Created by user on 2019/5/22.
 */

class  MsgHandler implements MessageListener{
    String messageType;
    ApplicationData applicationData;
    private static final AtomicLong messageCount = new AtomicLong(0);
    
    public void onMessage(Message message) {
        long count = messageCount.incrementAndGet();
        try {
            if (message instanceof TextMessage) {
                System.out.println("[消息计数: " + count + "] Listener received a Textmessage,length:" + message.getBody(String.class).length());
            }else if(message instanceof BytesMessage){
                System.out.println("[消息计数: " + count + "] Listener received a ByteMessage,length:" + message.getBody(byte[].class).length);
            }else{
                System.out.println("[消息计数: " + count + "] messageType error!!!");
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
    
    public static long getMessageCount() {
        return messageCount.get();
    }
}
class MsgReceiver  extends Thread {
    private JMSContext jmsContext;
    private String queueName;
    private int threadNo;
    MsgReceiver(int threadNo,JMSContext  jmsContext,String queueName){
        this.jmsContext=jmsContext;
        this.queueName = queueName;
        this.threadNo = threadNo;
    }

    /**
     * 等待终端的输入，目的是为了打断消息接收
     */
    private void waitInput() {
        try {
            System.out.println("threadNo "+threadNo+"请单击任意键退出应用程序");
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void run(){
        Destination destination = jmsContext.createQueue(queueName);
        JMSConsumer consumer = jmsContext.createConsumer(destination);
        consumer.setMessageListener(new MsgHandler());
        waitInput();
    }
}
public class MsgListener {
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
        MsgReceiver[] msgReceivers = new MsgReceiver[threadNum];
        for(int i=0;i<threadNum;i++) {
            myJmsContext[i] = jmsContext.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            msgReceivers[i] = new MsgReceiver( i,myJmsContext[i],queueName);  //创建一个消息接收线程
        }
        for(int i=0;i<threadNum;i++){
            msgReceivers[i].start();   //start  thread
        }

        try {
            for(int i=0;i<threadNum;i++){
                msgReceivers[i].join();   //start  thread
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            System.out.println("程序退出，总共收到消息数: " + MsgHandler.getMessageCount());
            jmsContext.close();
        }
    }
}
