package com.github.gaohongf.mq;

/**
 * Kafka 话题名。
 *
 * <h2>为什么这里只放话题名，不像 RedisChannels 那样区分"信号/数据"</h2>
 * Redis pub/sub 那边传的是"某某变了"的信号（发后即忘，接收方自己去重拉）。
 * Kafka 这边传的是<b>数据本身</b>（服务上报的端点清单），因为要的就是持久化 ——
 * service-auth 不在时消息要留得住。语义不同，所以是两个类。
 */
public final class KafkaTopics {

    /**
     * 服务上报自己需要权限的端点。
     * <p>
     * 消费者是 service-auth（单消费组，同一条消息只需要被处理一次 —— 登记是幂等的，
     * 重复消费也无害，但没必要）。
     * <p>
     * 消息体是 {@code RegisterServiceAuth} 的 JSON。刻意不带 Kafka 的类型头
     * （用 String 序列化而不是 JsonSerializer）：带了之后消息里会写上类的全限定名，
     * 将来类一改包名，历史消息就再也反序列化不了了，而这类消息恰恰是要留一段时间的。
     */
    public static final String SERVICE_REGISTRATION = "oanm.service.registration";

    private KafkaTopics() {
    }
}
