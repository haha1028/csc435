/*--------------------------------------------------------

 1. Wentao Liu / 01/15/2014:

 2. Java version used:

 java version "1.7.0_25"
 Java(TM) SE Runtime Environment (build 1.7.0_25-b17)
 Java HotSpot(TM) 64-Bit Server VM (build 23.25-b01, mixed mode)

 3. Precise command-line compilation examples / instructions:

 > javac InetServer.java
 > javac InetClient.java

 4. Precise examples / instructions to run this program:

 MUST start server before you start client,because of the new feature, persistent connection. I talked about this in 6.a 

 In separate shell windows:
 First,
 > java InetServer
 Then,
 > java InetClient

 All acceptable commands are displayed on the various consoles.

 This runs across machines, in which case you have to pass the IP address of
 the server to the clients. For example, if the server is running at
 192.168.0.101 then you would type:

 > java InetClient 192.168.0.101


 5. List of files needed for running the program.

 a. checklist.html
 b. InetServer.java
 c. InetClient.java

 6. Notes:

 a. client will establish a persistent connection to server on startup, instead of re-connect socket each time.
 b. shutdown command will cause server to close all active connections between this and other clients to server.
 c. command of quit and shutdown  are changed to strictly equals "quit" and "shutdown", instead of just contains them.
 d. made a few changes to output text format, though most time they are still same, or very similar.

 ----------------------------------------------------------*/


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
// Get the Input Output libraries
// Get the Java networking libraries


/**
 * 
 * @author swanliu@gmail.com
 * 
 */
class Worker extends Thread { // Class definition

	String id; // worker uuid.
	Socket sock; // Class member, socket, local to Worker.
	InetServer inetServer; // reference to server instance.

	Worker(String id, Socket s, InetServer inetServer) {
		this.id = id;
		sock = s;
		this.inetServer = inetServer;
	} // Constructor, assign arg s to local sock

	public void run() {

		try {

			while (true) {
				try {
					// Get I/O streams in/out from the socket:
					BufferedReader in = new BufferedReader(
							new InputStreamReader(sock.getInputStream()));
					PrintStream out = new PrintStream(sock.getOutputStream());
					/**
					 * test sock not closed.
					 */
					if (sock.isClosed()) {
						System.out.println("Sock is closed by client.");
						break;
					}

					/**
					 * read client user-input from socket
					 */
					String name = in.readLine();
					// this could happen if client closed socket
					if (name == null) {
						break;
					}

					/**
					 * deal with shutdown command. shutdown condition changed to
					 * strict equals shutdown, not contains.
					 */
					if (name.equals("shutdown")) {

						System.out
								.println("Worker has captured a shutdown request.");
						out.println("Shutdown request has been noted by worker.");

						/**
						 * this will stop the server socket accept anymore
						 * client connection.
						 */
						inetServer.controlSwitch = false;
						inetServer.close();

						break;
					}

					/**
					 * print total connect clients. as we use long connection.
					 */
					System.out.println("Total " + inetServer.getWorkers()
							+ " connected clients,Looking up " + name);
					printRemoteAddress(name, out);

					// sock.close(); // use long connection, do not close inside
					// loop.
				} catch (IOException ioe) {
					System.out.println("Server read error");
					ioe.printStackTrace();
					break;
				}
			}
			System.out.println("Exit worker with "
					+ sock.getRemoteSocketAddress() + ".");
		} finally {
			close();
		}

	}

	/**
	 * close socket, notify inetServer.
	 */
	public void close() {
		try {
			sock.close();
		} catch (IOException e) {
		}
		inetServer.workerFinsh(this);
	}

	/**
	 * 
	 * @param name
	 *            host name to solve.
	 * @param out
	 *            output result to.
	 * @return resolved inetaddress by name, or null if not found.
	 */
	private static InetAddress printRemoteAddress(String name, PrintStream out) {
		/*
		 * print init response line to client.
		 */
		out.println("Looking up " + name + "...");

		try {
			/**
			 * do look up by name.
			 */
			InetAddress machine = InetAddress.getByName(name);
			/**
			 * send response. if error ever happened ,will go catch block.
			 */
			out.println("Host name : " + machine.getHostName());
			out.println("Host IP : " + (machine.getHostAddress()));
			return machine;
		} catch (UnknownHostException ex) {
			out.println("UnknownHost");
			out.println("Failed in atempt to look up " + name);
			return null;
		}

	}

}

/**
 * 
 * @author swanliu@gmail.com
 * 
 */
public class InetServer {

	/**
	 * socket config
	 */
	private int q_len = 6; /* Number of requests for OpSys to queue */
	private int port;
	private ServerSocket servsock; // listening server socket

	volatile boolean controlSwitch = true; // if shutdown or not.

	/**
	 * number of workers, and workers
	 */
	private AtomicInteger numberOfCurrentActiveWorkers = new AtomicInteger(0);
	private Map<String, Worker> mapOfWorker = new java.util.concurrent.ConcurrentHashMap<String, Worker>();

	/**
	 * do nothing but store port. call open to start listen.
	 * 
	 * @param port
	 *            port to listen
	 */
	public InetServer(int port) {
		this.port = port;
	}

	/**
	 * Spawn worker to handle it
	 * 
	 * @param sock
	 *            socket to handle.
	 */
	protected void createWorker(Socket sock) {

		String uuid = java.util.UUID.randomUUID().toString();
		Worker worker = new Worker(uuid, sock, this);
		mapOfWorker.put(uuid, worker);
		worker.start(); // Spawn worker to handle it
		numberOfCurrentActiveWorkers.incrementAndGet();
	}

	/**
	 * remove this finished worker from active workers collections.
	 * 
	 * @param worker
	 *            finished worker.
	 */
	protected void workerFinsh(Worker worker) {
		mapOfWorker.remove(worker.id);
		numberOfCurrentActiveWorkers.decrementAndGet();
	}

	/**
	 * open server, start listen.
	 * 
	 * @throws IOException
	 */
	public void open() throws IOException {

		/**
		 * open server socket at port.
		 */
		servsock = new ServerSocket(port, q_len);

		System.out
				.println("Swan Liu's Inet server starting up, listening at port "
						+ port + ".\n");
		/**
		 * listening while not shutdown.
		 */
		while (controlSwitch) {
			Socket sock = servsock.accept(); // wait for the next client

			/**
			 * Spawn worker to handle it
			 */
			createWorker(sock);
		}

	}

	/**
	 * close server. By close serverSocket, it will cause thread blocked on
	 * serversock.accept() throw SocketException and continue.
	 * 
	 * This method will call by some worker who received shutdown request
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {

		try {
			servsock.close();
		} catch (IOException e) {

		}
		/**
		 * shutdown other active clients.
		 */
		for (Worker worker : mapOfWorker.values()) {

			System.out.println("close work [" + worker.id + "]");
			worker.close();
		}
	}

	/**
	 * 
	 * @return number of current active worker.
	 */
	public int getWorkers() {
		return numberOfCurrentActiveWorkers.get();
	}

	public static void main(String a[]) throws IOException {

		int port = 11565;

		InetServer inetServer = new InetServer(port);
		inetServer.open();
	}
}