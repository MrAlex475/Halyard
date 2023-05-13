package com.msd.gin.halyard.strategy;

public final class StrategyConfig {

	public static final String HALYARD_EVALUATION_HASH_JOIN_LIMIT = "halyard.evaluation.hashJoin.limit";
	public static final String HALYARD_EVALUATION_HASH_JOIN_COST_RATIO = "halyard.evaluation.hashJoin.costRatio";
	public static final String HALYARD_EVALUATION_STAR_JOIN_MIN_JOINS = "halyard.evaluation.starJoin.minJoins";
	public static final String HALYARD_EVALUATION_MEMORY_THRESHOLD = "halyard.evaluation.collections.memoryThreshold";
	public static final String HALYARD_EVALUATION_VALUE_CACHE_SIZE = "halyard.evaluation.valueCache.size";
	public static final String HALYARD_EVALUATION_POLL_TIMEOUT_MILLIS = "halyard.evaluation.pollTimeoutMillis";
	public static final String HALYARD_EVALUATION_OFFER_TIMEOUT_MILLIS = "halyard.evaluation.offerTimeoutMillis";
	public static final String HALYARD_EVALUATION_MAX_QUEUE_SIZE = "halyard.evaluation.maxQueueSize";
	public static final String HALYARD_EVALUATION_MAX_THREADS = "halyard.evaluation.maxThreads";
	public static final String HALYARD_EVALUATION_THREAD_GAIN = "halyard.evaluation.threadGain";
	public static final String HALYARD_EVALUATION_RETRY_LIMIT = "halyard.evaluation.retryLimit";
	public static final String HALYARD_EVALUATION_MAX_RETRIES = "halyard.evaluation.maxRetries";
	public static final String HALYARD_EVALUATION_THREADS = "halyard.evaluation.threads";
	public static final String HALYARD_EVALUATION_MIN_TASK_RATE = "halyard.evaluation.taskRate.min";
	public static final String HALYARD_EVALUATION_TASK_RATE_UPDATE_MILLIS = "halyard.evaluation.taskRate.updateMillis";
	public static final String HALYARD_EVALUATION_TASK_RATE_WINDOW_SIZE = "halyard.evaluation.taskRate.windowSize";
	public static final String HALYARD_EVALUATION_THREAD_POOL_CHECK_PERIOD_SECS = "halyard.evaluation.threadPoolCheckPeriodSecs";

	static final int DEFAULT_HASH_JOIN_LIMIT = 50000;
	static final int DEFAULT_STAR_JOIN_MIN_JOINS = 3;
	static final int DEFAULT_MEMORY_THRESHOLD = 100000;
	static final int DEFAULT_VALUE_CACHE_SIZE = 1000;
	public static final String JMX_DOMAIN = "com.msd.gin.halyard";
}
