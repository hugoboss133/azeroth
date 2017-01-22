package cn.com.warlock.kafka.utils;

/**
 * 常量定义
 */
public class KafkaConst {

	public final static String HEARTBEAT_TOPIC = "_kafka_heartBeat";
	
	public final static String ZK_CONSUMER_PATH = "/consumers/";
	
	public final static String ZK_PRODUCER_STAT_PATH = "/producers/statistics";
	
	public final static String PROP_PROCESS_THREADS = "consumer.default.process.threads";
	
	public final static String COMMAND_GET_STATISTICS = "get_statistics";
	
	public final static String PROP_MONITOR_ENABLE = "kafka.monitor.enable";
	
	public final static String PROP_TOPIC_LAT_THRESHOLD = "topic.lat.threshold";

}
