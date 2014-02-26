/*--------------------------------------------------------

 1. Wentao Liu / 02/23/2014:

 2. Java version used:

 java version "1.7.0_25"
 Java(TM) SE Runtime Environment (build 1.7.0_25-b17)
 Java HotSpot(TM) 64-Bit Server VM (build 23.25-b01, mixed mode)

 
 3. List of files needed for running the program.

 a. mime.properties
 b. MyWebServer.java
 c. BCHandler.java
 d. xstream jars.

 4. Notes:

 a.  The file named mime.properties is needed for server to determine mime type for files.
 b.  I didn't use Handler.java example. I don't even have the Handler class. From start, I wrote BCHandler part on my own, and also I extensively modified BCClient/BCWorker. 
 c.  I am not comfortable with the idea of manipulate/send/receive file content using custom java object(MyDataArray) based on xml/socket, so I modified BCHandler/BCWorker/BCClient, let them send/receive file content as list of string.
 d.  I modified safety check.
 	1. Removed line limit, it's vulnerable, if some one put a very long line in input file. 
 	2 .Instead, added another check: Now if the input file size is too large , BCHandler will refuse to process it. 
 ----------------------------------------------------------*/
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket; // Get the Java networking libraries
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import com.thoughtworks.xstream.XStream;

// Get the Input Output libraries

/**
 * 
 * @author wentao liu
 * 
 */
public class BCHandler {

	/**
	 * max of the input file size. if larger than this , BCHandler will refuse to process it.
	 */
	final static long MAX_FILE_SIZE = 1024 * 1024;

	public static void main(String[] args) throws Exception {

		/**
		 * 
		 * shim.bat is called by browser as handler of the specified mime type . The temp file name was passed as first argument to bat. Bat will call BCHandler, pass that file
		 * name as an environment property firstarg to java system.
		 * 
		 */

		/**
		 * get that firstarg system property. this is temp file name.
		 */
		String fileName = System.getProperty("firstarg");
		/**
		 * get current working path.
		 */
		Path currentRelativePath = Paths.get("");
		/**
		 * reslove filename to actual file. Because file name could be full path or just file name, we need resolve it.
		 */
		Path path = currentRelativePath.resolve(fileName);
		/**
		 * safety. check file not too large.
		 */
		checkFileSize(path);

		/**
		 * read all input file content into list of string.see header's Notes part item c for more details.
		 */
		List<String> basicContent = Files.readAllLines(path, Charset.defaultCharset());

		/**
		 * transform basic file content into xml content
		 */
		String xmlContent = new XStream().toXML(basicContent);

		/**
		 * persist file name.
		 */
		String XMLfileName = "C:\\temp\\mimer.output";

		/**
		 * get out put file object.
		 */
		Path outputPath = Paths.get(XMLfileName);

		/**
		 * write xml content to file object
		 */
		Files.write(outputPath, xmlContent.getBytes());

		/**
		 * call BCClient, send xmlcontent to server.
		 */
		BCClient.sendToBC(xmlContent, BCClient.serverPort, BCClient.serverName);
	}

	/**
	 * 
	 * @param path
	 *            file to check
	 * @throws IllegalArgumentException
	 *             if file size larger that MAX_FILE_SIZE
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private static void checkFileSize(Path path) throws IOException, IllegalArgumentException {
		long length = Files.size(path);
		if (length > MAX_FILE_SIZE) {
			throw new java.lang.IllegalArgumentException("File too large :" + path + "; MAX_FILE_SIZE" + MAX_FILE_SIZE + " exceeded.");
		}
	}
}

/*
 * file is: BCClient.java 
 * 
 * For use with webserver back channel. Written for Windows.
 *  
 * This is a standalone program to connect with MyWebServer.java through a back channel maintaining a server socket at port 2570.
 * 
 * ----------------------------------------------------------------------
 */
class BCClient {
	/**
	 * server to connect.
	 */
	static String serverName = "localhost";
	/**
	 * server port to connect.
	 */
	static final int serverPort = 2570;

	public static void main(String args[]) {

		System.out.println("Clark Elliott's back channel Client.\n");
		System.out.println("Using server: " + serverName + ", Port: 2540 / 2570");

		try {
			String userData;
			do {
				System.out.print("Enter a string to send to back channel of webserver, (quit) to end: ");
				System.out.flush();
				BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
				/**
				 * blocked and read input.
				 */
				userData = in.readLine();

				/**
				 * if ask to quit,exit loop. else continue process
				 */
				if (userData.indexOf("quit") < 0) {
					/**
					 * create a list of string to send.
					 */
					List<String> basicContent = new LinkedList<String>();
					basicContent.add("You");
					basicContent.add("typed");
					basicContent.add(userData);

					XStream xstream = new XStream();

					/**
					 * convert list to xml content string
					 */
					String xml = xstream.toXML(basicContent);
					/**
					 * send xml string to server
					 */
					sendToBC(xml, serverPort, serverName);

					System.out.println("\n\nHere is the XML version:");
					System.out.print(xml);

					/**
					 * deserialize data and print each line.
					 */
					@SuppressWarnings("unchecked")
					List<String> daTest = (List<String>) xstream.fromXML(xml); // deserialize data
					System.out.println("\n\nHere is the deserialized data: ");

					for (String line : daTest) {
						System.out.println(line);
					}
					System.out.println("\n");

				}
			} while (userData.indexOf("quit") < 0);
			System.out.println("Cancelled by user request.");

		} catch (IOException x) {
			x.printStackTrace();
		}
	}

	/**
	 * send sendData to serverPort@serverName
	 * 
	 */
	static void sendToBC(String sendData, int serverPort, String serverName) {

		try {
			// Open our connection Back Channel on server:
			Socket sock = new Socket(serverName, serverPort);
			PrintStream toServer = new PrintStream(sock.getOutputStream());
			// Will be blocking until we get ACK from server that data sent
			BufferedReader fromServer = new BufferedReader(new InputStreamReader(sock.getInputStream()));

			toServer.println(sendData);
			toServer.println("end_of_xml");
			toServer.flush();
			// Read two or three lines of response from the server,
			// and block while synchronously waiting:
			System.out.println("Blocking on acknowledgment from Server... ");
			/**
			 * read acknowledgment line.
			 */
			String textFromServer = fromServer.readLine();
			if (textFromServer != null) {
				System.out.println(textFromServer);
			}
			sock.close();
		} catch (IOException x) {
			System.out.println("Socket error.");
			x.printStackTrace();
		}
	}
}
