package com.gmail.swanliu;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class CustomizableUnreliableMulticastSocketTest {
	/**
	 * testCustomizableUnreliableMulticastSocket
	 */
	@Test
	public void testCustomizableUnreliableMulticastSocket() throws Exception {
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
		final int packetNum = 2 << 12;
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
		int maxReport = 120;
		while (maxReport-- > 0) {
			double actualLostRate = (1.0 - receivedCount.get() * 1.0 / sentCount.get());

			System.out.println("receivedCount =[" + receivedCount + "]sentCount =[" + sentCount + "] lostRate[" + lostRate + "] actualLostRate[" + actualLostRate + "]");
			Thread.sleep(interval);
		}

	}
}
