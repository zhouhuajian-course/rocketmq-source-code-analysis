
package org.apache.rocketmq.example.quickstart;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;

import java.io.UnsupportedEncodingException;
import java.util.List;

public class ConsumerTest {

    public static void main(String[] args) throws MQClientException {
        // 创建默认MQPush消费者
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("MonkeyConsumerGroup");
        // 消费者设置namesrc地址
        consumer.setNamesrvAddr("localhost:9876");
        // 消费者 设置从哪里消费
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // 消费者 订阅 主题 子表达式
        consumer.subscribe("FruitTopic", "*");
        // concurrently
        // 消费者 注册消息监听器 消息监听器并发 消息 上下文
        // consumer.registerMessageListener((MessageListenerConcurrently) (msg, context) -> {
        //     // 线程 当前线程 获取名字 消息
        //     System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msg);
        //     System.out.println(new String(msg.get(0).getBody(), "UTF-8"));
        //     // 返回 消费并发状态 消费成功
        //     return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        // });
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) throws UnsupportedEncodingException {
                System.out.println(Thread.currentThread().getName());
                System.out.println(msgs);
                for (MessageExt msg : msgs) {
                    System.out.println(new String(msgs.get(0).getBody(), "UTF-8"));
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        // 消费者 启动
        consumer.start();
        System.out.printf("Consumer Started.%n");
    }
}
