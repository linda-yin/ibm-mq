package mqsamples.ptop;

import mqsamples.config.AppConfig;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.json.JSONObject;

/**
 * MessageSender is responsible for sending message to a specific queue.
 */
class MessageSender  extends Thread {
    private  JMSContext jmsContext;
    private  String queueName;
    private  int msgNum;
    private  int threadNo;
    private  int msgSize;  // 消息大小（字节数）
    private long sentCount = 0;  // 当前线程发送的消息计数
    private static final AtomicLong totalSentCount = new AtomicLong(0);  // 所有线程发送的总消息计数
    private static final Random random = new Random();  // 用于生成随机消息内容
    private static final AtomicLong ID_SEQUENCE = new AtomicLong(10001);  // 全局自增，保证每条消息 id 唯一
    private static final String[] GIVEN_NAMES = {"San", "Si", "Wu", "Wei", "Fang", "Min", "Jing", "Lei", "Qiang", "Na", "Xiao", "Hui", "Ying", "Bo"};
    private static final String[] SURNAMES = {"Zhang", "Li", "Wang", "Zhao", "Chen", "Liu", "Yang", "Huang", "Wu", "Zhou"};
    private static final String[] NATIONALITIES = {"CN", "US", "UK", "JP", "DE", "FR"};
    private static final String[] MARITAL_STATUSES = {"married", "single"};
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";  // 字母数字和特殊字符
    
    private enum EnumName {  //nameEnum定义了一系列的人名，用于后面的ApplicationData对象的创建
        zhangsan, lisi, Tom, Jack, Brown, Saddy, Tommy, Maomao, Willion, John
    }
    MessageSender(int threadNo,JMSContext  jmsContext,String queueName, int msgNum, int msgSize){
        this.jmsContext=jmsContext;
        this.queueName = queueName;
        this.msgNum = msgNum;
        this.threadNo = threadNo;
        this.msgSize = msgSize;
    }
    private  ApplicationData produceMessage(String name){
        ApplicationData applicationData = new ApplicationData(name);
        applicationData.setAge(15);
        applicationData.setHeight(183);
        applicationData.setHobby("reading");
        return applicationData;  //生成一个ApplicationData对象
    }
    /**
     * 生成消息内容：未指定 msgSize 时生成员工信息 JSON，指定 msgSize 时生成定长随机字符内容。
     * @param seqNum 消息序号
     * @return 消息内容
     */
    private String produceMessage(int seqNum){
        if (msgSize <= 0) {
            // 未指定消息大小时，生成员工信息 JSON，格式参考 msgcontent/employee.json
            JSONObject json = new JSONObject();
            json.put("id", String.format("E%05d", ID_SEQUENCE.getAndIncrement()));
            json.put("name", randomName());
            json.put("gender", random.nextBoolean() ? "male" : "female");
            json.put("birthDate", randomBirthDate());
            json.put("nationality", NATIONALITIES[random.nextInt(NATIONALITIES.length)]);
            json.put("salary", 3000 + random.nextInt(17001));
            json.put("maritalStatus", MARITAL_STATUSES[random.nextInt(MARITAL_STATUSES.length)]);
            return json.toString();
        }

        // 生成指定大小的随机字符内容（字母、数字和特殊字符）
        StringBuilder sb = new StringBuilder(msgSize);
        String header = String.format("[Thread-%d Msg-%d] ", threadNo, seqNum);
        sb.append(header);

        // 计算需要填充的字符数
        int remainingSize = msgSize - sb.length();
        if (remainingSize <= 0) {
            // 如果消息头已经超过指定大小，只返回消息头
            return sb.toString().substring(0, Math.min(msgSize, sb.length()));
        }

        for (int i = 0; i < remainingSize; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 随机生成姓名，格式为"名 姓"，例如 "San Zhang"。
     */
    private String randomName() {
        return GIVEN_NAMES[random.nextInt(GIVEN_NAMES.length)] + " " + SURNAMES[random.nextInt(SURNAMES.length)];
    }

    /**
     * 随机生成出生日期，格式为 yyyy-MM-dd。
     */
    private String randomBirthDate() {
        int year = 1970 + random.nextInt(31);   // 1970 - 2000
        int month = 1 + random.nextInt(12);
        int day = 1 + random.nextInt(28);       // 简化为 1-28，避免月末边界问题
        return String.format("%04d-%02d-%02d", year, month, day);
    }
    public void run(){

        JMSProducer producer = jmsContext.createProducer();
        Destination destination =jmsContext.createQueue(queueName);
        for(int i=1;i<=msgNum;i++){
            producer.send(destination,produceMessage(i));
            sentCount++;  // 递增当前线程的计数器
            totalSentCount.incrementAndGet();  // 递增全局计数器
        }
        System.out.println("线程 " + threadNo + " 完成，发送消息总数: " + sentCount + " 条，队列: " + queueName);
        //发送一个对象，ObjectMessage类型的消息
        // System.out.println("10 ObjectMessages will be sent------------");
        // for (EnumName enumName : EnumName.values()) {          //发送10个自定义的可序列化对象
        //     producer.send(destination,produceMessage(enumName.name())); // 所有的发送均通过messageTransport的send方法发出
        //     sentCount++;
        //     totalSentCount.incrementAndGet();
        //     System.out.println("发送第 " + (enumName.ordinal() + 1) + " 个Object类型的消息");
        // }

    }
    
    public static long getTotalSentCount() {
        return totalSentCount.get();
    }
}

public class SendMsg  {
    private MQConnectionFactory factory;
    private JMSContext jmsContext;
    private String connType;
    private String qmgrName;
    private String hostName;
    private int port;
    private int threadNum=1;
    private String channelName;
    private int msgNum=1;
    private String queueName;
    private String username=null;
    private String password;
    private int retryTimeout;
    private int msgSize=0;  // 消息大小（字节数），0表示使用默认消息格式

    private boolean initFromConfig(AppConfig config){
        connType = config.mqConnectionType().toUpperCase();
        qmgrName = config.mqQueueManager();
        hostName = config.mqHost();
        port = config.mqPort();
        channelName = config.mqChannel();
        queueName = config.queueName();
        msgNum = config.queueMsgNum();
        threadNum = config.queueThreadNum();
        msgSize = config.queueMsgSize();
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
            System.out.println("Connecting to MQ server " + hostName + ":" + port + " using channel " + channelName);
            factory.setChannel(channelName);
            if(username==null){
                jmsContext = factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            }else{
                jmsContext = factory.createContext(username,password,JMSContext.AUTO_ACKNOWLEDGE);
            }
        }catch (JMSException e){
            System.out.println(e.getMessage());
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
            System.out.println(e.getMessage());
        }
    }
    private  boolean createConnection(){
        try {
            if ("CLIENT".equals(connType)){
                System.out.println("Creating client connection...");
                createConnectionByClient();
            }else{
                System.out.println("Creating local connection...");
                createConnectionByLocal();
            }
            return true;
        } catch (Exception e) {
            System.err.println("连接 MQ 失败: " + mqsamples.MqErrorUtil.rootCauseMessage(e));
            return false;
        }
    }

    /**
     * 创建多个发送线程
     */
    public void execute(AppConfig config) {
        if(!initFromConfig(config)){
            System.err.println("Parameter error!!");
            return;
        }
        if (!createConnection()) {
            return;
        }
        JMSContext[]  myJmsContext = new JMSContext[threadNum];
        MessageSender[] messageSenders = new MessageSender[threadNum];
        for(int i=0;i<threadNum;i++) {
            myJmsContext[i] = jmsContext.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            messageSenders[i] = new MessageSender( i,myJmsContext[i], queueName, msgNum, msgSize);  //创建一个消息发送线程
        }
        for(int i=0;i<threadNum;i++){
            messageSenders[i].start();   //start  thread
        }

        try {
            for(int i=0;i<threadNum;i++){
                messageSenders[i].join();   //start  thread
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            System.out.println("所有线程完成，总共发送消息数: " + MessageSender.getTotalSentCount());
            jmsContext.close();
        }
    }

}