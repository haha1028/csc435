import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 
 *  jdk7 required.
 * 
 * @author swanliu@gmail.com
 * 
 */
public class TotOrdered {

	/**
	 * Customizable Unreliable MulticastSocket. *
	 * <P>
	 * DatagramPacket send to this CustomizableUnreliableMulticastSocket first
	 * have a lostRate of chance to be dropped from being sent. if that datagram
	 * passed the random check, statistically after avgDelay, it will be sent to
	 * underlying socket.
	 * 
	 * @author swanliu@gmail.com
	 * 
	 */
	static final class CustomizableUnreliableMulticastSocket extends MulticastSocket {
		/**
		 * an troubler that randomly drop task , then randomly delay task
		 * execution.
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
		 *            statistically after avgDelay sent datagram to underlying
		 *            socket.
		 * @throws IOException
		 */
		public CustomizableUnreliableMulticastSocket(int port, double lostRate, int avgDelay) throws IOException {
			super(port);
			this.troubler = new UnreliableScheduledThreadPoolExecutor(lostRate, avgDelay);
		}

		/**
		 * synchronously send DatagramPacket. wait until sent.
		 * <P>
		 * Note: because this call is synchronous, if you didn't call this
		 * function concurrently, the order of sent packet would be exactly same
		 * as the caller sequence.
		 * <P>
		 * To simulate random send sequence , either concurrent call this
		 * function or use asynchronously sendAndGetFuture instead.
		 * <P>
		 * first compute against lostRate. if didn't pass, will be ignored.
		 * <P>
		 * if passed, statistically after avgDelay, it will be sent to
		 * underlying socket
		 * 
		 */
		@Override
		public void send(final DatagramPacket p) throws IOException {
			ScheduledFuture<IOException> future = sendAndGetFuture(p);
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
		 * asynchronously send packet, and return a ScheduledFuture reference to
		 * the action.
		 * <P>
		 * random send sequence. not the order of calling sequence.
		 * 
		 * @return delayed result-bearing action ,that can be cancelled. read
		 *         about ScheduledFuture for more information.
		 *         <P>
		 *         Note: CustomizableUnreliableMulticastSocket is not aware of
		 *         this future object.
		 *         <P>
		 *         So if you use this returned future object to call
		 *         cancel(true) to cancel task,
		 *         CustomizableUnreliableMulticastSocket will not be able to
		 *         keep accurate lostRate or avgDelay.
		 *         <P>
		 *         if you use this returned future and get its result,send
		 *         sequence become orderly again.
		 */
		public ScheduledFuture<IOException> sendAndGetFuture(final DatagramPacket p) {

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
		// testUnreliableScheduledThreadPoolExecutor(args);
		testUnreliableMultiCast(args);
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
	 * an Executor that randomly drop task , then randomly delay task execution.
	 * <P>
	 * task submitted to this Executor first have a lostRate of chance to be
	 * dropped from being executed.
	 * <P>
	 * if that task passed the random check, statistically after avgDelay, it
	 * will be executed.
	 * <P>
	 * 
	 * @author swanliu@gmail.com
	 * 
	 */
	public static final class UnreliableScheduledThreadPoolExecutor {

		/**
		 * double precision.
		 */
		private double esp = 0.0000001;

		/**
		 * chance of drop task. 0.2= 20% chance.
		 */
		private double lostRate = 0;

		/**
		 * avgDelay to actually call task.
		 */
		private int avgDelay = 0;
		/**
		 * implementation internal use. maxDelay =2*avgDelay
		 */
		private int maxDelay = 0;

		/**
		 * actual pool to exec task. poolSize set to 4 because most machine are
		 * quad core.
		 */
		private int poolSize = 4;
		private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(poolSize);

		final AtomicLong finishTaskCount = new AtomicLong();
		final AtomicLong totalDelayedTime = new AtomicLong();
		final AtomicLong failedTaskCount = new AtomicLong();

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
		 *            task are guaranteed will executed within twice of avgDelay
		 *            time
		 */
		public UnreliableScheduledThreadPoolExecutor(double lostRate, int avgDelay) {

			this.avgDelay = avgDelay;
			this.maxDelay = avgDelay * 2;
			this.lostRate = lostRate;
		}

		/**
		 * Initiates an orderly shutdown in which previously submitted tasks are
		 * executed, but no new tasks will be accepted. Blocks until all tasks
		 * have completed execution after a shutdown request, or the timeout
		 * occurs, or the current thread is interrupted, whichever happens
		 * first.
		 * 
		 */
		public void shutdownAndAwaitTermination() {
			scheduledThreadPoolExecutor.shutdown();
			try {
				long timeout = 1000;
				scheduledThreadPoolExecutor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public int getAvgDelay() {
			return avgDelay;
		}

		public double getLostRate() {
			return lostRate;
		}

		/**
		 * 
		 * @return tasks already executed. does not contain those have been
		 *         dropped.
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
		 * @return number of task that are pending.
		 */
		public int getQueueSize() {
			return scheduledThreadPoolExecutor.getQueue().size();
		}

		/**
		 * 
		 * submit a task to exec. this task will be executed statistically after
		 * avgDelay and have a lostRate of chance to be dropped from being
		 * executed
		 * 
		 * @param task
		 *            function to exec .
		 *            <P>
		 *            task are guaranteed will executed within twice of avgDelay
		 *            time if was not dropped.
		 * @return ScheduledFuture that can be used to extract result or cancel.
		 *         <P>
		 *         Note: UnreliableScheduledThreadPoolExecutor is not aware of
		 *         this future object.
		 *         <P>
		 *         So if you use this returned future object to call
		 *         cancel(true) to cancel task,
		 *         UnreliableScheduledThreadPoolExecutor will not be able to
		 *         keep accurate lostRate or avgDelay.
		 */
		public <T> ScheduledFuture<T> submit(final Callable<T> task) {

			double lost = ThreadLocalRandom.current().nextDouble();
			if ((lost - lostRate) < esp) {
				failedTaskCount.incrementAndGet();
				return null;
			}

			long delay = 0;
			if (maxDelay > 0) {
				delay = ThreadLocalRandom.current().nextInt(maxDelay);
			}

			final long scheduledSendTime = delay + System.currentTimeMillis();
			ScheduledFuture<T> future = scheduledThreadPoolExecutor.schedule(new Callable<T>() {
				@Override
				public T call() throws Exception {
					long now = System.currentTimeMillis();
					long delta = now - scheduledSendTime;

					finishTaskCount.incrementAndGet();
					totalDelayedTime.addAndGet(delta);
					T t = task.call();
					return t;
				}
			}, delay, TimeUnit.MILLISECONDS);

			return future;
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
		int avgDelay = 50;
		final UnreliableScheduledThreadPoolExecutor pool = new UnreliableScheduledThreadPoolExecutor(lostRate, avgDelay);

		class Receiver {
			AtomicInteger count = new AtomicInteger();
			AtomicLong delayedTime = new AtomicLong();

			public void receive(long orignalTime) {
				count.incrementAndGet();
				delayedTime.addAndGet(System.currentTimeMillis() - orignalTime);
			}
		}
		final Receiver receiver = new Receiver();

		int nThreads = 8;
		final int eachThreadTask = 2 << 18;
		final int N = nThreads * eachThreadTask;

		long cost = System.currentTimeMillis();

		ExecutorService service = Executors.newFixedThreadPool(nThreads);
		for (int i = 0; i < nThreads; i++) {
			Future<?> future = service.submit(new Runnable() {

				@Override
				public void run() {
					for (int i = 0; i < eachThreadTask; i++) {
						pool.submit(new Callable<Void>() {
							private long orignalTime = System.currentTimeMillis();

							@Override
							public Void call() throws Exception {
								receiver.receive(orignalTime);
								return null;
							}

						});
					}
				}

			});
			future.get();
		}
		service.shutdown();

		pool.shutdownAndAwaitTermination();
		double actualLostRate = (N - receiver.count.get()) / (N * 1.0);
		double actualAvgDelay = receiver.delayedTime.get() * 1.0 / receiver.count.get();

		System.out.println("UnreliableScheduledThreadPoolExecutor: actualLostRate=[" + actualLostRate + "] expectLostRate=[" + lostRate + "] actualAvgDelay=[" + actualAvgDelay
				+ "] expectAvgDelay=[" + avgDelay + "]");
		cost = System.currentTimeMillis() - cost;
		// System.out.println("cost=[" + cost + "]");
	}
}
