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
 d. made a few changes to output text format, most time they are same, or very similar.
 
 ----------------------------------------------------------*/


import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries


/**
 * 
 * @author swanliu@gmail.com
 * @version 0.2
 */
public class InetClient {

	/**
	 * a named field storing the command to quit. this made the code more
	 * readable.
	 */
	private static final String QUIT_COMMAND = "quit";
	/**
	 * server socket port to connect.
	 */
	private static final int SERVER_PORT = 11565;
	/**
	 * client version.
	 */
	private static final String VERSION = "0.1";

	public static void main(String args[]) throws Exception {

		// default server name to connect
		String serverName = "localhost";

		// read optional serverName param from args if there is args.
		if (args.length > 0) {
			serverName = args[0];
		}

		// print configuration information
		printConfiguration(serverName);

		/**
		 * we need to close resources after use them, so declared two named
		 * object, and auto close them by jdk7.
		 */
		try (InputStreamReader inputStreamReader = new InputStreamReader(
				System.in);
				BufferedReader bufferedReader = new BufferedReader(
						inputStreamReader);) {
			/**
			 * create connection to server .I do not want reconnect socket each
			 * time, so I re-use it.
			 */
			Socket server = new Socket(serverName, SERVER_PORT);

			// continue do job until break by user's quit command
			while (true) {
				System.out.print("Enter a hostname or an IP address, ("
						+ QUIT_COMMAND + ") to end: ");
				System.out.flush();

				String hostName = bufferedReader.readLine();
				/**
				 * validate input.must be not null and not empty.
				 */
				if (hostName == null || hostName.isEmpty()) {
					System.out.println("query host must not be null or empty");
					continue;
				}

				/**
				 * More strict condition to quit. Must exactly equals quit
				 * command to quit, other than only contains quit command in the
				 * host name.
				 */

				if (hostName.equals(QUIT_COMMAND)) {
					System.out.println("Cancelled by user request.");
					// break is to quit here.
					break;
				}
				// didn't quit. so do query and print.
				getRemoteAddress(hostName, server);

			}
		} catch (IOException x) {
			System.out.println("Socket error. Client Terminated");
			x.printStackTrace();
		}
	}

	/**
	 * print configuration
	 * 
	 * @param serverName
	 *            server to use.
	 */
	private static void printConfiguration(String serverName) {
		System.out.println("Wentao Liu's Inet Client " + VERSION + ".\n");
		System.out.println("Using server: " + serverName + ", Port: "
				+ SERVER_PORT + "");
	}

	private static void getRemoteAddress(String hostName, Socket server)
			throws IOException {

		try {
			/**
			 * Create filter I/O streams for the socket, Open our connection to
			 * server port .
			 */
			InputStreamReader inputStreamReader = new InputStreamReader(
					server.getInputStream());
			BufferedReader fromServer = new BufferedReader(inputStreamReader);
			PrintStream toServer = new PrintStream(server.getOutputStream());

			// Send machine name or IP address to server:
			toServer.println(hostName);
			toServer.flush();

			/**
			 * Read lines of response from the server,and block while
			 * synchronously waiting:
			 */
			final int lines = 3;
			for (int i = 0; i < lines; i++) {
				String textFromServer = fromServer.readLine();
				if (textFromServer == null) {
					throw new IOException(
							"unable to read from server, end of the stream has been reached");
				} else {
					System.out.println(textFromServer);
				}
			}

		} catch (IOException x) {
			throw x;
		}
	}
}