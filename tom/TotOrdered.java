import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 * jdk7 required.
 * 
 * @author swanliu@gmail.com
 * 
 */
public class TotOrdered {

	/**
	 * Customizable Unreliable MulticastSocket. *
	 * <P>
	 * DatagramPacket send to this CustomizableUnreliableMulticastSocket first have a lostRate of chance to be dropped from being sent. if that datagram passed the random check,
	 * statistically after avgDelay, it will be sent to underlying socket.
	 * 
	 * @author swanliu@gmail.com
	 * 
	 */
	static final class CustomizableUnreliableMulticastSocket extends MulticastSocket {
		/**
		 * an troubler that randomly drop task , then randomly delay task execution.
		 */
		UnreliableScheduledThreadPoolExecutor troubler;

		/**
		 * 
		 * @param port
		 *            socket port
		 * 
		 * @param lostRate
		 *            chance of datagram to be dropped from being sent
		 * @param avgDelay
		 *            statistically after avgDelay sent datagram to underlying socket.
		 * @throws IOException
		 */
		public CustomizableUnreliableMulticastSocket(int port, double lostRate, int avgDelay) throws IOException {
			super(port);
			this.troubler = new UnreliableScheduledThreadPoolExecutor(lostRate, avgDelay, TimeUnit.MILLISECONDS);
		}

		/**
		 * synchronously send DatagramPacket. wait until sent.
		 * <P>
		 * Note: because this call is synchronous, if you didn't call this function concurrently, the order of sent packet would be exactly same as the caller sequence.
		 * <P>
		 * To simulate random send sequence , either concurrent call this function or use asynchronously sendAndGetFuture instead.
		 * <P>
		 * first compute against lostRate. if didn't pass, will be ignored.
		 * <P>
		 * if passed, statistically after avgDelay, it will be sent to underlying socket
		 * 
		 */
		@Override
		public void send(final DatagramPacket p) throws IOException {
			Future<IOException> future = sendAndGetFuture(p);
			if (future != null) {

				try {
					IOException e = future.get();
					if (e != null) {
						throw e;
					}
				} catch (InterruptedException | ExecutionException e1) {
					throw new IOException(e1);
				}

			}
		}

		/**
		 * 
		 * asynchronously send packet, and return a ScheduledFuture reference to the action.
		 * <P>
		 * random send sequence. not the order of calling sequence.
		 * 
		 * @return delayed result-bearing action ,that can be cancelled. read about ScheduledFuture for more information.
		 *         <P>
		 *         Note: CustomizableUnreliableMulticastSocket is not aware of this future object.
		 *         <P>
		 *         So if you use this returned future object to call cancel(true) to cancel task, CustomizableUnreliableMulticastSocket will not be able to keep accurate lostRate
		 *         or avgDelay.
		 *         <P>
		 *         if you use this returned future and get its result,send sequence become orderly again.
		 */
		public Future<IOException> sendAndGetFuture(final DatagramPacket p) {

			return troubler.submit(new Callable<IOException>() {

				@Override
				public IOException call() {
					try {
						actualSend(p);
					} catch (IOException e) {
						return e;
					}
					return null;
				}

			});
		}

		/**
		 * actually send p to socket.
		 */
		private void actualSend(final DatagramPacket p) throws IOException {
			super.send(p);
		}

		/**
		 * an UnsupportedOperationException is always thrown.
		 */
		@Override
		public void setTTL(byte ttl) throws IOException {
			throw new java.lang.UnsupportedOperationException();
		}

		/**
		 * an UnsupportedOperationException is always thrown.
		 */
		@Override
		public byte getTTL() throws IOException {
			throw new java.lang.UnsupportedOperationException();
		}

		/**
		 * an UnsupportedOperationException is always thrown.
		 */
		@Override
		public void send(DatagramPacket p, byte ttl) throws IOException {
			throw new java.lang.UnsupportedOperationException();
		}

	}

	public static void main(String[] args) throws Exception {
		while (true) {
			testUnreliableScheduledThreadPoolExecutor(args);
		}
		// testUnreliableMultiCast(args);
	}

	public static void testUnreliableMultiCast(String[] args) throws Exception {
		double lostRate = 0.1;
		int avgDelay = 25;
		final int sendDelay = 5;
		final int port = 9011;
		final long interval = 1500;

		final InetAddress mcastaddr = InetAddress.getByName("224.0.0.0");

		final CustomizableUnreliableMulticastSocket unreliable = new CustomizableUnreliableMulticastSocket(port, lostRate, avgDelay);
		unreliable.joinGroup(mcastaddr);
		unreliable.setTimeToLive(0);

		final AtomicLong sentCount = new AtomicLong();
		final AtomicLong receivedCount = new AtomicLong();
		final CountDownLatch startSignal = new CountDownLatch(1);
		final int packetNum = 2 << 8;
		final InetAddress local = InetAddress.getLocalHost();
		Executors.newSingleThreadExecutor().submit(new Runnable() {

			@Override
			public void run() {
				try {

					startSignal.await();

					int senTPacketNum = packetNum;
					while (senTPacketNum-- > 0) {
						byte[] data = String.valueOf(senTPacketNum).getBytes();
						DatagramPacket packet = new DatagramPacket(data, data.length, local, port);

						unreliable.sendAndGetFuture(packet);
						sentCount.incrementAndGet();
						Thread.sleep(sendDelay);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}

			}

		});

		Executors.newSingleThreadExecutor().submit(new Runnable() {

			@Override
			public void run() {

				try {
					startSignal.countDown();
					int receivedPacketNum = packetNum;
					while (receivedPacketNum-- > 0) {
						byte[] data = new byte[32];
						DatagramPacket receivedPacket = new DatagramPacket(data, data.length, local, port);
						try {

							unreliable.receive(receivedPacket);
							String content = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
							System.out.println("received sequence [" + receivedPacketNum + "]  get data [" + content + "]");

							receivedCount.incrementAndGet();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					unreliable.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		});
		int maxReport = 3;
		while (maxReport-- > 0) {
			double actualLostRate = (1.0 - receivedCount.get() * 1.0 / sentCount.get());

			System.out.println("receivedCount =[" + receivedCount + "]sentCount =[" + sentCount + "] lostRate[" + lostRate + "] actualLostRate[" + actualLostRate + "]");
			Thread.sleep(interval);
		}

	}

	/**
	 * an ExecutorService that randomly drop task , then randomly delay task execution.
	 * <P>
	 * task submitted to this Executor first have a lostRate of chance to be dropped from being executed.
	 * <P>
	 * if that task passed the random check, statistically after avgDelay, it will be executed.
	 * <P>
	 * <P>
	 * task are expected be executed within twice of avgDelay time, but this is not guaranteed. if task exec is very slow or too many task were scheduled, the actual avg dealy
	 * could be much longer than expected.
	 * <P>
	 * It is caller's responsibility to check run time status to ensure the executor is running as expected
	 * 
	 * @author swanliu@gmail.com
	 * 
	 */
	public static final class UnreliableScheduledThreadPoolExecutor extends AbstractExecutorService implements ExecutorService {

		/**
		 * double precision.
		 */
		private double esp = 0.00000001;

		/**
		 * chance of drop task. 0.2= 20% chance.
		 */
		private double lostRate = 0;

		/**
		 * avgDelay to actually call task.
		 */
		private int avgDelay = 0;

		/**
		 * time unit to delay.
		 */
		private TimeUnit delayUnit = TimeUnit.MILLISECONDS;
		/**
		 * implementation internal use. maxDelay =2*avgDelay in milliseconds
		 */
		private long maxDelay = 0;

		/**
		 * actual pool to exec task.
		 */
		private int poolSize = Runtime.getRuntime().availableProcessors();;
		/**
		 * workers.
		 */
		ScheduledThreadPoolExecutor[] scheduledThreadPoolExecutors = new ScheduledThreadPoolExecutor[poolSize];

		final AtomicLong finishTaskCount = new AtomicLong();
		final AtomicLong totalDelayedTime = new AtomicLong();
		final AtomicLong failedTaskCount = new AtomicLong();

		/**
		 * Lock held on access to workers set and related bookkeeping.
		 */
		private final ReentrantLock mainLock = new ReentrantLock();
		/**
		 * Wait condition to support awaitTermination
		 */
		private final Condition termination = mainLock.newCondition();

		private boolean TERMINATED = false;
		private boolean SHUTDOWN = false;

		/**
		 * default avg dealy and default lostRate.
		 */
		public UnreliableScheduledThreadPoolExecutor() {

		}

		/**
		 * 
		 * 
		 * @param lostRate
		 *            chance of drop task. 0.2= 20% chance
		 * @param avgDelay
		 *            . avgDelay to actually call task.
		 *            <P>
		 *            task are expected be executed within twice of avgDelay time, but this is not guaranteed. if task exec is very slow or too many task were scheduled, the actual
		 *            avg dealy could be much longer than expected.
		 *            <P>
		 *            It is caller's responsibility to check totalDelayedTime to ensure the Executor is running as expected
		 */
		public UnreliableScheduledThreadPoolExecutor(double lostRate, int avgDelay, TimeUnit delayUnit) {

			this.avgDelay = avgDelay;
			this.maxDelay = delayUnit.toMillis(avgDelay) * 2;
			this.delayUnit = delayUnit;
			this.lostRate = lostRate;
			for (int i = 0; i < scheduledThreadPoolExecutors.length; i++) {
				ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(poolSize) {
					protected void terminated() {
						mainLock.lock();
						try {
							termination.signalAll();

						} finally {
							mainLock.unlock();
						}
						super.terminated();
					}
				};
				scheduledThreadPoolExecutors[i] = scheduledThreadPoolExecutor;
			}
		}

		/**
		 * 
		 * @return user expect avgDelay
		 */
		public int getAvgDelay() {
			return avgDelay;
		}

		/**
		 * 
		 * @return user expect avgDelay TimeUnit
		 */
		public TimeUnit getDelayUnit() {
			return delayUnit;
		}

		/**
		 * 
		 * @return user expect lostRate
		 */
		public double getLostRate() {
			return lostRate;
		}

		/**
		 * 
		 * @return tasks already executed. does not contain those have been dropped.
		 */
		public long getFinishTaskCount() {
			return finishTaskCount.get();
		}

		/**
		 * @return total delayed time for already executed tasks
		 */
		public long getTotalDelayedTime() {
			return totalDelayedTime.get();
		}

		/**
		 * @return number of task those have been dropped
		 */
		public long getFailedTaskCount() {
			return failedTaskCount.get();
		}

		/**
		 * @return approximate number of task that are pending.
		 */
		public int getQueueSize() {

			final ReentrantLock mainLock = this.mainLock;
			mainLock.lock();
			try {
				int size = 0;
				for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : scheduledThreadPoolExecutors) {
					size += scheduledThreadPoolExecutor.getQueue().size();

				}
				return size;
			} finally {
				mainLock.unlock();
			}

		}

		/**
		 * 
		 * submit a task to exec. this task will be executed statistically after avgDelay and have a lostRate of chance to be dropped from being executed
		 * 
		 * @param task
		 *            function to exec .
		 *            <P>
		 *            task are usually will executed within twice of avgDelay time if was not dropped.
		 * @return ScheduledFuture that can be used to extract result or cancel.
		 *         <P>
		 *         Note: UnreliableScheduledThreadPoolExecutor is not aware of this future object.
		 *         <P>
		 *         So if you use this returned future object to call cancel(true) to cancel task, UnreliableScheduledThreadPoolExecutor will not be able to keep accurate lostRate
		 *         or avgDelay.
		 */
		@Override
		public <T> ScheduledFuture<T> submit(final Callable<T> task) {
			long delay = this.checkAndGetDelay();
			if (delay < 0) {
				return null;
			}
			final long scheduledAt = System.currentTimeMillis();

			Callable<T> callable = new Callable<T>() {
				@Override
				public T call() throws Exception {
					statDelay(scheduledAt);
					T t = task.call();
					return t;
				}
			};
			ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = pickScheduledThreadPoolExecutor(delay);
			return scheduledThreadPoolExecutor.schedule(callable, delay, TimeUnit.MILLISECONDS);

		}

		/**
		 * @see #submit
		 */
		@Override
		public void execute(final Runnable command) {
			submit(command, null);
		}

		/**
		 * @see #submit
		 */
		@Override
		public <T> Future<T> submit(final Runnable task, final T result) {
			final long delay = this.checkAndGetDelay();
			if (delay < 0) {
				return null;
			}
			final long scheduledAt = System.currentTimeMillis();
			final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = pickScheduledThreadPoolExecutor(delay);
			Callable<T> callable = new Callable<T>() {
				@Override
				public T call() throws Exception {
					statDelay(scheduledAt);

					scheduledThreadPoolExecutor.execute(task);
					return result;
				}

			};
			return scheduledThreadPoolExecutor.schedule(callable, delay, TimeUnit.MILLISECONDS);

		}

		/**
		 * an UnsupportedOperationException is always thrown.
		 */
		@Override
		public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
			throw new java.lang.UnsupportedOperationException("NOT yet implemented .");

		}

		/**
		 * an UnsupportedOperationException is always thrown.
		 */
		@Override
		public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
			throw new java.lang.UnsupportedOperationException("NOT yet implemented .");
		}

		/**
		 * an UnsupportedOperationException is always thrown.
		 */
		@Override
		public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
			throw new java.lang.UnsupportedOperationException("NOT yet implemented .");
		}

		/**
		 * an UnsupportedOperationException is always thrown.
		 */
		@Override
		public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			throw new java.lang.UnsupportedOperationException("NOT yet implemented .");
		}

		/**
		 * stat finish task and total delay upon task exectution.
		 * 
		 * @param scheduledAt
		 */
		private void statDelay(final long scheduledAt) {

			long now = System.currentTimeMillis();
			long delta = now - scheduledAt;
			finishTaskCount.incrementAndGet();
			totalDelayedTime.addAndGet(delta);
		}

		/**
		 * check whether task should be send or not.
		 * 
		 * @return -1 if don't need to exec. else the delay to use.
		 * @throws InterruptedException
		 */
		long checkAndGetDelay() {
			long delay = 0;
			double lost = ThreadLocalRandom.current().nextDouble();

			if ((lost - lostRate) < esp) {
				failedTaskCount.incrementAndGet();
				delay = -1;
			} else {
				if (maxDelay > 0) {
					delay = ThreadLocalRandom.current().nextLong(maxDelay);
					
					/**
					 * adjust delay time according to lag.
					 */
					long lag = getLag();
					delay = delay - lag / 2;

				}

			}
			return delay;
		}

		/**
		 * 
		 * @return current delay - expect delay.
		 */
		private long getLag() {
			long lag = (this.getTotalDelayedTime() / (this.getFinishTaskCount() + 1)) - this.maxDelay / 2;
			return lag;
		}

		ScheduledThreadPoolExecutor pickScheduledThreadPoolExecutor(long scheduledSendTime) {
			int index = (int) (scheduledSendTime % scheduledThreadPoolExecutors.length);
			return scheduledThreadPoolExecutors[index];

		}

		@Override
		public void shutdown() {

			final ReentrantLock mainLock = this.mainLock;
			mainLock.lock();
			try {

				for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : scheduledThreadPoolExecutors) {
					scheduledThreadPoolExecutor.shutdown();
				}
				SHUTDOWN = true;

			} finally {
				mainLock.unlock();
			}

		}

		@Override
		public List<Runnable> shutdownNow() {
			final ReentrantLock mainLock = this.mainLock;
			mainLock.lock();
			try {
				List<Runnable> tasks = new ArrayList<Runnable>();
				for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : scheduledThreadPoolExecutors) {
					List<Runnable> subTasks = scheduledThreadPoolExecutor.shutdownNow();
					tasks.addAll(subTasks);
				}
				SHUTDOWN = true;
				return tasks;
			} finally {
				mainLock.unlock();
			}
		}

		@Override
		public boolean isShutdown() {

			final ReentrantLock mainLock = this.mainLock;
			mainLock.lock();
			try {

				return SHUTDOWN;

			} finally {
				mainLock.unlock();
			}
		}

		@Override
		public boolean isTerminated() {
			final ReentrantLock mainLock = this.mainLock;
			mainLock.lock();
			try {

				return TERMINATED;
			} finally {
				mainLock.unlock();
			}
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {

			long nanos = unit.toNanos(timeout);
			final ReentrantLock mainLock = this.mainLock;
			mainLock.lock();
			try {
				for (;;) {
					if (TERMINATED)
						return true;
					boolean poolTerminated = true;
					for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : scheduledThreadPoolExecutors) {
						poolTerminated = poolTerminated && scheduledThreadPoolExecutor.isTerminated();
						if (!poolTerminated) {
							break;
						}
					}
					if (poolTerminated) {
						TERMINATED = true;
						return true;
					}
					if (nanos <= 0)
						return false;
					nanos = termination.awaitNanos(nanos);
				}
			} finally {
				mainLock.unlock();
			}
		}
	}

	/**
	 * Test UnreliableScheduledThreadPoolExecutor
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void testUnreliableScheduledThreadPoolExecutor(String[] args) throws Exception {
		double lostRate = 0.2;
		int avgDelay = 1;
		TimeUnit timeUnit = TimeUnit.SECONDS;
		final UnreliableScheduledThreadPoolExecutor pool = new UnreliableScheduledThreadPoolExecutor(lostRate, avgDelay, timeUnit);

		class Receiver {
			AtomicInteger count = new AtomicInteger();
			AtomicLong delayedTime = new AtomicLong();

			public void receive(long orignalTime) {

				count.incrementAndGet();
				delayedTime.addAndGet(System.currentTimeMillis() - orignalTime);
			}
		}
		final Receiver receiver = new Receiver();

		int nThreads = 256;
		final int eachThreadTask = 2 << 17;
		final int N = nThreads * eachThreadTask;

		long cost = System.currentTimeMillis();
		final CountDownLatch endSignal = new CountDownLatch(nThreads);
		final CountDownLatch startSignal = new CountDownLatch(1);
		int poolSize = 4;
		ExecutorService service = Executors.newFixedThreadPool(poolSize);
		for (int i = 0; i < nThreads; i++) {
			service.submit(new Runnable() {

				@Override
				public void run() {
					try {
						startSignal.await();
						for (int i = 0; i < eachThreadTask; i++) {

							final long orignalTime = System.currentTimeMillis();
							pool.submit(new Callable<Void>() {
								@Override
								public Void call() throws Exception {
									receiver.receive(orignalTime);
									return null;
								}

							});
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
				while (true) {

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
		service.shutdown();
		pool.shutdown();
		pool.awaitTermination(30, TimeUnit.SECONDS);

		report.interrupt();
		double actualLostRate = (N - receiver.count.get()) / (N * 1.0);
		double actualAvgDelay = receiver.delayedTime.get() * 1.0 / receiver.count.get();
		System.out.println("UnreliableScheduledThreadPoolExecutor: N=[" + N + "] receiver.count=[" + receiver.count + "]  pool.getFinishTaskCount=[" + pool.getFinishTaskCount()
				+ "] pool.getFailedTaskCount=[" + pool.getFailedTaskCount() + "]pool.getQueueSize=[" + pool.getQueueSize() + "]");
		System.out.println("UnreliableScheduledThreadPoolExecutor: actualLostRate=[" + actualLostRate + "] expectLostRate=[" + lostRate + "] actualAvgDelay=[" + actualAvgDelay
				+ "] expectAvgDelay=[" + timeUnit.toMillis(avgDelay) + "]");
		cost = System.currentTimeMillis() - cost;
		System.out.println("cost=[" + cost + "]");
	}
}
