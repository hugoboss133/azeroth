package cn.com.warlock.kafka.consumer;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.com.warlock.kafka.handler.MessageHandler;
import cn.com.warlock.kafka.message.DefaultMessage;
import cn.com.warlock.kafka.thread.StandardThreadExecutor.StandardThreadFactory;

/**
 * 消费者端处理错误消息重试处理器
 */
public class ErrorMessageDefaultProcessor implements Closeable{
	
	private static final Logger logger = LoggerFactory.getLogger(ErrorMessageDefaultProcessor.class);
	
	//重试时间间隔单元（毫秒）
	private static final long RETRY_PERIOD_UNIT = 15 * 1000;

	private final PriorityBlockingQueue<PriorityTask> taskQueue = new PriorityBlockingQueue<PriorityTask>(1000);  
	
	private ExecutorService executor;
	
	private AtomicBoolean closed = new AtomicBoolean(false);
	
	public ErrorMessageDefaultProcessor() {
		this(1);
	}

	public ErrorMessageDefaultProcessor(int poolSize) {
		executor = Executors.newFixedThreadPool(poolSize, new StandardThreadFactory("ErrorMessageProcessor"));
		executor.submit(new Runnable() {
			@Override
			public void run() {
				while(!closed.get()){
					try {
						PriorityTask task = taskQueue.take();
						//空任务跳出循环
						if(task.getMessage() == null)break;
						if(task.nextFireTime - System.currentTimeMillis() > 0){
							TimeUnit.MILLISECONDS.sleep(1000);
							taskQueue.put(task);
							continue;
						}
						task.run();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});
	}

	public void submit(final DefaultMessage message,final MessageHandler messageHandler){
		int taskCount;
		if((taskCount = taskQueue.size()) > 1000){
			logger.warn("ErrorMessageProcessor queue task count over:{}",taskCount);
		}
		taskQueue.add(new PriorityTask(message, messageHandler));
	}
	
	public void close(){
		closed.set(true);
		//taskQueue里面没有任务会一直阻塞，所以先add一个新任务保证执行
		taskQueue.add(new PriorityTask(null, null));
		try {Thread.sleep(1000);} catch (Exception e) {}
		executor.shutdown();
		logger.info("ErrorMessageDefaultProcessor closed");
	}
	
	class PriorityTask implements Runnable,Comparable<PriorityTask>{

		final DefaultMessage message;
		final MessageHandler messageHandler;
		
		int retryCount = 0;
	    long nextFireTime;
		
	    public PriorityTask(DefaultMessage message, MessageHandler messageHandler) {
	    	this(message, messageHandler, System.currentTimeMillis() + RETRY_PERIOD_UNIT);
	    }
	    
		public PriorityTask(DefaultMessage message, MessageHandler messageHandler,long nextFireTime) {
			super();
			this.message = message;
			this.messageHandler = messageHandler;
			this.nextFireTime = nextFireTime;
		}

		public DefaultMessage getMessage() {
			return message;
		}

		@Override
		public void run() {
			try {	
				logger.debug("begin re-process message:"+message.getMsgId());
				messageHandler.p2Process(message);
			} catch (Exception e) {
				retryCount++;
				logger.warn("retry[{}] mssageId[{}] error",retryCount,message.getMsgId());
				retry();
			}
		}
		
		private void retry(){
			if(retryCount == 3){
				logger.warn("retry_skip mssageId[{}] retry over {} time error ,skip!!!");
				return;
			}
			nextFireTime = nextFireTime + retryCount * RETRY_PERIOD_UNIT;
			//重新放入任务队列
			taskQueue.add(this);
			logger.debug("re-submit mssageId[{}] task to queue,next fireTime:{}",this.message.getMsgId(),nextFireTime);
		}

		@Override
		public int compareTo(PriorityTask o) {
			return (int) (this.nextFireTime - o.nextFireTime);
		}

		@Override
		public String toString() {
			return "PriorityTask [message=" + message.getMsgId() + ", messageHandler=" + messageHandler.getClass().getSimpleName() + ", retryCount="
					+ retryCount + ", nextFireTime=" + nextFireTime + "]";
		}
		
	}

}

