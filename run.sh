## MQ的环境
#    QM1:
#      通道名：SVR.CONN
#     端口：1419
#      队列：LQ1，LQ2
#    QM2:
#      通道名：JMSSVRCONN
#      端口：10250
#      队列：LQ1，LQ2
## 运行准备：
## 修改config.yml文件，配置MQ连接信息和运行模式
## mode: send          - 点对点：发送消息
## mode: receive       - 点对点：接收消息
  ## 如果发送和接收，需要设置queue:小节下的队列名和消息数。
## mode: publish       - JMS 发布订阅：发布消息
  ## 如果发布消息，需要设置topic:小节下的主题名和消息数。
## mode: subscribe     - JMS 发布订阅：订阅消息
  ## 如果订阅消息，需要设置topic:小节下的主题名和订阅者ID。
  ## 如果模糊匹配，则主题可用带通配符，如订阅/financial/#,或/financial/*
java  -jar target/mqdemo-1.0-jar-with-dependencies.jar config-qm1.yml

java  -jar target/mqdemo-1.0-jar-with-dependencies.jar config-qm2.yml

