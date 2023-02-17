# RocketMQ 源码分析

消息 生成、存储、消费 消息即数据 RocketMQ即特殊数据库 可为业务提供请求堆积功能 尤其适合有超量请求的业务

高可用、高性能、负载均衡、动态扩容缩容

削峰填谷、异步解耦

## 复制策略、刷盘策略

生产者 →  |  broke-master 内存 → 磁盘 |
                   ↓
         |  broke-slave 内存 → 磁盘 |

**复制策略**

+ 同步复制，生产者发消息到master，master同步到slave成功后(可能同步刷盘、可能异步刷盘)，才响应生产者消息发送成功
+ 异步复制，生产者发消息到master，发成功后(可能同步刷盘、可能异步刷盘)，立马响应生产者消息发送成功，然后异步同步消息到slave，生产者不知道消息同步到slave成功与否

同步复制，适合公司核心业务，例如金融类业务  
异步复制，适合公司非核心业务，例如日志类业务

**刷盘策略**

+ 同步刷盘，生产者发消息发送到master内存，然后内存落盘到磁盘后，才响应生产者消息发送成功
+ 异步刷盘，生产者发消息发送到master内存，发成功后，立马响应生产者消息发送成功，然后异步将消息落盘到磁盘，生产者不知道消息落盘到磁盘成功与否
+ 如果结合复制策略，那么slave也是如此

同步刷盘，适合公司核心业务，例如金融类业务  
异步刷盘，适合公司非核心业务，例如日志类业务

同步策略，性能较低，但安全性更高  
异步策略，安全性更地，但性能较高

                   
## 定时消息

## 死信队列

默认重试16次，都失败则写入到死信队列

```text
  public RemotingCommand sendMessage(final ChannelHandlerContext ctx,
        final RemotingCommand request,
        final SendMessageContext sendMessageContext,
        final SendMessageRequestHeader requestHeader,
        final TopicQueueMappingContext mappingContext,
        final SendMessageCallback sendMessageCallback) throws RemotingCommandException {
        ...
        // DLQ - 死信队列 ( Dead Letter Queue )
        if (!handleRetryAndDLQ(requestHeader, response, request, msgInner, topicConfig, oriProps)) {
            return response;
        }
```

## 消息发送/存储 store

```text
RequestCode.SEND_MESSAGE 

Find Usages...

public SendResult sendMessage(
        final String addr,
        final String brokerName,
        final Message msg,
        final SendMessageRequestHeader requestHeader,
        final long timeoutMillis,
        final CommunicationMode communicationMode,
        final SendCallback sendCallback,
        final TopicPublishInfo topicPublishInfo,
        final MQClientInstance instance,
        final int retryTimesWhenSendFailed,
        final SendMessageContext context,
        final DefaultMQProducerImpl producer
    ) throws RemotingException, MQBrokerException, InterruptedException {
                ...
                request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, requestHeader);
            }
        }
        request.setBody(msg.getBody());
        ...
        
        
// broker
public RemotingCommand processRequest(ChannelHandlerContext ctx,
        RemotingCommand request) throws RemotingCommandException {
        ...
        switch (request.getCode()) {
            case RequestCode.CONSUMER_SEND_MSG_BACK:
                return this.consumerSendMsgBack(ctx, request);
            default:
                // 解析请求头
                SendMessageRequestHeader requestHeader = parseRequestHeader(request);
               

                RemotingCommand response;
                
                if (requestHeader.isBatch()) { // 批量发送
                    response = this.sendBatchMessage(ctx, request, traceContext, requestHeader, mappingContext,
                        (ctx1, response1) -> executeSendMessageHookAfter(response1, ctx1));
                } else { // 单条发送
                    response = this.sendMessage(ctx, request, traceContext, requestHeader, mappingContext,
                        (ctx12, response12) -> executeSendMessageHookAfter(response12, ctx12));
                }
                // 返回响应
                return response;
        }
        
        
public RemotingCommand sendMessage(final ChannelHandlerContext ctx,
        final RemotingCommand request,
        final SendMessageContext sendMessageContext,
        final SendMessageRequestHeader requestHeader,
        final TopicQueueMappingContext mappingContext,
        final SendMessageCallback sendMessageCallback) throws RemotingCommandException {
        ...
        // 请求体
        final byte[] body = request.getBody();
        // 队列ID，由客户端选择
        int queueIdInt = requestHeader.getQueueId();
        TopicConfig topicConfig = this.brokerController.getTopicConfigManager().selectTopicConfig(requestHeader.getTopic());
        // 如果队列ID小于零，随机选一个队列
        if (queueIdInt < 0) {
            queueIdInt = randomQueueId(topicConfig.getWriteQueueNums());
        }

        // 消息内部
        MessageExtBrokerInner msgInner = new MessageExtBrokerInner();
        msgInner.setTopic(requestHeader.getTopic());
        msgInner.setQueueId(queueIdInt);

        msgInner.setBody(body);
        msgInner.setFlag(requestHeader.getFlag());
        ...
        if (brokerController.getBrokerConfig().isAsyncSendEnable()) {
            ...
        } else {
            PutMessageResult putMessageResult = null;
            if (sendTransactionPrepareMessage) {
                putMessageResult = this.brokerController.getTransactionalMessageService().prepareMessage(msgInner);
            } else { // 将消息写入磁盘
                putMessageResult = this.brokerController.getMessageStore().putMessage(msgInner);
            ...
        }
    } 
    
    // PutMessageResult 放消息结果 把消息放到磁盘的结果 同步调用/异步回调
    protected PutMessageResult encode(MessageExtBrokerInner msgInner) {
            this.byteBuf.clear();
            /**
             * Serialize message  序列化消息
             */
            final byte[] propertiesData =
                msgInner.getPropertiesString() == null ? null : msgInner.getPropertiesString().getBytes(MessageDecoder.CHARSET_UTF8);

            final int propertiesLength = propertiesData == null ? 0 : propertiesData.length;

            if (propertiesLength > Short.MAX_VALUE) {
                log.warn("putMessage message properties length too long. length={}", propertiesData.length);
                return new PutMessageResult(PutMessageStatus.PROPERTIES_SIZE_EXCEEDED, null);
            }

            final byte[] topicData = msgInner.getTopic().getBytes(MessageDecoder.CHARSET_UTF8);
            final int topicLength = topicData.length;

            final int bodyLength = msgInner.getBody() == null ? 0 : msgInner.getBody().length;

            final int msgLen = calMsgLength(msgInner.getSysFlag(), bodyLength, topicLength, propertiesLength);

            // Exceeds the maximum message body
            if (bodyLength > this.maxMessageBodySize) {
                CommitLog.log.warn("message body size exceeded, msg total size: " + msgLen + ", msg body size: " + bodyLength
                    + ", maxMessageSize: " + this.maxMessageBodySize);
                return new PutMessageResult(PutMessageStatus.MESSAGE_ILLEGAL, null);
            }

            final long queueOffset = msgInner.getQueueOffset();

            // Exceeds the maximum message
            if (msgLen > this.maxMessageSize) {
                CommitLog.log.warn("message size exceeded, msg total size: " + msgLen + ", msg body size: " + bodyLength
                    + ", maxMessageSize: " + this.maxMessageSize);
                return new PutMessageResult(PutMessageStatus.MESSAGE_ILLEGAL, null);
            }

            // 1 TOTALSIZE
            this.byteBuf.writeInt(msgLen);
            // 2 MAGICCODE
            this.byteBuf.writeInt(CommitLog.MESSAGE_MAGIC_CODE);
            // 3 BODYCRC
            this.byteBuf.writeInt(msgInner.getBodyCRC());
            // 4 QUEUEID
            this.byteBuf.writeInt(msgInner.getQueueId());
            // 5 FLAG
            this.byteBuf.writeInt(msgInner.getFlag());
            // 6 QUEUEOFFSET
            this.byteBuf.writeLong(queueOffset);
            // 7 PHYSICALOFFSET, need update later
            this.byteBuf.writeLong(0);
            // 8 SYSFLAG
            this.byteBuf.writeInt(msgInner.getSysFlag());
            // 9 BORNTIMESTAMP
            this.byteBuf.writeLong(msgInner.getBornTimestamp());

            // 10 BORNHOST
            ByteBuffer bornHostBytes = msgInner.getBornHostBytes();
            this.byteBuf.writeBytes(bornHostBytes.array());

            // 11 STORETIMESTAMP
            this.byteBuf.writeLong(msgInner.getStoreTimestamp());

            // 12 STOREHOSTADDRESS
            ByteBuffer storeHostBytes = msgInner.getStoreHostBytes();
            this.byteBuf.writeBytes(storeHostBytes.array());

            // 13 RECONSUMETIMES
            this.byteBuf.writeInt(msgInner.getReconsumeTimes());
            // 14 Prepared Transaction Offset
            this.byteBuf.writeLong(msgInner.getPreparedTransactionOffset());
            // 15 BODY
            this.byteBuf.writeInt(bodyLength);
            if (bodyLength > 0)
                this.byteBuf.writeBytes(msgInner.getBody());
            // 16 TOPIC
            this.byteBuf.writeByte((byte) topicLength);
            this.byteBuf.writeBytes(topicData);
            // 17 PROPERTIES
            this.byteBuf.writeShort((short) propertiesLength);
            if (propertiesLength > 0)
                this.byteBuf.writeBytes(propertiesData);

            return null;
        }           
```

## 写消息高可用

如果一个主题的队列是broker-a的q0 q1 q2 q3 和 broker-b的q0 q1 q2 q3，共8个队列，分布在两个broker。  
因为发消息是轮询的，如果往broker-a的q0发消息失败，那么会将broker-a的所有队列屏蔽一段时间，  
此时消息会发送给broker-b的q0。屏蔽时间过后，如果还失败，屏蔽时间会递增，但有上限。

简单来比喻，更实际会有出入，例如队列索引。生产者往TopicA发消息，TopicA共5个队列，队列1 队列2在BrokerA，队列3 队列4 队列5在BrokerB，
如果BrokerA宕机了，那么队列1 队列2是不可用的

```text
private SendResult sendDefaultImpl(
        Message msg,
        final CommunicationMode communicationMode,
        final SendCallback sendCallback,
        final long timeout
    ) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        this.makeSureStateOK();
        TopicPublishInfo topicPublishInfo = this.tryToFindTopicPublishInfo(msg.getTopic());
        if (topicPublishInfo != null && topicPublishInfo.ok()) {
            
            for (; times < timesTotal; times++) {
                String lastBrokerName = null == mq ? null : mq.getBrokerName();
                MessageQueue mqSelected = this.selectOneMessageQueue(topicPublishInfo, lastBrokerName);
                if (mqSelected != null) {
                    try {
                        // 发送消息
                        sendResult = this.sendKernelImpl(msg, mq, communicationMode, sendCallback, topicPublishInfo, timeout - costTime);
        
                        ...
                        this.updateFaultItem(mq.getBrokerName(), endTimestamp - beginTimestampPrev, false);
                        
                    } catch (RemotingException e) {  // 发送失败
                        ...
                        this.updateFaultItem(mq.getBrokerName(), endTimestamp - beginTimestampPrev, true);
                    } catch (MQClientException e) {  // 发送失败
                        ...
                        this.updateFaultItem(mq.getBrokerName(), endTimestamp - beginTimestampPrev, true);
                    } catch (MQBrokerException e) {  // 发送失败
                        ...
                        this.updateFaultItem(mq.getBrokerName(), endTimestamp - beginTimestampPrev, true);
                    } catch (InterruptedException e) {  // 发送失败
                        ...
                        this.updateFaultItem(mq.getBrokerName(), endTimestamp - beginTimestampPrev, false);
                    }
                    ...
    }
    
// MQ故障策略    
public class MQFaultStrategy {
    ...
    private long[] latencyMax = {50L, 100L, 550L, 1000L, 2000L, 3000L, 15000L};
    private long[] notAvailableDuration = {0L, 0L, 30000L, 60000L, 120000L, 180000L, 600000L};
    ...
    
latency
noun [ U ]   formal
UK  /ˈleɪ.tən.si/ US  /ˈleɪ.tən.si/
 
the fact of being present but needing particular conditions to become active, obvious, or completely developed
潜在因素；潜伏
The latency period for the cancer is 15 years.
癌症的潜伏期是十五年。
They measured the latency of the brain's response to a stimulus.
他们测量了大脑对刺激做出反应的潜在因素。
```

## Netty 网络编程框架

https://netty.io/

Netty is an asynchronous event-driven network application framework
for rapid development of maintainable high performance protocol servers & clients.

使用 NIO 非阻塞IO 高性能的网络编程框架

要实现服务器，方式很多，性能比较差的有，  
一个请求就开启一个新线程进行处理，这样无法做到高性能，因为开启线程需要资源，线程上下文切换需要耗时，多线程的性能很依赖CPU核数，如果16核CPU，跑16个线程性能最优，适合连接数比较少的情况。  
另一种是使用线程池，但在阻塞IO的情况下，没有抢到线程的客户端请求会被强制等待或丢弃，适合请求处理时间短，短连接情况，处理完马上断开连接，以处理其他请求。

## NameServer 无状态

HTTP协议是无状态的，Web服务器不记录和客户端的连接状态，第一次请求处理完，第二次请求再来，不清楚是不是还是原来的客户端。

业务中使用Session、Cookie、Token等记录的信息，可以使请求有状态，但是这是业务中实现的，并不是HTTP协议实现的

NameServer 无状态，意味着不会记录和客户端请求/连接状态，不清楚请求的发起者是谁，也不记录是谁。

## Linux 环境

```shell
yum install lrzsz
rz

https://www.oracle.com/java/technologies/javase/javase8u211-later-archive-downloads.html
yum install jdk-8u351-linux-x64.rpm

vim /etc/profile
export JAVA_HOME=/usr/java/jdk1.8.0_351-amd64
source /etc/profile
```

## JPS

```shell
[root@centos /opt/rocketmq/rocketmq-all-5.0.0-bin-release]# jps
9713 ProxyStartup
9204 NamesrvStartup
9758 Jps

```

## SecureCRT参考配置

Theme: Linux

Font: bold 14

## 虚拟机参考配置

vmware  2G 处理器 1 硬盘 20G

## 日志默认存放位置

家目录的logs

~/logs/...

## gRPC 远程服务调用框架/远程过程调用框架

类似Thrift等RPC框架，Thrift最初由Facebook开发，后面变成Apache项目

gRPC 最初由Google开发

不是使用HTTP协议，因为性能相对较差，使用自定义的协议，基于TCP。

输出的是字节流，字节流需要序列化/反序列化，序列化/反序列化使用的不是JSON/XML，因为性能相对较差，使用的是Google开源的Protocol Buffers。

Protocol Buffers 性能比JSON/XML高，压缩率也更大，体积更小，更利于网络传输。

gRPC或者RPC使用的是Client/Server架构。

## 调试启动时修改内存

runserver.sh runbroker.sh

## RocketMQ relative repositories

https://github.com/orgs/apache/repositories?q=rocketmq&type=all&language=&sort=

## 创建主题 Dashboard

writeQueueNums  16  
readQueueNums 16  
perm 6  

读写队列数量，物理上是同一个队列，是逻辑上的队列数量，第一次创建时一般需要一样，如果不一样，那么以更大的数量为主，创建物理队列

作用是为了让主题的队列进行动态扩容/缩容，不丢失任何数据的动态扩容/缩容，例如如果要缩容，那么可以先把写队列数量设置为8，那么生产者会往0 1 2 3 4 5 6 7队列写消息，
但不会往8 9 10 11 12 13 14 15队列写消息，但是读队列数量还是16，8 9 10 11 12 13 14 15堆积的消息，依然能被消费者消费。
等8 9 10 11 12 13 14 15队列里面的数据全被消费完后，再把读队列数量也改为8，实现动态缩容，最后物理队列也会变成8。

如果要进行扩容，例如原来读写队列数量都是8，那么可以先把读队列数量改为16，那么消费者会读取0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15队列的数据，
但是目前8 9 10 11 12 13 14 15没有任何数据可以读，等读队列准备好后，这时候写队列再改成16，那么8 9 10 11 12 13 14 15就会有数据，实现不丢任何数据的扩容。

+ 缩容，先改小写，再改小读
+ 缩容，先改大读，再改大写

perm 2 4 6 只读 只写 读写，主题的权限，作用，可以临时关闭读或关闭写，实现生产者/消费者的调整

## Broker

+ Remoting Module 处理 客户端 请求
+ Client Manager 客户端管理器，管理客户端
+ Store Service 存储服务，消息存储到物理磁盘
+ HA Service 高可用服务，Master Broker和Slave Broker之间数据同步
+ Index Service 索引服务 特定消息Key索引服务
+ ...

高可用，主Broker宕机，备用Broker顶上

brokerName相同可抽象理解为同一个Broker，brokerId 0表示Master 非0表示Slave，所有Broker都会跟NameServer建立长连接，并且默认每30秒向NameServer发送自己的心跳包

DefaultCluster默认集群名

## 主从集群/主备集群

主从集群的从节点需要工作

主备集群的备节点不需要工作，之作备份用，主出问题时，顶上

## 消息标签（MessageTag）

消息标签是Apache RocketMQ 提供的细粒度消息分类属性，可以在主题层级之下做消息类型的细分。消费者通过订阅特定的标签来实现细粒度过滤。

## 生成端发送，选择主题里面的队列

负载均衡

简单情况，轮询其中一个队列进行发送

```text
// rocketmq-client
    private SendResult sendDefaultImpl(
        Message msg,
        final CommunicationMode communicationMode,
        final SendCallback sendCallback,
        final long timeout
    ) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        ...
        // 找到主题发布信息
        TopicPublishInfo topicPublishInfo = this.tryToFindTopicPublishInfo(msg.getTopic());
        if (topicPublishInfo != null && topicPublishInfo.ok()) {
            ...
            // 发消息重试机制
            for (; times < timesTotal; times++) {
                String lastBrokerName = null == mq ? null : mq.getBrokerName();
                // 选择一个消息队列
                MessageQueue mqSelected = this.selectOneMessageQueue(topicPublishInfo, lastBrokerName);
                ...

    public MessageQueue selectOneMessageQueue(final TopicPublishInfo tpInfo, final String lastBrokerName) {
        if (this.sendLatencyFaultEnable) {
            try {
                int index = tpInfo.getSendWhichQueue().incrementAndGet();
                for (int i = 0; i < tpInfo.getMessageQueueList().size(); i++) {
                    int pos = Math.abs(index++) % tpInfo.getMessageQueueList().size();
                    if (pos < 0)
                        pos = 0;
                    MessageQueue mq = tpInfo.getMessageQueueList().get(pos);
                    if (latencyFaultTolerance.isAvailable(mq.getBrokerName()))
                        return mq;
                }

                final String notBestBroker = latencyFaultTolerance.pickOneAtLeast();
                int writeQueueNums = tpInfo.getQueueIdByBroker(notBestBroker);
                if (writeQueueNums > 0) {
                    final MessageQueue mq = tpInfo.selectOneMessageQueue();
                    if (notBestBroker != null) {
                        mq.setBrokerName(notBestBroker);
                        mq.setQueueId(tpInfo.getSendWhichQueue().incrementAndGet() % writeQueueNums);
                    }
                    return mq;
                } else {
                    latencyFaultTolerance.remove(notBestBroker);
                }
            } catch (Exception e) {
                log.error("Error occurred when selecting message queue", e);
            }

            return tpInfo.selectOneMessageQueue();
        }
        // 默认情况
        return tpInfo.selectOneMessageQueue(lastBrokerName);
    }            
    
     public MessageQueue selectOneMessageQueue(final String lastBrokerName) {
        if (lastBrokerName == null) {
            return selectOneMessageQueue();
        } else {
            for (int i = 0; i < this.messageQueueList.size(); i++) {
                int index = this.sendWhichQueue.incrementAndGet();
                // 累加 与 队列大小 取余
                int pos = Math.abs(index) % this.messageQueueList.size();
                if (pos < 0)
                    pos = 0;
                // 获取具体索引的队列    
                MessageQueue mq = this.messageQueueList.get(pos);
                if (!mq.getBrokerName().equals(lastBrokerName)) {
                    return mq;
                }
            }
            return selectOneMessageQueue();
        }
    }
    
    public MessageQueue selectOneMessageQueue() {
        // 累加 与 队列大小 取余
        int index = this.sendWhichQueue.incrementAndGet();
        int pos = Math.abs(index) % this.messageQueueList.size();
        if (pos < 0)
            pos = 0;
        return this.messageQueueList.get(pos);
    }
```

## NameServer

NameServer是注册中心，类似Zookeeper、Nacos

不同的是，NameServer集群的每个NameServer是不互相通信的

Broker启动时需要指定所有NameServer地址，并且NameServer和Broker会有长连接，会有心跳检测、剔除机制等。

## 自动创建主题

生成客户端，发送消息，如果主题路由没找到，那么会再找一次，进行自动创建主题

```text
    private TopicPublishInfo tryToFindTopicPublishInfo(final String topic) {
        TopicPublishInfo topicPublishInfo = this.topicPublishInfoTable.get(topic);
        // 找不到主题路由
        if (null == topicPublishInfo || !topicPublishInfo.ok()) {
            this.topicPublishInfoTable.putIfAbsent(topic, new TopicPublishInfo());
            // 从NameServer拉取主题路由信息
            this.mQClientFactory.updateTopicRouteInfoFromNameServer(topic);
            // 获取主题发布信息
            topicPublishInfo = this.topicPublishInfoTable.get(topic);
        }
        // 找到路由信息则返回
        if (topicPublishInfo.isHaveTopicRouterInfo() || topicPublishInfo.ok()) {
            return topicPublishInfo;
        } else {
            // 如果依然找不到主题路由信息，则会自动创建主题，注意第二个参数为true
            this.mQClientFactory.updateTopicRouteInfoFromNameServer(topic, true, this.defaultMQProducer);
            topicPublishInfo = this.topicPublishInfoTable.get(topic);
            return topicPublishInfo;
        }
    }
    
    public boolean updateTopicRouteInfoFromNameServer(final String topic, boolean isDefault,
        DefaultMQProducer defaultMQProducer) {
        try {
            if (this.lockNamesrv.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    TopicRouteData topicRouteData;
                    if (isDefault && defaultMQProducer != null) {
                        topicRouteData = this.mQClientAPIImpl.getDefaultTopicRouteInfoFromNameServer(defaultMQProducer.getCreateTopicKey(),
                            clientConfig.getMqClientApiTimeout());
         ...
    }
    
    defaultMQProducer.getCreateTopicKey()
    
// rocketmq-common
public class TopicValidator {
    public static final String AUTO_CREATE_TOPIC_KEY_TOPIC = "TBW102"; // Will be created at broker when isAutoCreateTopicEnable
    public static final String RMQ_SYS_SCHEDULE_TOPIC = "SCHEDULE_TOPIC_XXXX";
    ...
    
// rocketmq-broker
    public TopicConfigManager(BrokerController brokerController) {
        this.brokerController = brokerController;
        {
            String topic = TopicValidator.RMQ_SYS_SELF_TEST_TOPIC;
            TopicConfig topicConfig = new TopicConfig(topic);
            TopicValidator.addSystemTopic(topic);
            topicConfig.setReadQueueNums(1);
            topicConfig.setWriteQueueNums(1);
            this.topicConfigTable.put(topicConfig.getTopicName(), topicConfig);
        }
        {
        }
            // 自动创建主题
            if (this.brokerController.getBrokerConfig().isAutoCreateTopicEnable()) {
                String topic = TopicValidator.AUTO_CREATE_TOPIC_KEY_TOPIC;
                TopicConfig topicConfig = new TopicConfig(topic);
                TopicValidator.addSystemTopic(topic);
                topicConfig.setReadQueueNums(this.brokerController.getBrokerConfig()
                    .getDefaultTopicQueueNums());
                topicConfig.setWriteQueueNums(this.brokerController.getBrokerConfig()
                    .getDefaultTopicQueueNums());
                int perm = PermName.PERM_INHERIT | PermName.PERM_READ | PermName.PERM_WRITE;
                topicConfig.setPerm(perm);
                this.topicConfigTable.put(topicConfig.getTopicName(), topicConfig);
            }
        }
        ...
```

## 客户端、服务端配置同步模型

+ push模型，发布订阅模型，实时性高，但服务端要维护客户端的长连接，要进行心跳检测等，需要占用较多的系统资源。
  适合用在实时性要求较高，客户端不多的情况。
+ pull模型，拉取模型，客户端定时向服务端拉取最新配置，实时性较差，一般30秒拉取一次，不需要维护长连接，拉取完后，直接断开连接。
+ long pulling模型，客户但定时向服务端拉去最新配置，但不会马上断开连接，服务端维护一段时间，如果这段时间内有配置更新，马上通知客户端。时间过了，就断开连接。
  中庸之道，充分考虑push和pull模型两者的优缺点。

## 生产端更新Topic路由信息 

生产客户端和NameServer服务端的配置同步是使用了pull模型

rocketmq-client (not rocketmq-client-java)

1. 定时更新，默认每隔30秒；
2. 主动更新，发消息时，具体的Topic路由

到NameServer获取最新的Topic路由，并保存到本地内存

rocketmq-all-5.0.0-source-release/example/src/main/java/org/apache/rocketmq/example/quickstart

```java
public class MQClientInstance {
    // ...
    private void startScheduledTask() {
        // ...
        this.scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                MQClientInstance.this.updateTopicRouteInfoFromNameServer();
            } catch (Exception e) {
                log.error("ScheduledTask updateTopicRouteInfoFromNameServer exception", e);
            }
        }, 10, this.clientConfig.getPollNameServerInterval(), TimeUnit.MILLISECONDS);
        // ...
    }
    // ...
}    
```

```java
public class DefaultMQProducerImpl implements MQProducerInner {
    // ...
    private TopicPublishInfo tryToFindTopicPublishInfo(final String topic) {
        TopicPublishInfo topicPublishInfo = this.topicPublishInfoTable.get(topic);
        if (null == topicPublishInfo || !topicPublishInfo.ok()) {
            this.topicPublishInfoTable.putIfAbsent(topic, new TopicPublishInfo());
            this.mQClientFactory.updateTopicRouteInfoFromNameServer(topic);
            topicPublishInfo = this.topicPublishInfoTable.get(topic);
        }

        if (topicPublishInfo.isHaveTopicRouterInfo() || topicPublishInfo.ok()) {
            return topicPublishInfo;
        } else {
            this.mQClientFactory.updateTopicRouteInfoFromNameServer(topic, true, this.defaultMQProducer);
            topicPublishInfo = this.topicPublishInfoTable.get(topic);
            return topicPublishInfo;
        }
    }
    // ...
}    
```

## RocketMQ 官网

https://rocketmq.apache.org/

## 源码地址

RocketMQ https://github.com/apache/rocketmq

## 源码编译构建

```shell
> java -version
java version "1.8.0_333"
Java(TM) SE Runtime Environment (build 1.8.0_333-b02)
Java HotSpot(TM) 64-Bit Server VM (build 25.333-b02, mixed mode)

> mvn -Prelease-all -DskipTests -Dspotbugs.skip=true clean install -U
```

检查 java -version，尽量保证编译用的 java 版本，跟后面运行用的 java 版本一致，不然可能会出现奇怪问题。

例如：用 java 11 来编译，用 java 8 来运行 会出现

```shell
> .\bin\mqbroker.cmd --namesrvAddr localhost:9876
java.lang.NoSuchMethodError: java.nio.ByteBuffer.position(I)Ljava/nio/ByteBuffer;
        at org.apache.rocketmq.store.timer.TimerWheel.checkPhyPos(TimerWheel.java:176)
        at org.apache.rocketmq.store.timer.TimerMessageStore.recover(TimerMessageStore.java:281)
        at org.apache.rocketmq.store.timer.TimerMessageStore.load(TimerMessageStore.java:218)
        at org.apache.rocketmq.broker.BrokerController.initialize(BrokerController.java:757)
        at org.apache.rocketmq.broker.BrokerStartup.createBrokerController(BrokerStartup.java:224)
        at org.apache.rocketmq.broker.BrokerStartup.main(BrokerStartup.java:58)
```

## Could not find artifact io.grpc:grpc-core:jar:1.45.0

D:\apache-maven-3.8.6\conf\settings.xml 去掉 mirrors 里面的所有 mirror

## Windows 运行

启动 mqnamesrc (用 start 命令可以开启新的窗口)

```shell
> cd rocketmq-all-5.0.0-source-release\distribution\target\rocketmq-5.0.0\rocketmq-5.0.0
> .\bin\mqnamesrv.cmd
Please set the ROCKETMQ_HOME variable in your environment!
> set ROCKETMQ_HOME=E:\java-project\rocketmq-source-code-analysis\rocketmq-all-5.0.0-source-release\distribution\target\rocketmq-5.0.0\rocketmq-5.0.0
(最好设置环境变量，避免每次都要设置临时环境变量)
> .\bin\mqnamesrv.cmd
Java HotSpot(TM) 64-Bit Server VM warning: Option UseConcMarkSweepGC was deprecated in version 9.0 and will likely be removed in a future release.
Unrecognized VM option 'UseCMSCompactAtFullCollection'
Error: Could not create the Java Virtual Machine.
Error: A fatal exception has occurred. Program will exit.

解决方案：使用支持 VM option 'UseCMSCompactAtFullCollection' 的 Java 版本。
也可以考虑去掉这个选项，但可能要改的地方很多，并且会出现新的错误 Unrecognized VM option 'UseParNewGC'。

JDK 8 下载 https://www.oracle.com/java/technologies/javase/javase8u211-later-archive-downloads.html

> .\bin\mqnamesrv.cmd
Java HotSpot(TM) 64-Bit Server VM warning: Using the DefNew young collector with the CMS collector is deprecated and will likely be removed in a future release
Java HotSpot(TM) 64-Bit Server VM warning: UseCMSCompactAtFullCollection is deprecated and will likely be removed in a future release.
The Name Server boot success. serializeType=JSON

查看环境变量 `set XXX`
临时设置环境变量 `set XXX=XXX`
临时追加环境变量 `set PATH=...;%PATH%`
(变量名不区分大小写)

留意 start call 执行命令的区别，简单理解 start 会启动新的窗口
```

启动 mqbroker

```shell
> .\bin\mqbroker.cmd -h
usage: mqbroker [-c <arg>] [-h] [-m] [-n <arg>] [-p]
 -c,--configFile <arg>       Broker config properties file
 -h,--help                   Print help
 -m,--printImportantConfig   Print important config item
 -n,--namesrvAddr <arg>      Name server address list, eg: '192.168.0.1:9876;192.168.0.2:9876'
 -p,--printConfigItem        Print all config item
"Broker starts OK"

> start .\bin\mqbroker.cmd --namesrvAddr localhost:9876
(新窗口)
The broker[BROKER_NAME, 192.168.1.103:10911] boot success. serializeType=JSON and name server is localhost:9876
```

输出 mqbroker 配置项

```shell
>.\bin\mqbroker.cmd --printConfigItem
2023-02-10 15:08:34 INFO main - brokerConfigPath=
2023-02-10 15:08:34 INFO main - rocketmqHome=E:\java-project\rocketmq-source-code-analysis\rocketmq-all-5.0.0-source-release\distribution\target\rocketmq-5.0.0\rocketmq-5.0.0
2023-02-10 15:08:34 INFO main - namesrvAddr=
2023-02-10 15:08:34 INFO main - listenPort=6888
2023-02-10 15:08:34 INFO main - brokerIP1=192.168.1.103
2023-02-10 15:08:34 INFO main - brokerIP2=192.168.1.103
2023-02-10 15:08:34 INFO main - brokerPermission=6
2023-02-10 15:08:34 INFO main - defaultTopicQueueNums=8
2023-02-10 15:08:34 INFO main - autoCreateTopicEnable=true
2023-02-10 15:08:34 INFO main - clusterTopicEnable=true
2023-02-10 15:08:34 INFO main - brokerTopicEnable=true
2023-02-10 15:08:34 INFO main - autoCreateSubscriptionGroup=true
2023-02-10 15:08:34 INFO main - messageStorePlugIn=
2023-02-10 15:08:34 INFO main - msgTraceTopicName=RMQ_SYS_TRACE_TOPIC
2023-02-10 15:08:34 INFO main - traceTopicEnable=false
2023-02-10 15:08:34 INFO main - sendMessageThreadPoolNums=4
2023-02-10 15:08:34 INFO main - putMessageFutureThreadPoolNums=4
2023-02-10 15:08:34 INFO main - pullMessageThreadPoolNums=40
2023-02-10 15:08:34 INFO main - litePullMessageThreadPoolNums=40
2023-02-10 15:08:34 INFO main - ackMessageThreadPoolNums=3
2023-02-10 15:08:34 INFO main - processReplyMessageThreadPoolNums=40
2023-02-10 15:08:34 INFO main - queryMessageThreadPoolNums=20
2023-02-10 15:08:34 INFO main - adminBrokerThreadPoolNums=16
2023-02-10 15:08:34 INFO main - clientManageThreadPoolNums=32
2023-02-10 15:08:34 INFO main - consumerManageThreadPoolNums=32
2023-02-10 15:08:34 INFO main - loadBalanceProcessorThreadPoolNums=32
2023-02-10 15:08:34 INFO main - heartbeatThreadPoolNums=12
2023-02-10 15:08:34 INFO main - endTransactionThreadPoolNums=32
2023-02-10 15:08:34 INFO main - flushConsumerOffsetInterval=5000
2023-02-10 15:08:34 INFO main - flushConsumerOffsetHistoryInterval=60000
2023-02-10 15:08:34 INFO main - rejectTransactionMessage=false
2023-02-10 15:08:34 INFO main - fetchNamesrvAddrByAddressServer=false
2023-02-10 15:08:34 INFO main - sendThreadPoolQueueCapacity=10000
2023-02-10 15:08:34 INFO main - putThreadPoolQueueCapacity=10000
2023-02-10 15:08:34 INFO main - pullThreadPoolQueueCapacity=100000
2023-02-10 15:08:34 INFO main - litePullThreadPoolQueueCapacity=100000
2023-02-10 15:08:34 INFO main - ackThreadPoolQueueCapacity=100000
2023-02-10 15:08:34 INFO main - replyThreadPoolQueueCapacity=10000
2023-02-10 15:08:34 INFO main - queryThreadPoolQueueCapacity=20000
2023-02-10 15:08:34 INFO main - clientManagerThreadPoolQueueCapacity=1000000
2023-02-10 15:08:34 INFO main - consumerManagerThreadPoolQueueCapacity=1000000
2023-02-10 15:08:34 INFO main - heartbeatThreadPoolQueueCapacity=50000
2023-02-10 15:08:34 INFO main - endTransactionPoolQueueCapacity=100000
2023-02-10 15:08:34 INFO main - adminBrokerThreadPoolQueueCapacity=10000
2023-02-10 15:08:34 INFO main - loadBalanceThreadPoolQueueCapacity=100000
2023-02-10 15:08:34 INFO main - filterServerNums=0
2023-02-10 15:08:34 INFO main - longPollingEnable=true
2023-02-10 15:08:34 INFO main - shortPollingTimeMills=1000
2023-02-10 15:08:34 INFO main - notifyConsumerIdsChangedEnable=true
2023-02-10 15:08:34 INFO main - highSpeedMode=false
2023-02-10 15:08:34 INFO main - commercialBaseCount=1
2023-02-10 15:08:34 INFO main - commercialSizePerMsg=4096
2023-02-10 15:08:34 INFO main - accountStatsEnable=true
2023-02-10 15:08:34 INFO main - accountStatsPrintZeroValues=true
2023-02-10 15:08:34 INFO main - transferMsgByHeap=true
2023-02-10 15:08:34 INFO main - maxDelayTime=40
2023-02-10 15:08:34 INFO main - regionId=DefaultRegion
2023-02-10 15:08:34 INFO main - registerBrokerTimeoutMills=24000
2023-02-10 15:08:34 INFO main - sendHeartbeatTimeoutMillis=1000
2023-02-10 15:08:34 INFO main - slaveReadEnable=false
2023-02-10 15:08:34 INFO main - disableConsumeIfConsumerReadSlowly=false
2023-02-10 15:08:34 INFO main - consumerFallbehindThreshold=17179869184
2023-02-10 15:08:34 INFO main - brokerFastFailureEnable=true
2023-02-10 15:08:34 INFO main - waitTimeMillsInSendQueue=200
2023-02-10 15:08:34 INFO main - waitTimeMillsInPullQueue=5000
2023-02-10 15:08:34 INFO main - waitTimeMillsInLitePullQueue=5000
2023-02-10 15:08:34 INFO main - waitTimeMillsInHeartbeatQueue=31000
2023-02-10 15:08:34 INFO main - waitTimeMillsInTransactionQueue=3000
2023-02-10 15:08:34 INFO main - waitTimeMillsInAckQueue=3000
2023-02-10 15:08:34 INFO main - startAcceptSendRequestTimeStamp=0
2023-02-10 15:08:34 INFO main - traceOn=true
2023-02-10 15:08:34 INFO main - enableCalcFilterBitMap=false
2023-02-10 15:08:34 INFO main - rejectPullConsumerEnable=false
2023-02-10 15:08:34 INFO main - expectConsumerNumUseFilter=32
2023-02-10 15:08:34 INFO main - maxErrorRateOfBloomFilter=20
2023-02-10 15:08:34 INFO main - filterDataCleanTimeSpan=86400000
2023-02-10 15:08:34 INFO main - filterSupportRetry=false
2023-02-10 15:08:34 INFO main - enablePropertyFilter=false
2023-02-10 15:08:34 INFO main - compressedRegister=false
2023-02-10 15:08:34 INFO main - forceRegister=true
2023-02-10 15:08:34 INFO main - registerNameServerPeriod=30000
2023-02-10 15:08:34 INFO main - brokerHeartbeatInterval=1000
2023-02-10 15:08:34 INFO main - brokerNotActiveTimeoutMillis=10000
2023-02-10 15:08:34 INFO main - enableNetWorkFlowControl=false
2023-02-10 15:08:34 INFO main - popPollingSize=1024
2023-02-10 15:08:34 INFO main - popPollingMapSize=100000
2023-02-10 15:08:34 INFO main - maxPopPollingSize=100000
2023-02-10 15:08:34 INFO main - reviveQueueNum=8
2023-02-10 15:08:34 INFO main - reviveInterval=1000
2023-02-10 15:08:34 INFO main - reviveMaxSlow=3
2023-02-10 15:08:34 INFO main - reviveScanTime=10000
2023-02-10 15:08:34 INFO main - enablePopLog=false
2023-02-10 15:08:34 INFO main - enablePopBufferMerge=false
2023-02-10 15:08:34 INFO main - popCkStayBufferTime=10000
2023-02-10 15:08:34 INFO main - popCkStayBufferTimeOut=3000
2023-02-10 15:08:34 INFO main - popCkMaxBufferSize=200000
2023-02-10 15:08:34 INFO main - popCkOffsetMaxQueueSize=20000
2023-02-10 15:08:34 INFO main - realTimeNotifyConsumerChange=true
2023-02-10 15:08:34 INFO main - litePullMessageEnable=true
2023-02-10 15:08:34 INFO main - syncBrokerMemberGroupPeriod=1000
2023-02-10 15:08:34 INFO main - loadBalancePollNameServerInterval=30000
2023-02-10 15:08:34 INFO main - cleanOfflineBrokerInterval=30000
2023-02-10 15:08:34 INFO main - serverLoadBalancerEnable=true
2023-02-10 15:08:34 INFO main - defaultMessageRequestMode=PULL
2023-02-10 15:08:34 INFO main - defaultPopShareQueueNum=-1
2023-02-10 15:08:34 INFO main - transactionTimeOut=6000
2023-02-10 15:08:34 INFO main - transactionCheckMax=15
2023-02-10 15:08:34 INFO main - transactionCheckInterval=60000
2023-02-10 15:08:34 INFO main - aclEnable=false
2023-02-10 15:08:34 INFO main - storeReplyMessageEnable=true
2023-02-10 15:08:34 INFO main - enableDetailStat=true
2023-02-10 15:08:34 INFO main - autoDeleteUnusedStats=false
2023-02-10 15:08:34 INFO main - isolateLogEnable=false
2023-02-10 15:08:34 INFO main - forwardTimeout=3000
2023-02-10 15:08:34 INFO main - enableSlaveActingMaster=false
2023-02-10 15:08:34 INFO main - enableRemoteEscape=false
2023-02-10 15:08:34 INFO main - skipPreOnline=false
2023-02-10 15:08:34 INFO main - asyncSendEnable=true
2023-02-10 15:08:34 INFO main - consumerOffsetUpdateVersionStep=500
2023-02-10 15:08:34 INFO main - delayOffsetUpdateVersionStep=200
2023-02-10 15:08:34 INFO main - lockInStrictMode=false
2023-02-10 15:08:34 INFO main - compatibleWithOldNameSrv=true
2023-02-10 15:08:34 INFO main - enableControllerMode=false
2023-02-10 15:08:34 INFO main - controllerAddr=
2023-02-10 15:08:34 INFO main - syncBrokerMetadataPeriod=5000
2023-02-10 15:08:34 INFO main - checkSyncStateSetPeriod=5000
2023-02-10 15:08:34 INFO main - syncControllerMetadataPeriod=10000
2023-02-10 15:08:34 INFO main - bindAddress=0.0.0.0
2023-02-10 15:08:34 INFO main - listenPort=10911
2023-02-10 15:08:34 INFO main - serverWorkerThreads=8
2023-02-10 15:08:34 INFO main - serverCallbackExecutorThreads=0
2023-02-10 15:08:34 INFO main - serverSelectorThreads=3
2023-02-10 15:08:34 INFO main - serverOnewaySemaphoreValue=256
2023-02-10 15:08:34 INFO main - serverAsyncSemaphoreValue=64
2023-02-10 15:08:34 INFO main - serverChannelMaxIdleTimeSeconds=120
2023-02-10 15:08:34 INFO main - serverSocketSndBufSize=0
2023-02-10 15:08:34 INFO main - serverSocketRcvBufSize=0
2023-02-10 15:08:34 INFO main - writeBufferHighWaterMark=0
2023-02-10 15:08:34 INFO main - writeBufferLowWaterMark=0
2023-02-10 15:08:34 INFO main - serverSocketBacklog=1024
2023-02-10 15:08:34 INFO main - serverPooledByteBufAllocatorEnable=true
2023-02-10 15:08:34 INFO main - useEpollNativeSelector=false
2023-02-10 15:08:34 INFO main - clientWorkerThreads=4
2023-02-10 15:08:34 INFO main - clientCallbackExecutorThreads=12
2023-02-10 15:08:34 INFO main - clientOnewaySemaphoreValue=65535
2023-02-10 15:08:34 INFO main - clientAsyncSemaphoreValue=65535
2023-02-10 15:08:34 INFO main - connectTimeoutMillis=3000
2023-02-10 15:08:34 INFO main - channelNotActiveInterval=60000
2023-02-10 15:08:34 INFO main - clientChannelMaxIdleTimeSeconds=120
2023-02-10 15:08:34 INFO main - clientSocketSndBufSize=0
2023-02-10 15:08:34 INFO main - clientSocketRcvBufSize=0
2023-02-10 15:08:34 INFO main - clientPooledByteBufAllocatorEnable=false
2023-02-10 15:08:34 INFO main - clientCloseSocketIfTimeout=true
2023-02-10 15:08:34 INFO main - useTLS=false
2023-02-10 15:08:34 INFO main - writeBufferHighWaterMark=0
2023-02-10 15:08:34 INFO main - writeBufferLowWaterMark=0
2023-02-10 15:08:34 INFO main - disableCallbackExecutor=false
2023-02-10 15:08:34 INFO main - disableNettyWorkerGroup=false
2023-02-10 15:08:34 INFO main - storePathRootDir=C:\Users\zhouh\store
2023-02-10 15:08:34 INFO main - storePathCommitLog=
2023-02-10 15:08:34 INFO main - storePathDLedgerCommitLog=
2023-02-10 15:08:34 INFO main - storePathEpochFile=C:\Users\zhouh\store\epochFileCheckpoint
2023-02-10 15:08:34 INFO main - readOnlyCommitLogStorePaths=
2023-02-10 15:08:34 INFO main - mappedFileSizeCommitLog=1073741824
2023-02-10 15:08:34 INFO main - mappedFileSizeTimerLog=104857600
2023-02-10 15:08:34 INFO main - timerPrecisionMs=1000
2023-02-10 15:08:34 INFO main - timerRollWindowSlot=172800
2023-02-10 15:08:34 INFO main - timerFlushIntervalMs=1000
2023-02-10 15:08:34 INFO main - timerGetMessageThreadNum=3
2023-02-10 15:08:34 INFO main - timerPutMessageThreadNum=3
2023-02-10 15:08:34 INFO main - timerEnableDisruptor=false
2023-02-10 15:08:34 INFO main - timerEnableCheckMetrics=true
2023-02-10 15:08:34 INFO main - timerInterceptDelayLevel=false
2023-02-10 15:08:34 INFO main - timerMaxDelaySec=259200
2023-02-10 15:08:34 INFO main - timerWheelEnable=true
2023-02-10 15:08:34 INFO main - disappearTimeAfterStart=-1
2023-02-10 15:08:34 INFO main - timerStopEnqueue=false
2023-02-10 15:08:34 INFO main - timerCheckMetricsWhen=05
2023-02-10 15:08:34 INFO main - timerSkipUnknownError=false
2023-02-10 15:08:34 INFO main - timerWarmEnable=false
2023-02-10 15:08:34 INFO main - timerStopDequeue=false
2023-02-10 15:08:34 INFO main - timerCongestNumEachSlot=2147483647
2023-02-10 15:08:34 INFO main - timerMetricSmallThreshold=1000000
2023-02-10 15:08:34 INFO main - timerProgressLogIntervalMs=10000
2023-02-10 15:08:34 INFO main - mappedFileSizeConsumeQueue=6000000
2023-02-10 15:08:34 INFO main - enableConsumeQueueExt=false
2023-02-10 15:08:34 INFO main - mappedFileSizeConsumeQueueExt=50331648
2023-02-10 15:08:34 INFO main - mapperFileSizeBatchConsumeQueue=13800000
2023-02-10 15:08:34 INFO main - bitMapLengthConsumeQueueExt=64
2023-02-10 15:08:34 INFO main - flushIntervalCommitLog=500
2023-02-10 15:08:34 INFO main - commitIntervalCommitLog=200
2023-02-10 15:08:34 INFO main - maxRecoveryCommitlogFiles=30
2023-02-10 15:08:34 INFO main - diskSpaceWarningLevelRatio=90
2023-02-10 15:08:34 INFO main - diskSpaceCleanForciblyRatio=85
2023-02-10 15:08:34 INFO main - useReentrantLockWhenPutMessage=true
2023-02-10 15:08:34 INFO main - flushCommitLogTimed=true
2023-02-10 15:08:34 INFO main - flushIntervalConsumeQueue=1000
2023-02-10 15:08:34 INFO main - cleanResourceInterval=10000
2023-02-10 15:08:34 INFO main - deleteCommitLogFilesInterval=100
2023-02-10 15:08:34 INFO main - deleteConsumeQueueFilesInterval=100
2023-02-10 15:08:34 INFO main - destroyMapedFileIntervalForcibly=120000
2023-02-10 15:08:34 INFO main - redeleteHangedFileInterval=120000
2023-02-10 15:08:34 INFO main - deleteWhen=04
2023-02-10 15:08:34 INFO main - diskMaxUsedSpaceRatio=75
2023-02-10 15:08:34 INFO main - fileReservedTime=72
2023-02-10 15:08:34 INFO main - deleteFileBatchMax=10
2023-02-10 15:08:34 INFO main - putMsgIndexHightWater=600000
2023-02-10 15:08:34 INFO main - maxMessageSize=4194304
2023-02-10 15:08:34 INFO main - checkCRCOnRecover=true
2023-02-10 15:08:34 INFO main - flushCommitLogLeastPages=4
2023-02-10 15:08:34 INFO main - commitCommitLogLeastPages=4
2023-02-10 15:08:34 INFO main - flushLeastPagesWhenWarmMapedFile=4096
2023-02-10 15:08:34 INFO main - flushConsumeQueueLeastPages=2
2023-02-10 15:08:34 INFO main - flushCommitLogThoroughInterval=10000
2023-02-10 15:08:34 INFO main - commitCommitLogThoroughInterval=200
2023-02-10 15:08:34 INFO main - flushConsumeQueueThoroughInterval=60000
2023-02-10 15:08:34 INFO main - maxTransferBytesOnMessageInMemory=262144
2023-02-10 15:08:34 INFO main - maxTransferCountOnMessageInMemory=32
2023-02-10 15:08:34 INFO main - maxTransferBytesOnMessageInDisk=65536
2023-02-10 15:08:34 INFO main - maxTransferCountOnMessageInDisk=8
2023-02-10 15:08:34 INFO main - accessMessageInMemoryMaxRatio=40
2023-02-10 15:08:34 INFO main - messageIndexEnable=true
2023-02-10 15:08:34 INFO main - maxHashSlotNum=5000000
2023-02-10 15:08:34 INFO main - maxIndexNum=20000000
2023-02-10 15:08:34 INFO main - maxMsgsNumBatch=64
2023-02-10 15:08:34 INFO main - messageIndexSafe=false
2023-02-10 15:08:34 INFO main - haListenPort=10912
2023-02-10 15:08:34 INFO main - haSendHeartbeatInterval=5000
2023-02-10 15:08:34 INFO main - haHousekeepingInterval=20000
2023-02-10 15:08:34 INFO main - haTransferBatchSize=32768
2023-02-10 15:08:34 INFO main - haMasterAddress=
2023-02-10 15:08:34 INFO main - haMaxGapNotInSync=268435456
2023-02-10 15:08:34 INFO main - brokerRole=ASYNC_MASTER
2023-02-10 15:08:34 INFO main - flushDiskType=ASYNC_FLUSH
2023-02-10 15:08:34 INFO main - syncFlushTimeout=5000
2023-02-10 15:08:34 INFO main - putMessageTimeout=8000
2023-02-10 15:08:34 INFO main - slaveTimeout=3000
2023-02-10 15:08:34 INFO main - messageDelayLevel=1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
2023-02-10 15:08:34 INFO main - flushDelayOffsetInterval=10000
2023-02-10 15:08:34 INFO main - cleanFileForciblyEnable=true
2023-02-10 15:08:34 INFO main - warmMapedFileEnable=false
2023-02-10 15:08:34 INFO main - offsetCheckInSlave=false
2023-02-10 15:08:34 INFO main - debugLockEnable=false
2023-02-10 15:08:34 INFO main - duplicationEnable=false
2023-02-10 15:08:34 INFO main - diskFallRecorded=true
2023-02-10 15:08:34 INFO main - osPageCacheBusyTimeOutMills=1000
2023-02-10 15:08:34 INFO main - defaultQueryMaxNum=32
2023-02-10 15:08:34 INFO main - transientStorePoolEnable=false
2023-02-10 15:08:34 INFO main - transientStorePoolSize=5
2023-02-10 15:08:34 INFO main - fastFailIfNoBufferInStorePool=false
2023-02-10 15:08:34 INFO main - enableDLegerCommitLog=false
2023-02-10 15:08:34 INFO main - dLegerGroup=
2023-02-10 15:08:34 INFO main - dLegerPeers=
2023-02-10 15:08:34 INFO main - dLegerSelfId=
2023-02-10 15:08:34 INFO main - preferredLeaderId=
2023-02-10 15:08:34 INFO main - isEnableBatchPush=false
2023-02-10 15:08:34 INFO main - enableScheduleMessageStats=true
2023-02-10 15:08:34 INFO main - enableLmq=false
2023-02-10 15:08:34 INFO main - enableMultiDispatch=false
2023-02-10 15:08:34 INFO main - maxLmqConsumeQueueNum=20000
2023-02-10 15:08:34 INFO main - enableScheduleAsyncDeliver=false
2023-02-10 15:08:34 INFO main - scheduleAsyncDeliverMaxPendingLimit=2000
2023-02-10 15:08:34 INFO main - scheduleAsyncDeliverMaxResendNum2Blocked=3
2023-02-10 15:08:34 INFO main - maxBatchDeleteFilesNum=50
2023-02-10 15:08:34 INFO main - dispatchCqThreads=10
2023-02-10 15:08:34 INFO main - dispatchCqCacheNum=4096
2023-02-10 15:08:34 INFO main - enableAsyncReput=true
2023-02-10 15:08:34 INFO main - recheckReputOffsetFromCq=false
2023-02-10 15:08:34 INFO main - maxTopicLength=1000
2023-02-10 15:08:34 INFO main - travelCqFileNumWhenGetMessage=1
2023-02-10 15:08:34 INFO main - correctLogicMinOffsetSleepInterval=1
2023-02-10 15:08:34 INFO main - correctLogicMinOffsetForceInterval=300000
2023-02-10 15:08:34 INFO main - mappedFileSwapEnable=true
2023-02-10 15:08:34 INFO main - commitLogForceSwapMapInterval=43200000
2023-02-10 15:08:34 INFO main - commitLogSwapMapInterval=3600000
2023-02-10 15:08:34 INFO main - commitLogSwapMapReserveFileNum=100
2023-02-10 15:08:34 INFO main - logicQueueForceSwapMapInterval=43200000
2023-02-10 15:08:34 INFO main - logicQueueSwapMapInterval=3600000
2023-02-10 15:08:34 INFO main - cleanSwapedMapInterval=300000
2023-02-10 15:08:34 INFO main - logicQueueSwapMapReserveFileNum=20
2023-02-10 15:08:34 INFO main - searchBcqByCacheEnable=true
2023-02-10 15:08:34 INFO main - dispatchFromSenderThread=false
2023-02-10 15:08:34 INFO main - wakeCommitWhenPutMessage=true
2023-02-10 15:08:34 INFO main - wakeFlushWhenPutMessage=false
2023-02-10 15:08:34 INFO main - enableCleanExpiredOffset=false
2023-02-10 15:08:34 INFO main - maxAsyncPutMessageRequests=5000
2023-02-10 15:08:34 INFO main - pullBatchMaxMessageCount=160
2023-02-10 15:08:34 INFO main - totalReplicas=1
2023-02-10 15:08:34 INFO main - inSyncReplicas=1
2023-02-10 15:08:34 INFO main - minInSyncReplicas=1
2023-02-10 15:08:34 INFO main - allAckInSyncStateSet=false
2023-02-10 15:08:34 INFO main - enableAutoInSyncReplicas=false
2023-02-10 15:08:34 INFO main - haFlowControlEnable=false
2023-02-10 15:08:34 INFO main - maxHaTransferByteInSecond=104857600
2023-02-10 15:08:34 INFO main - haMaxTimeSlaveNotCatchup=15000
2023-02-10 15:08:34 INFO main - syncMasterFlushOffsetWhenStartup=false
2023-02-10 15:08:34 INFO main - maxChecksumRange=1073741824
2023-02-10 15:08:34 INFO main - replicasPerDiskPartition=1
2023-02-10 15:08:34 INFO main - logicalDiskSpaceCleanForciblyThreshold=0.8
2023-02-10 15:08:34 INFO main - maxSlaveResendLength=268435456
2023-02-10 15:08:34 INFO main - syncFromLastFile=false
2023-02-10 15:08:34 INFO main - asyncLearner=false
"Broker starts OK"
```

## RocketMQ Dashboard

https://github.com/apache/rocketmq-dashboard

https://rocketmq.apache.org/zh/download#rocketmq-dashboard

旧版本源码是在rocketmq-external里的rocketmq-console，新版本已经单独拆分成rocketmq-dashboard

https://rocketmq.apache.org/zh/docs/deploymentOperations/04Dashboard

`mvn spring-boot:run`

推荐使用Jar包的方式启动

## Java 版本命名

例如 jdk 8u101

```text
jdk: Java development Kit
8: jdk版本，即是jdk 1.8
u: update 更新的意思
101: 更新次数，即 jdk 1.8 版本更新了 101 次了
```

![java-version-name-rule.png](readme/java-version-name-rule.png)

## 简单测试

```shell
$ export NAMESRV_ADDR=localhost:9876
$ sh bin/tools.sh org.apache.rocketmq.example.quickstart.Producer
SendResult [sendStatus=SEND_OK, msgId= ...
$ sh bin/tools.sh org.apache.rocketmq.example.quickstart.Consumer
ConsumeMessageThread_%d Receive New Messages: [MessageExt...

> set NAMESRV_ADDR=localhost:9876

> call bin\tools.cmd org.apache.rocketmq.example.quickstart.Producer
SendResult [sendStatus=SEND_OK, msgId=7F0000012D98214C265E31BFB8990000, offsetMsgId=C0A8016700002A9F000000000003ACFA, messageQueue=MessageQueue [topic=TopicTest, brokerName=BROKER_NAME, queueId=3], queueOffset=250]
SendResult [sendStatus=SEND_OK, msgId=7F0000012D98214C265E31BFB8A10001, offsetMsgId=C0A8016700002A9F000000000003ADE9, messageQueue=MessageQueue [topic=TopicTest, brokerName=BROKER_NAME, queueId=0], queueOffset=250]
SendResult [sendStatus=SEND_OK, msgId=7F0000012D98214C265E31BFB8A20002, offsetMsgId=C0A8016700002A9F000000000003AED8, messageQueue=MessageQueue [topic=TopicTest, brokerName=BROKER_NAME, queueId=1], queueOffset=250]
SendResult [sendStatus=SEND_OK, msgId=7F0000012D98214C265E31BFB8A30003, offsetMsgId=C0A8016700002A9F000000000003AFC7, messageQueue=MessageQueue [topic=TopicTest, brokerName=BROKER_NAME, queueId=2], queueOffset=250]
...

> call bin\tools.cmd org.apache.rocketmq.example.quickstart.Consumer
Consumer Started.
ConsumeMessageThread_please_rename_unique_group_name_4_7 Receive New Messages: [MessageExt [brokerName=BROKER_NAME, queueId=0, storeSize=239, queueOffset=767, sysFlag=0, bornTim
estamp=1676015545864, bornHost=/192.168.1.103:52747, storeTimestamp=1676015545864, storeHost=/192.168.1.103:10911, msgId=C0A8016700002A9F00000000000B4C1C, commitLogOffset=740
380, bodyCRC=988340972, reconsumeTimes=0, preparedTransactionOffset=0, toString()=Message{topic='TopicTest', flag=0, properties={MIN_OFFSET=0, TRACE_ON=true, MAX_OFFSET=862, 
MSG_REGION=DefaultRegion, CONSUME_START_TIME=1676015566432, UNIQ_KEY=7F0000010A24214C265E31C136080007, CLUSTER=DefaultCluster, WAIT=true, TAGS=TagA}, body=[72, 101, 108, 108, 111, 32, 82, 111, 99, 107, 101, 116, 77, 81, 32, 55], transactionId='null'}]]
ConsumeMessageThread_please_rename_unique_group_name_4_20 Receive New Messages: [MessageExt [brokerName=BROKER_NAME, queueId=3, storeSize=240, queueOffset=771, sysFlag=0, bornTi
mestamp=1676015545877, bornHost=/192.168.1.103:52747, storeTimestamp=1676015545877, storeHost=/192.168.1.103:10911, msgId=C0A8016700002A9F00000000000B5669, commitLogOffset=74
3017, bodyCRC=89962020, reconsumeTimes=0, preparedTransactionOffset=0, toString()=Message{topic='TopicTest', flag=0, properties={MIN_OFFSET=0, TRACE_ON=true, MAX_OFFSET=863, 
MSG_REGION=DefaultRegion, CONSUME_START_TIME=1676015566432, UNIQ_KEY=7F0000010A24214C265E31C136150012, CLUSTER=DefaultCluster, WAIT=true, TAGS=TagA}, body=[72, 101, 108, 108, 111, 32, 82, 111, 99, 107, 101, 116, 77, 81, 32, 49, 56], transactionId='null'}]]
ConsumeMessageThread_please_rename_unique_group_name_4_19 Receive New Messages: [MessageExt [brokerName=BROKER_NAME, queueId=1, storeSize=240, queueOffset=771, sysFlag=0, bornTi
mestamp=1676015545875, bornHost=/192.168.1.103:52747, storeTimestamp=1676015545875, storeHost=/192.168.1.103:10911, msgId=C0A8016700002A9F00000000000B5489, commitLogOffset=74
2537, bodyCRC=1659149091, reconsumeTimes=0, preparedTransactionOffset=0, toString()=Message{topic='TopicTest', flag=0, properties={MIN_OFFSET=0, TRACE_ON=true, MAX_OFFSET=863
, MSG_REGION=DefaultRegion, CONSUME_START_TIME=1676015566432, UNIQ_KEY=7F0000010A24214C265E31C136130010, CLUSTER=DefaultCluster, WAIT=true, TAGS=TagA}, body=[72, 101, 108, 108, 111, 32, 82, 111, 99, 107, 101, 116, 77, 81, 32, 49, 54], transactionId='null'}]]
...
(挂起，等待新的消息)

```
 
## RocketMQ Proxy

RocketMQ Proxy 是一个 RocketMQ Broker 的代理服务，支持客户端用 GRPC 协议访问 Broker

```shell
> start .\bin\mqproxy.cmd -n localhost:9876
(新窗口)
Java HotSpot(TM) 64-Bit Server VM warning: Using the DefNew young collector with the CMS collector is deprecated and will likely be removed in a future release
Java HotSpot(TM) 64-Bit Server VM warning: UseCMSCompactAtFullCollection is deprecated and will likely be removed in a future release.
Fri Feb 10 16:10:47 CST 2023 rocketmq-proxy startup successfully
```

## 通过 mqadmin 创建 topic

```shell
$ sh bin/mqadmin updatetopic -n localhost:9876 -t TestTopic -c DefaultCluster

> call bin\mqadmin.cmd updatetopic -n localhost:9876 -t TestTopic123 -c DefaultCluster
create topic to 192.168.1.103:10911 success.
TopicConfig [topicName=TestTopic123, readQueueNums=8, writeQueueNums=8, perm=RW-, topicFilterType=SINGLE_TAG, topicSysFlag=0, order=false, attributes={}]

```

## rocketmq-client-java 和 rocketmq-client

https://github.com/apache/rocketmq-clients

https://github.com/apache/rocketmq-clients/issues/325

> 欢迎关注 5.0 客户端，rocketmq-client-java 对应的客户端是 RocketMQ 社区推荐的新的 5.0 客户端，未来 RocketMQ 客户端新的 feature 和主力维护方向是在当前仓库进行的（包含以此衍生的所有多语言客户端），关于这一点，你可以在 RocketMQ 官网进行参阅。
rocketmq-client 对应的是 RocketMQ 旧有的客户端，沿袭 RocketMQ 的旧有设计和编码，原则上只做 bugfix，不再进行 feature 新增。

## Articles

+ 全面升级 —— Apache RocketMQ 5.0 SDK 的新面貌 https://developer.aliyun.com/article/797655
