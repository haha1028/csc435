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

	static class Receiver {
		AtomicInteger count = new AtomicInteger();
		AtomicLong delayedTime = new AtomicLong();

		public void receive(long orignalTime) {

			count.incrementAndGet();
			delayedTime.addAndGet(System.currentTimeMillis() - orignalTime);

		}
	}

	/**
	 * run UnreliableScheduledThreadPoolExecutor
	 * 
	 */
	@Test
	public void testVeryLowLostAndDelay() throws Exception {
		double lostRate = 0.01;
		int avgDelay = 5;
		int poolSize = 4;
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;

		int nThreads = 256;

		final int eachThreadTask = 2 << 14;
		final int N = nThreads * eachThreadTask;

		final AtomicLong submittedCount = new AtomicLong();

		final Receiver receiver = new Receiver();

		final UnreliableScheduledThreadPoolExecutor pool = testPool(lostRate, avgDelay, poolSize, timeUnit, nThreads, eachThreadTask, submittedCount, receiver);

		assertPool(lostRate, avgDelay, timeUnit, N, submittedCount, receiver, pool);

	}

	/**
	 * run UnreliableScheduledThreadPoolExecutor
	 * 
	 */
	@Test
	public void testLowLostAndDelay() throws Exception {
		double lostRate = 0.1;
		int avgDelay = 25;
		int poolSize = 4;
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;

		int nThreads = 256;

		final int eachThreadTask = 2 << 14;
		final int N = nThreads * eachThreadTask;

		final AtomicLong submittedCount = new AtomicLong();

		final Receiver receiver = new Receiver();

		final UnreliableScheduledThreadPoolExecutor pool = testPool(lostRate, avgDelay, poolSize, timeUnit, nThreads, eachThreadTask, submittedCount, receiver);

		assertPool(lostRate, avgDelay, timeUnit, N, submittedCount, receiver, pool);

	}

	/**
	 * run UnreliableScheduledThreadPoolExecutor
	 * 
	 */
	@Test
	public void testHighLostAndDelay() throws Exception {
		double lostRate = 0.8;
		int avgDelay = 4;
		int poolSize = 4;
		TimeUnit timeUnit = TimeUnit.SECONDS;

		int nThreads = 256;

		final int eachThreadTask = 2 << 14;
		final int N = nThreads * eachThreadTask;

		final AtomicLong submittedCount = new AtomicLong();

		final Receiver receiver = new Receiver();

		final UnreliableScheduledThreadPoolExecutor pool = testPool(lostRate, avgDelay, poolSize, timeUnit, nThreads, eachThreadTask, submittedCount, receiver);
		assertPool(lostRate, avgDelay, timeUnit, N, submittedCount, receiver, pool);

	}

	private void assertPool(double lostRate, int avgDelay, TimeUnit timeUnit, final int N, final AtomicLong submittedCount, final Receiver receiver,
			final UnreliableScheduledThreadPoolExecutor pool) {
		long finished = pool.getFinishTaskCount();
		long failed = pool.getFailedTaskCount();
		double actualLostRate = (N - receiver.count.get()) / (N * 1.0);
		double actualAvgDelay = receiver.delayedTime.get() * 1.0 / receiver.count.get();

		double rateError = Math.abs(actualLostRate / lostRate - 1);
		double delayError = Math.abs(actualAvgDelay / timeUnit.toMillis(avgDelay) - 1);

		System.out.println("UnreliableScheduledThreadPoolExecutor: N=[" + N + "] receiver.count=[" + receiver.count + "]  pool.getFinishTaskCount=[" + pool.getFinishTaskCount()
				+ "] pool.getFailedTaskCount=[" + pool.getFailedTaskCount() + "]pool.getQueueSize=[" + pool.getQueueSize() + "]");
		System.out.println("UnreliableScheduledThreadPoolExecutor: actualLostRate=[" + actualLostRate + "] expectLostRate=[" + lostRate + "] actualAvgDelay=[" + actualAvgDelay
				+ "] expectAvgDelay=[" + timeUnit.toMillis(avgDelay) + "] rateError=[" + rateError + "] delayError=[" + delayError + "]");

		Assert.assertEquals(receiver.count.get(), pool.getFinishTaskCount());

		Assert.assertEquals(N, submittedCount.get());

		Assert.assertEquals(N, finished + failed);

		Assert.assertEquals(0, pool.getQueueSize());
		double precision = 0.05;

		Assert.assertTrue("lost rate  not good [" + rateError + "]", rateError < precision);
		Assert.assertTrue("delay not good [" + delayError + "]", avgDelay - actualAvgDelay < 1 || delayError < precision);
	}

	private UnreliableScheduledThreadPoolExecutor testPool(double lostRate, int avgDelay, int poolSize, TimeUnit timeUnit, int nThreads, final int eachThreadTask,
			final AtomicLong submittedCount, final Receiver receiver) throws InterruptedException {
		final CountDownLatch endSignal = new CountDownLatch(nThreads);
		final CountDownLatch startSignal = new CountDownLatch(1);

		final ExecutorService submitTaskToPoolService = Executors.newFixedThreadPool(poolSize);

		final UnreliableScheduledThreadPoolExecutor pool = new UnreliableScheduledThreadPoolExecutor(lostRate, avgDelay, timeUnit);

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

					double currentDelay = pool.getTotalDelayedTime() / (pool.getFinishTaskCount() + 1);
					System.out.println("currentDelay =[" + currentDelay + "] currentLag= [" + pool.getLag() + "]");
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
		pool.awaitTermination(30, TimeUnit.SECONDS);

		report.interrupt();
		return pool;
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
