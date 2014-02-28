package com.gmail.swanliu;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Test;

public class UnreliableScheduledThreadPoolExecutorTest {

	int poolSize = Runtime.getRuntime().availableProcessors();
	boolean debug = false;

	/**
	 * keep record of number of successfully called task , and their delay time before call.
	 * 
	 */
	class Receiver {
		AtomicInteger count = new AtomicInteger();
		AtomicLong delayedTime = new AtomicLong();
		int executionTime = 0;

		/**
		 * 
		 * @param orignalTime
		 *            the time that task was submit to pool.
		 * @throws InterruptedException
		 */
		public void receive(long orignalTime) throws InterruptedException {
			if (executionTime > 0) {
				Thread.sleep(executionTime);
			}
			count.incrementAndGet();
			delayedTime.addAndGet(System.currentTimeMillis() - orignalTime);

		}
	}

	/**
	 * testVeryLowLostAndDelay
	 */
	@Test
	public void testVeryLowLostAndDelay() throws Exception {
		double lostRate = 0.01;
		int avgDelay = 5;
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;
		int nThreads = 256;
		final int eachThreadTask = 2 << 14;
		testPool(lostRate, avgDelay, timeUnit, nThreads, eachThreadTask);

	}

	/**
	 * testLowLostAndDelay
	 */
	@Test
	public void testLowLostAndDelay() throws Exception {
		double lostRate = 0.1;
		int avgDelay = 25;
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;
		int nThreads = 256;
		final int eachThreadTask = 2 << 14;
		testPool(lostRate, avgDelay, timeUnit, nThreads, eachThreadTask);
	}

	/**
	 * testHighLostAndDelay
	 */
	@Test
	public void testHighLostAndDelay() throws Exception {
		double lostRate = 0.8;
		int avgDelay = 4;
		TimeUnit timeUnit = TimeUnit.SECONDS;
		int nThreads = 12;
		final int eachThreadTask = 2 << 14;
		testPool(lostRate, avgDelay, timeUnit, nThreads, eachThreadTask);
	}

	/**
	 * testHighTaskExecutionTime
	 */
	@Test
	public void testHighTaskExecutionTime() throws Exception {
		double lostRate = 0.1;
		int avgDelay = 140;
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;
		int nThreads = 8;
		final int eachThreadTask = 2 << 10;

		int repeat = 5;
		while (repeat-- > 0) {
			final Receiver receiver = new Receiver();
			receiver.executionTime = 40;
			testPool(receiver, lostRate, avgDelay, timeUnit, nThreads, eachThreadTask);
		}
	}

	private void testPool(final Receiver receiver, double lostRate, int avgDelay, TimeUnit timeUnit, int nThreads, final int eachThreadTask) throws InterruptedException {
		final UnreliableScheduledThreadPoolExecutor pool = new UnreliableScheduledThreadPoolExecutor(lostRate, avgDelay, timeUnit);

		final ExecutorService submitTaskToPoolService = Executors.newFixedThreadPool(poolSize);

		final AtomicLong submittedCount = new AtomicLong();
		final CountDownLatch endSignal = new CountDownLatch(nThreads);
		final CountDownLatch startSignal = new CountDownLatch(1);

		for (int i = 0; i < nThreads; i++) {
			submitTaskToPoolService.submit(new Runnable() {

				@Override
				public void run() {
					try {
						startSignal.await();
						for (int i = 0; i < eachThreadTask; i++) {

							final long orignalTime = System.currentTimeMillis();
							pool.submit(new Callable<Exception>() {
								@Override
								public Exception call() throws Exception {
									receiver.receive(orignalTime);
									return null;

								}

							});
							submittedCount.incrementAndGet();

						}
						endSignal.countDown();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			});

		}
		startSignal.countDown();

		final int interval = 1000;
		Thread report = new Thread(new Runnable() {
			public void run() {
				while (!pool.isTerminated()) {

					try {
						Thread.sleep(interval);
					} catch (InterruptedException e) {
						break;
					}
					if (debug) {
						System.out.println("queueSize =[" + pool.getQueueSize() + "]  currentDelay =[" + (pool.getTotalDelayedTime() / (pool.getFinishTaskCount() + 1))
								+ "] currentLag= [" + pool.getLag() + "] actualLostRate =["
								+ (1.0 * pool.getFailedTaskCount() / (pool.getFinishTaskCount() + pool.getFailedTaskCount())) + "]");
					}

				}
			}
		});

		report.start();

		endSignal.await();
		/**
		 * all task submitted
		 */
		submitTaskToPoolService.shutdown();
		pool.shutdown();
		pool.awaitTermination(1, TimeUnit.MINUTES);

		report.interrupt();

		final int N = nThreads * eachThreadTask;
		assertPoolBenchmarkStatus(lostRate, avgDelay, timeUnit, N, submittedCount, receiver, pool);
	}

	private void testPool(double lostRate, int avgDelay, TimeUnit timeUnit, int nThreads, final int eachThreadTask) throws InterruptedException {
		final Receiver receiver = new Receiver();
		testPool(receiver, lostRate, avgDelay, timeUnit, nThreads, eachThreadTask);
	}

	private void assertPoolBenchmarkStatus(double lostRate, int avgDelay, TimeUnit timeUnit, final int N, final AtomicLong submittedCount, final Receiver receiver,
			final UnreliableScheduledThreadPoolExecutor pool) {

		Assert.assertTrue("pool not shutdown", pool.isShutdown());
		Assert.assertTrue("pool not terminated", pool.isTerminated());

		Assert.assertEquals("not all task finished. adjust await time then run test again.", 0, pool.getQueueSize());
		Assert.assertEquals("not all task submitted", N, submittedCount.get());

		long finished = pool.getFinishTaskCount();
		long failed = pool.getFailedTaskCount();

		Assert.assertEquals("lost finished task", receiver.count.get(), finished);

		Assert.assertEquals("lost submited task", N, finished + failed);

		double actualLostRate = (N - receiver.count.get()) / (N * 1.0);
		double actualAvgDelay = receiver.delayedTime.get() * 1.0 / receiver.count.get();
		double expectAvgDelay = timeUnit.toMillis(avgDelay);

		double lostRateRelatedError = Math.abs(actualLostRate / lostRate);
		double delayRelatedError = Math.abs(actualAvgDelay / expectAvgDelay);

		double delayAbsError = Math.abs(actualAvgDelay - expectAvgDelay);

		System.out.println("assertPoolBenchmarkStatus queueSize =[" + pool.getQueueSize() + "]  actualAvgDelay =[" + actualAvgDelay + "] currentLag= [" + pool.getLag()
				+ "] actualLostRate =[" + actualLostRate + "]expectAvgDelay =[" + expectAvgDelay + "] rateError =[" + lostRateRelatedError + "]delayError =[" + delayRelatedError
				+ "]");

		double minRelatedError = 0.95;
		double maxRelatedError = 1.05;

		Assert.assertTrue("lost rate  not good [" + lostRateRelatedError + "]", lostRateRelatedError > minRelatedError && lostRateRelatedError < maxRelatedError);

		long lag = pool.getLag();
		if (lag == 0) {
			int maxDelayAbsError = 5;
			Assert.assertTrue("delay not good [" + delayRelatedError + "]", delayAbsError < maxDelayAbsError
					|| (delayRelatedError > minRelatedError && delayRelatedError < maxRelatedError));
		}

	}

	@Test
	public void testJavaRandom() {
		Random lostRandom = new Random(System.currentTimeMillis());
		int N = 1000000;
		int k = 5;
		int M = k * N;
		int j = 0;
		while (M-- > 0) {
			double next = lostRandom.nextDouble();
			if (next < 1.0 / k) {
				j++;
			}

		}
		double precision = 0.01;
		double error = (1.0 * j - N) / N;
		Assert.assertTrue("error=" + error, Math.abs(error) < precision);
	}
}
