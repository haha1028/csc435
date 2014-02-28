package com.gmail.swanliu;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Customizable Unreliable MulticastSocket. *
 * <P>
 * DatagramPacket send to this CustomizableUnreliableMulticastSocket first have
 * a lostRate of chance to be dropped from being sent. if that datagram passed
 * the random check, statistically after avgDelay, it will be sent to underlying
 * socket.
 * 
 * @author Wentao Liu
 * 
 */
final class CustomizableUnreliableMulticastSocket extends MulticastSocket {
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
	 *            statistically after avgDelay sent datagram to underlying
	 *            socket.
	 * @throws IOException
	 */
	public CustomizableUnreliableMulticastSocket(int port, double lostRate, int avgDelay) throws IOException {
		super(port);
		this.troubler = new UnreliableScheduledThreadPoolExecutor(lostRate, avgDelay, TimeUnit.MILLISECONDS);
	}

	/**
	 * synchronously send DatagramPacket. wait until sent.
	 * <P>
	 * Note: because this call is synchronous, if you didn't call this function
	 * concurrently, the order of sent packet would be exactly same as the
	 * caller sequence.
	 * <P>
	 * To simulate random send sequence , either concurrent call this function
	 * or use asynchronously sendAndGetFuture instead.
	 * <P>
	 * first compute against lostRate. if didn't pass, will be ignored.
	 * <P>
	 * if passed, statistically after avgDelay, it will be sent to underlying
	 * socket
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
	 * asynchronously send packet, and return a ScheduledFuture reference to the
	 * action.
	 * <P>
	 * random send sequence. not the order of calling sequence.
	 * 
	 * @return delayed result-bearing action ,that can be cancelled. read about
	 *         ScheduledFuture for more information.
	 *         <P>
	 *         Note: CustomizableUnreliableMulticastSocket is not aware of this
	 *         future object.
	 *         <P>
	 *         So if you use this returned future object to call cancel(true) to
	 *         cancel task, CustomizableUnreliableMulticastSocket will not be
	 *         able to keep accurate lostRate or avgDelay.
	 *         <P>
	 *         if you use this returned future and get its result,send sequence
	 *         become orderly again.
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
	
	/**
	 * run UnreliableMultiCast 
	 * @param args
	 * @throws Exception
	 */
	public static void runUnreliableMultiCast(String[] args) throws Exception {
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
}