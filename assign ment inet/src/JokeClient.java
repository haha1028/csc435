/*--------------------------------------------------------

 1. Wentao Liu / 01/26/2014:

 2. Java version used:

 java version "1.7.0_25"
 Java(TM) SE Runtime Environment (build 1.7.0_25-b17)
 Java HotSpot(TM) 64-Bit Server VM (build 23.25-b01, mixed mode)

 3. Precise command-line compilation examples / instructions:

 > javac JokeClient.java
 > javac JokeServer.java
 > javac JokeClientAdmin.java

 4. Precise examples / instructions to run this program:

 MUST start server before you start client/admin client,because of the new feature, persistent connection. I talked about this in 6.a 

 In separate shell windows:
 First,
 > java JokeServer
 Then,
 > java JokeClient
 Then,
 > java JokeClientAdmin

 All acceptable commands are displayed on the various consoles.

 This runs across machines, in which case you have to pass the IP address of
 the server to the clients. For example, if the server is running at
 192.168.0.101 then you would type:

 > java JokeClient 192.168.0.101


 5. List of files needed for running the program.

 a. checklist-joke.html
 b. JokeClient.java
 c. JokeServer.java
 d. JokeClientAdmin.java
 
 6. Notes:

 a.  client support both short-connection and persistent connection. In short-mode(which is default), client close connection to server after each joke/proverb query. 
	 But in persist mode ,client connect to server on startup only once, instead of re-connect socket each time. 
	 Modify  JokeClient.usePersistConnection to true to enable persistent connection. 
	 Admin-client only support persistent connection.
 b. support pass client id as arg. 	usage >java JokeClient host clientId. 
 	ClientId arg is optional, application will generate one if you don't pass. To pass client id, host must be passed too.  
 c. support share client id in multiple concurrent connection. eg. more than one client use same client id. 
 	Server will send back to every client the userName that was first issued by client using this client id. 
 	joke/proverbs are non-repeat and random among all clients with same cient Id.
 	no restriction to client mode, can either be short-connection or persist-connection.  
 d. shutdown command from admin client will cause server to close all active connections between this and other clients/admin clients to server.
 e. command of quit and shutdown  are changed to strictly equals "quit" and "Shutdown", instead of just contains them. 
 f.  client can not shutdown server, only admin can. Shutdown command first shutdown admin server, then  shutdown jokeserver. 
 g. made a few changes to output text format, most time they are same, or very similar.
 h. client user must enter a name before query joke/proverb. client don't store input userName, instead ,it store verified and accepted userName by server.

 ----------------------------------------------------------*/

import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries

/**
 * 
 * @author swanliu@gmail.com
 * @version 0.2
 */
public class JokeClient {
	/**
	 * whether to use PersistConnection or not. if set to false,will always break the connection after each joke, or proverb, is returned.
	 */
	static boolean usePersistConnection = false;
	/**
	 * a named field storing the command to quit. this made the code more readable.
	 */
	private static final String QUIT_COMMAND = "quit";

	/**
	 * server socket port to connect.
	 */
	private static final int SERVER_PORT = 11567;
	/**
	 * client version.
	 */
	private static final String VERSION = "0.1";

	public static void main(String args[]) throws Exception {
		boolean usePersistConnection=JokeClient.usePersistConnection;
		// default server name to connect
		String serverName = "localhost";

		// read optional serverName param from args if there is args.
		if (args.length > 0) {
			serverName = args[0];
		}

		String userName = null;
		
		String clientId = java.util.UUID.randomUUID().toString();
		/**
		 * user can pass a clientId as arg.
		 */
		if (args.length > 1) {
			clientId = args[1];
		}
		/**
		 * user can pass usePersistConnection as arg.
		 */
		if (args.length > 2) {
			usePersistConnection = Boolean.parseBoolean(args[2]);
		}
		// print configuration information
		printConfiguration(serverName,clientId,usePersistConnection);
		/**
		 * we need to close resources after use them, so declared two named object, and auto close them by jdk7.
		 */
		try (InputStreamReader inputStreamReader = new InputStreamReader(System.in); BufferedReader bufferedReader = new BufferedReader(inputStreamReader);) {
			/**
			 * create connection to server .I do not want reconnect socket each time, so I re-use it.
			 */
			Socket server = null;
			/**
			 * when use PersistConnection, will not re connect each time
			 */
			if (usePersistConnection) {
				server = new Socket(serverName, SERVER_PORT);
			}
			// continue do job until break by user's quit command
			while (true) {
				if (!usePersistConnection) {
					server = new Socket(serverName, SERVER_PORT);

				}
				if (userName == null) {
					System.out.print("Enter \"" + ClientCommandType.SetName + " yourName\" to set your name,(" + QUIT_COMMAND + ") to end: ");
				} else {
					System.out.print("Enter " + ClientCommandType.QueryNext + " to query next joke/proverb, (" + QUIT_COMMAND + ") to end: ");
				}
				System.out.flush();

				String clientCommand = bufferedReader.readLine();
				/**
				 * validate input.must be not null and not empty.
				 */
				if (clientCommand == null || clientCommand.isEmpty()) {
					System.out.println("command must not be null or empty");
					continue;
				}

				/**
				 * More strict condition to quit. Must exactly equals quit command to quit, other than only contains quit command in the host name.
				 */

				if (clientCommand.equals(QUIT_COMMAND)) {
					System.out.println("Cancelled by user request.");
					// break is to quit here.
					break;
				}
				/**
				 * if userName is not set ,do not allow query command.
				 */
				if (userName == null) {
					if (!clientCommand.startsWith(ClientCommandType.SetName.toString())) {
						System.out.println("You must enter name before QueryNext joke or proverb.");
						continue;
					} else {
						/**
						 * client don't store input userName, instead ,it store verified and accepted userName by server.
						 */
						userName = getResponse(clientId, clientCommand, server)[0];
						continue;
					}
				}
				// didn't quit. userName is set. so do query and print.
				getResponse(clientId, clientCommand, server);

				if (!usePersistConnection) {
					server.close();
				}
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
	private static void printConfiguration(String serverName,String clientId,boolean usePersistConnection) {
		System.out.println("Wentao Liu's Joke Client " + VERSION + ".\n");
		System.out.println("Using clientId: " + clientId);
		System.out.println("Using usePersistConnection: " + usePersistConnection);
		System.out.println("Using server: " + serverName + ", Port: " + SERVER_PORT + "");
	}

	/**
	 * 
	 * @return lines read from server.
	 * 
	 */
	private static String[] getResponse(String clientId, String clientCommand, Socket server) throws IOException {

		try {
			/**
			 * Create filter I/O streams for the socket, Open our connection to server port .
			 */
			InputStreamReader inputStreamReader = new InputStreamReader(server.getInputStream());
			BufferedReader fromServer = new BufferedReader(inputStreamReader);
			PrintStream toServer = new PrintStream(server.getOutputStream());

			// Send machine name or IP address to server:
			toServer.println(clientId + " " + clientCommand);
			toServer.flush();

			/**
			 * Read lines of response from the server,and block while synchronously waiting:
			 */
			final int lines = 2;
			String[] response = new String[lines];
			for (int i = 0; i < lines; i++) {
				String textFromServer = fromServer.readLine();
				response[i] = textFromServer;
				if (textFromServer == null) {
					throw new IOException("unable to read from server, end of the stream has been reached");
				} else {
					System.out.println(textFromServer);
				}
			}
			return response;

		} catch (IOException x) {
			throw x;
		}
	}
}