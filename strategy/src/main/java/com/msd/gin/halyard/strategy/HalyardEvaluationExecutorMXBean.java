package com.msd.gin.halyard.strategy;

public interface HalyardEvaluationExecutorMXBean {
	void setMaxQueueSize(int size);
	int getMaxQueueSize();

	void setQueuePollTimeoutMillis(int millis);
	int getQueuePollTimeoutMillis();

	float getIncomingBindingsRatePerSecond();
	float getOutgoingBindingsRatePerSecond();

	TrackingThreadPoolExecutorMXBean getThreadPoolExecutor();
}
