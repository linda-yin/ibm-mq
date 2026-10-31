package mqsamples.pubsub;

import mqsamples.config.AppConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;

import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;

import org.json.JSONObject;

/**
 * 发布消息：向单个主题发布消息。
 * 消息内容参考 SendMsg 实现：
 *   - 主题包含 "financial"  时，生成金融交易消息（参考 msgcontent/financial.json）
 *   - 主题包含 "manufacture" 时，生成制造/进出口消息（参考 msgcontent/manufacture.json）
 * 主题不含 financial 或 manufacture 时，视为无效主题。
 */
class Publisher extends Thread {
    private JMSContext jmsContext;
    private String topic;
    private int msgNum;

    private static final AtomicLong ID_SEQUENCE = new AtomicLong(1);
    private static final Random RANDOM = new Random();

    // 金融交易字段取值范围
    private static final String[] TRANSACTION_TYPES = {"DEPOSIT", "WITHDRAWAL"};
    private static final String[] ACCOUNT_NAMES = {"张三", "李四", "王五", "赵六", "陈七", "刘八"};
    private static final String[] ACCOUNT_TYPES = {"PERSONAL", "CORPORATE"};
    private static final String[] BANK_CODES = {"ICBC", "ABC", "BOC", "CCB", "BCOM"};

    // 制造/进出口字段取值范围
    private static final String[] TRADE_DIRECTIONS = {"EXPORT", "IMPORT"};
    private static final String[] PRODUCT_NAMES = {"智能手机", "笔记本电脑", "平板电脑", "智能手表", "无线耳机"};
    private static final String[] SPECIFICATIONS = {"6.1英寸，128GB", "15.6英寸，512GB", "11英寸，256GB", "44mm，32GB", "标准版"};
    private static final String[] BRANDS = {"apple", "huawei", "xiaomi", "samsung", "oppo"};

    Publisher(JMSContext jmsContext, String topic, int msgNum){
        this.jmsContext = jmsContext;
        this.topic = topic;
        this.msgNum = msgNum;
    }

    private String produceMessage(int seqNum){
        String lower = topic.toLowerCase();
        if (lower.contains("financial")) {
            return produceFinancialMessage();
        } else if (lower.contains("manufacture")) {
            return produceManufactureMessage();
        }
        return null; // 已在 execute 中校验，不会走到这里
    }

    /** 生成金融交易消息，字段参考 msgcontent/financial.json */
    private String produceFinancialMessage(){
        JSONObject json = new JSONObject();
        json.put("messageId", "MSG" + timestamp("yyyyMMddHHmmssSSS") + String.format("%05d", ID_SEQUENCE.getAndIncrement()));
        json.put("messageType", "BANK_DEPOSIT_WITHDRAWAL");
        json.put("timestamp", timestamp("yyyy-MM-dd HH:mm:ss.SSS"));
        json.put("transactionType", TRANSACTION_TYPES[RANDOM.nextInt(TRANSACTION_TYPES.length)]);
        json.put("amount", String.format("%.2f", 0.01 + RANDOM.nextDouble() * 999999.99));
        json.put("currency", "CNY");
        json.put("accountNo", randomAccountNo());
        json.put("accountName", ACCOUNT_NAMES[RANDOM.nextInt(ACCOUNT_NAMES.length)]);
        json.put("accountType", ACCOUNT_TYPES[RANDOM.nextInt(ACCOUNT_TYPES.length)]);
        json.put("bankCode", BANK_CODES[RANDOM.nextInt(BANK_CODES.length)]);
        json.put("status", "SUCCESS");
        return json.toString();
    }

    /** 生成制造/进出口消息，字段参考 msgcontent/manufacture.json */
    private String produceManufactureMessage(){
        JSONObject json = new JSONObject();
        json.put("messageId", "MSG" + timestamp("yyyyMMddHHmmssSSS") + String.format("%05d", ID_SEQUENCE.getAndIncrement()));
        json.put("messageType", "GOODS_IMPORT_EXPORT");
        json.put("timestamp", timestamp("yyyy-MM-dd HH:mm:ss.SSS"));
        json.put("tradeDirection", TRADE_DIRECTIONS[RANDOM.nextInt(TRADE_DIRECTIONS.length)]);
        json.put("productName", PRODUCT_NAMES[RANDOM.nextInt(PRODUCT_NAMES.length)]);
        json.put("specification", SPECIFICATIONS[RANDOM.nextInt(SPECIFICATIONS.length)]);
        json.put("brand", BRANDS[RANDOM.nextInt(BRANDS.length)]);
        json.put("quantity", String.valueOf(1 + RANDOM.nextInt(10000)));
        json.put("unitPrice", String.format("%.2f", 0.01 + RANDOM.nextDouble() * 5000.00));
        json.put("currency", "USD");
        return json.toString();
    }

    private String randomAccountNo(){
        StringBuilder sb = new StringBuilder("6222");
        for (int i = 0; i < 15; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String timestamp(String pattern){
        return new SimpleDateFormat(pattern).format(new Date());
    }

    public void run(){
        JMSProducer producer = jmsContext.createProducer();
        Destination destination = jmsContext.createTopic(topic);
        for(int i=1;i<=msgNum;i++){
            producer.send(destination, produceMessage(i));
        }
        System.out.println(msgNum + " messages have been sent to topic(" + topic + ")");
    }
}

public class PubMsg {
    private MQConnectionFactory factory;
    private JMSContext jmsContext;
    private String connType;
    private String qmgrName;
    private String hostName;
    private int port;
    private String channelName;
    private int msgNum=1;
    private String username=null;
    private String password;
    private int retryTimeout;

    private boolean initFromConfig(AppConfig config){
        connType = config.mqConnectionType().toUpperCase();
        qmgrName = config.mqQueueManager();
        hostName = config.mqHost();
        port = config.mqPort();
        channelName = config.mqChannel();
        msgNum = config.pubsubMsgNum();
        username = config.mqUsername();
        password = config.mqPassword();

        if (qmgrName == null || qmgrName.isEmpty()) {
            System.err.println("配置错误：请设置 mq.queueManager");
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

    /**
     * 向单个主题发布消息
     */
    public void execute(AppConfig config) {
        if(!initFromConfig(config)){
            System.err.println("Parameter error!!");
            return;
        }

        String topic = config.pubsubTopic();
        if (topic == null || topic.isEmpty()) {
            System.err.println("配置错误：请设置 pubsub.topic");
            return;
        }
        String lower = topic.toLowerCase();
        if (!lower.contains("financial") && !lower.contains("manufacture")) {
            System.err.println("无效主题: " + topic + "，主题名需包含 financial 或 manufacture");
            return;
        }

        if (!createConnection()) {
            return;
        }
        JMSContext myJmsContext = jmsContext.createContext(JMSContext.AUTO_ACKNOWLEDGE);
        Publisher publisher = new Publisher(myJmsContext, topic, msgNum);
        publisher.start();
        try {
            publisher.join();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            jmsContext.close();
        }
    }
}
