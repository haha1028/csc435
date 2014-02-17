/*--------------------------------------------------------

 1. Wentao Liu / 02/16/2014:

 2. Java version used:

 java version "1.7.0_25"
 Java(TM) SE Runtime Environment (build 1.7.0_25-b17)
 Java HotSpot(TM) 64-Bit Server VM (build 23.25-b01, mixed mode)

 3. Precise command-line compilation examples / instructions:

 > javac MyWebServer.java

 4. Precise examples / instructions to run this program:

 > java MyWebServer



 5. List of files needed for running the program.

 a. mime.properties
 b. MyWebServer.java

 6. Notes:

 a.  The file named mime.properties is needed for server to determine mime type for files.

 ----------------------------------------------------------*/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
//Get the Input Output libraries
//Get the Java networking libraries

/**
 * 
 * @author swanliu@gmail.com
 * 
 */

public class MyWebServer implements Runnable {
	public static final int PORT = 2540;

	/**
	 * whether to output a lot of debug info to client or not.
	 * 
	 */
	static final boolean debug = false;
	/**
	 * how to separare command args
	 */
	static String COMMAND_ARGS_SEPARATOR = " ";

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
	private Map<String, MyWebWorker> mapOfWorker = new java.util.concurrent.ConcurrentHashMap<String, MyWebWorker>();

	/**
	 * stored known mime types for files.
	 */
	static java.util.Properties mime = new java.util.Properties();
	/**
	 * load at startup. also would reload if meet unknown extension.
	 */
	static {
		loadMIME();
	}

	/**
	 * synchronized because there could be multi threaded call.
	 */
	protected static synchronized void loadMIME() {
		try {
			mime.load(new FileInputStream(new File("mime.properties")));
			System.out.println("Known mimetypes:" + mime.keySet().size());
		} catch (Exception e) {
			e.printStackTrace();

		}
	}

	/**
	 * do nothing but store port. start with server with thread.start to begin listen.
	 * 
	 * @param port
	 *            port to listen
	 */
	public MyWebServer(int port) {
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

		MyWebWorker worker = new MyWebWorker(uuid, sock, this);

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
	protected void workerFinsh(MyWebWorker worker) {
		mapOfWorker.remove(worker.id);
		numberOfCurrentActiveWorkers.decrementAndGet();
	}

	/**
	 * open server, start listen.
	 * 
	 * @throws IOException
	 */
	public void run() {

		try {
			/**
			 * open server socket at port.
			 */
			servsock = new ServerSocket(port, q_len);

			System.out.println("Swan Liu's Web server starting up, listening at port " + port + ".\n");
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
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * close server. By close serverSocket, it will cause thread blocked on serversock.accept() throw SocketException and continue.
	 * 
	 * This method will call by some worker who received shutdown request
	 * 
	 * @throws IOException
	 */
	public synchronized void close() throws IOException {

		try {
			servsock.close();
		} catch (IOException e) {

		}
		/**
		 * shutdown other active clients.
		 */
		for (MyWebWorker worker : mapOfWorker.values()) {

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

		MyWebServer webServer = new MyWebServer(PORT);
		new Thread(webServer).start();

	}

}

class MyWebWorker extends Thread { // Class definition

	String id; // worker uuid.
	Socket sock; // Class member, socket, local to Worker.
	MyWebServer webServer; // reference to server instance.

	MyWebWorker(String id, Socket s, MyWebServer webServer) {
		this.id = id;
		sock = s;
		this.webServer = webServer;

	} // Constructor, assign arg s to local sock

	public void run() {

		try {

			try {
				// Get I/O streams in/out from the socket:
				BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				PrintStream out = new PrintStream(sock.getOutputStream());
				/**
				 * test sock not closed.
				 */
				if (sock.isClosed()) {
					System.out.println("Sock is closed by client.");

					return;
				}

				/**
				 * create a request.
				 */
				Request request = new Request(in);
				/**
				 * request read input content
				 */
				request.readInput();
				/**
				 * request parse parmaters from query string.
				 */
				request.parseParameters();
				/**
				 * create a response object
				 */
				Response response = new Response(out);
				/**
				 * handle request , store possible output in response.
				 */
				handle(request, response);
				/**
				 * reponse write ouput content to actual out stream.
				 */
				response.writeOutput();
				/**
				 * close client.
				 */
				in.close();
				out.close();

			} catch (IOException ioe) {
				// System.out.println("Server read error");
				// ioe.printStackTrace();
			}

			System.out.println("Exit worker with " + sock.getRemoteSocketAddress() + ".");
		} finally {
			close();
		}

	}

	/**
	 * find a service and let the service handle the request, write response to response object.
	 */
	private void handle(Request request, Response response) throws IOException {
		/**
		 * find service by request endpoint. endpoint mainly meaning the uri.
		 */
		String endpoint = request.endpoint;
		/**
		 * if no endpoint found, send notfound.
		 */
		if (endpoint == null) {
			this.notfound(response);
			return;
		}
		/**
		 * look up servier using endpoint
		 */
		HttpService service = lookUpService(endpoint);
		/**
		 * service do handle, after handle, worker will let the response write content to actual output.
		 */
		service.handle(request, response);

	}

	/**
	 * service is determined by extension of endpoint. if end with fake-cgi, goes to DynamicServletService.else all go to file system browse service.
	 * 
	 * @param endpoint
	 * @return HttpService determined by extension of endpoint.
	 */
	private HttpService lookUpService(String endpoint) {
		if (endpoint.endsWith("fake-cgi")) {
			DynamicServletService service = new DynamicServletService();
			return service;
		} else if (endpoint.endsWith("/") || endpoint.endsWith(".txt") || endpoint.endsWith(".html") || endpoint.endsWith(".java")) {
			FileSystemService service = new FileSystemService();
			return service;
		} else {
			FileSystemService service = new FileSystemService();
			return service;
		}
	}

	/**
	 * abstract interface to a http service. handle request ,write process result to response.
	 * 
	 */
	interface HttpService {
		void handle(Request request, Response response) throws IOException;

	}

	/**
	 * compute num1 +num2 in request ,write person and sum to output. *
	 */
	class DynamicServletService implements HttpService {
		public void handle(Request request, Response response) {

			/***
			 * build debug information start
			 */
			StringBuffer contentBuilder = new StringBuffer();
			contentBuilder.append("Request Endpoint is " + request.endpoint + "<br/>");
			for (Entry<String, String> requestAttribute : request.getRequestAttributes().entrySet()) {

				String key = requestAttribute.getKey();
				String value = requestAttribute.getValue();

				contentBuilder.append("Key " + key + "'s value is " + value + "<br/>");
			}
			log(contentBuilder.toString());
			/**
			 * build debug information end.
			 */

			/**
			 * get num1 and num2 from parsed request attribtes , add as sum, set compute result to content of response.
			 */
			int num1 = Integer.parseInt(request.getRequestAttributes().get("num1"));
			int num2 = Integer.parseInt(request.getRequestAttributes().get("num2"));
			String person = request.getRequestAttributes().get("person");
			log("person=" + person);
			String content = "Dear " + person + ", the sum of " + num1 + " and " + num2 + " is " + (num1 + num2) + ".";
			response.content = content.getBytes();

		}

	}

	/**
	 * 
	 * allow user browse file system via http.
	 */
	class FileSystemService implements HttpService {
		final String directoryIdexHtmlFileName = "index.html";

		public void handle(Request request, Response response) throws IOException {

			/**
			 * to handle space in requested path, do a decode.
			 */
			String endpoint = request.endpoint;
			endpoint = URLDecoder.decode(endpoint, "utf-8");

			/**
			 * remove leading / in endpoint.
			 */
			String fileName = endpoint.substring(1);
			log("FileSystemService: client request " + fileName);

			/**
			 * get current working dir. method from jdk nio package
			 */
			Path currentRelativePath = Paths.get("");
			/**
			 * reslove request string to actual file
			 */
			Path path = currentRelativePath.resolve(fileName);

			/**
			 * basic security by restricting access to directory tree, prevent directory travel. only sub directory of working directory is allowed
			 */
			if (!path.toAbsolutePath().startsWith(currentRelativePath.toAbsolutePath())) {
				log(currentRelativePath.toAbsolutePath().toString());
				log(path.toAbsolutePath().toString());
				/**
				 * send 403 to response.
				 */
				forbidden(response);
				return;
			}
			
			File file = path.toFile().getAbsoluteFile();
			/**
			 * file not found. send 404.
			 */
			if (!file.exists()) {
				log(" file not found :"+file.getAbsolutePath());

				notfound(response);
				return;
			}

			/**
			 * compute dynamic view for directory. space in name are handled, replaced to %20
			 */
			if (file.isDirectory()) {
				log("getDirectoryView :"+file.getAbsolutePath());
				getDirectoryView(request, response, endpoint, path);

				return;

			} else {
				/**
				 * send file content to out.
				 */
				byte[] data = Files.readAllBytes(path);
				response.setContent(data);
			}
			/**
			 * if no extension ,use .txt as extension to find a mime.
			 */
			String extension = ".txt";

			int i = fileName.lastIndexOf('.');
			if (i > 0) {
				extension = fileName.substring(i + 1);
			}
			/*
			 * lookup mime. set mime.
			 */
			String mimeHeader = lookUpMime(extension);
			response.setMimeHeader(mimeHeader);
		}

		/**
		 * html view of directory. if directoryIdexHtmlFileName exists, will return this file, instead of directory content.
		 */
		private void getDirectoryView(Request request, Response response, String endpoint, Path path) throws IOException {
			
			/**
			 * if there is a index.html, use that file as html view of directory.
			 */
			Path directoryIdexHtmlFile = path.resolve( directoryIdexHtmlFileName);
			log("directoryIdexHtmlFile is"+directoryIdexHtmlFile.getFileName().toString());
			if (Files.exists(directoryIdexHtmlFile)) {
				byte[] content = Files.readAllBytes(directoryIdexHtmlFile);
				response.setContent(content);
				response.setMimeHeader("text/html");
				return;

			} else {
				File file = path.toFile().getAbsoluteFile();
				
				String fileName = path.toFile().getName();
				log("fileName:"+fileName);
				StringBuilder sb = new StringBuilder();
				sb.append("<html>");
				sb.append("<pre/>");
				int lastSlashIndex = endpoint.lastIndexOf('/');
				String parent = "/";
				if (lastSlashIndex != 0) {
					parent = endpoint.substring(0, lastSlashIndex);
				}
				log("parent is "+parent);
				sb.append("<h1>Index of " + endpoint + "</h1>");
				sb.append("<a href=\"" + parent.replace(" ", "%20") + "\">Parent Directory</a> <br>");
				sb.append("<table>");
				for (File subFile : file.listFiles()) {
					String subFileName = subFile.getName().replace(" ", "%20");
					
					
					String link= endpoint +"/"+ subFileName;
					if(endpoint.equals("/"))
					{
						link= endpoint + subFileName;
					}
					//log("link="+link);
					

					sb.append("<tr><td><a href=\"" + link + "\" >" + subFile.getName() + " </a></td><td> lastModified:" + new Date(subFile.lastModified()) + "</td>");
				}
				sb.append("</table>");
				sb.append("</html>");
				response.setMimeHeader("text/html");
				response.setContent(sb.toString().getBytes());
			}
		}

		/**
		 * get mime. reload and retry if mime not found.
		 * 
		 * default is text/plain.
		 */
		private String lookUpMime(String extension) {
			String mime = MyWebServer.mime.getProperty(extension);
			if (mime == null) {
				MyWebServer.loadMIME();
				mime = MyWebServer.mime.getProperty(extension);
				if (mime == null) {
					mime = "text/plain";
				}
			}

			return mime;
		}

	}

	/**
	 * close socket, notify webServer.
	 */
	public void close() {
		try {
			sock.close();
		} catch (IOException e) {
		}
		webServer.workerFinsh(this);
	}

	class Request {
		Request(BufferedReader input) {
			this.input = input;
		}

		/**
		 * should be get method . in this assignment.
		 */
		String queryMethod;
		/**
		 * structure: /endpoint?queryString
		 */
		String endpoint;
		String queryString;
		/**
		 * httpVersion in requst. no use. just keep it.
		 */
		String httpVersion;
		protected HashMap<String, String> attributes = new HashMap<String, String>();
		BufferedReader input;

		/**
		 * read query string form input stream
		 * 
		 * @throws IOException
		 */
		protected void readInput() throws IOException {

			String query = input.readLine();

			log("query is " + query);
			parseQueryString(query);

			/**
			 * no use lines. read and just log them.
			 */
			String line;
			do {
				line = input.readLine();
				log(line);
			} while (line != null && input.ready());

		}

		/**
		 * parse query string from first line of request.
		 * 
		 * @param query
		 */
		protected void parseQueryString(String query) {
			if (query == null || query.isEmpty()) {
				return;
			}
			log(query);
			/**
			 * let query be GET /cgi/addnums.fake-cgi?person=YourName&num1=4&num2=5 HTTP/1.1
			 * 
			 * */
			String[] querySegments = query.split(" ");
			queryMethod = querySegments[0]; // GET
			queryString = querySegments[1];// /cgi/addnums.fake-cgi?person=YourName&num1=4&num2=5
			int endpointIndex = queryString.indexOf('?');
			if (endpointIndex != -1) {
				endpoint = queryString.substring(0, endpointIndex); // /cgi/addnums.fake-cgi
				queryString = queryString.substring(endpointIndex, queryString.length());// ?person=YourName&num1=4&num2=5
			} else {
				endpoint = queryString;
				queryString = "";
			}
			httpVersion = querySegments[2]; // HTTP/1.1
		}

		/**
		 * * parse query parama meters from queryString, stored in attributes field.
		 */
		protected void parseParameters() {
			log("queryString is " + queryString);
			if (queryString == null || queryString.isEmpty()) {
				return;
			}
			String[] queryParameterSegments = queryString.split("&");
			for (String parametersegment : queryParameterSegments) {
				String[] parts = parametersegment.split("=");
				if (parts.length > 0) {
					String key = parts[0].replace("?", "");
					String val = parts[1].trim();
					attributes.put(key, val);
				}
			}

		}

		/**
		 * cloned attributes to prevent modification.
		 * 
		 * @return
		 */
		@SuppressWarnings("unchecked")
		protected Map<String, String> getRequestAttributes() {
			return (HashMap<String, String>) attributes.clone();
		}
	}

	/**
	 * hold information to http response, including status code, and content.
	 * 
	 * */
	class Response {

		int status = 200;
		String statusDesp = "OK";
		Date lastModified = new Date();
		String mimeHeader = "text/html";

		PrintStream output;
		/**
		 * out put data.
		 */
		byte[] content = "".getBytes();

		Response(PrintStream out) {
			this.output = out;
		}

		/**
		 * write content to actual output.
		 * 
		 * @throws IOException
		 */
		void writeOutput() throws IOException {
			/**
			 * last modified is passed from service, do not use current, though it is default for testing purpose. but in correct design ,the default behavior should be there is no
			 * default last modified
			 */
			String statusLine = "HTTP/1.1" + " " + status + " " + statusDesp;
			String lengthLine = "Content-Length:" + content.length;
			String contentTypeLine = "Content-Type:" + mimeHeader;
			String lastModifedLine = "Last-Modified:" + lastModified.toString();
			String connectionLine = "Connection: close";

			outputLine(statusLine);
			outputLine(lengthLine);
			outputLine(contentTypeLine);
			outputLine(lastModifedLine);
			outputLine(connectionLine);
			outputLine("");
			output.write(content);
		}

		void outputLine(String line) {
			output.print(line + "\r\n");
			log(line);
		}

		protected void setLastModified(Date lastModified) {
			this.lastModified = lastModified;
		}

		protected void setMimeHeader(String mimeHeader) {
			this.mimeHeader = mimeHeader;
		}

		protected void setContent(byte[] content) {
			this.content = content;
		}

	}

	void log(String message) {
		System.out.println(message);
	}

	private void notfound(Response response) {
		response.status = 404;
		response.statusDesp = "NotFound";
		response.content = "404 NotFound".getBytes();
	}

	private void forbidden(Response response) {
		response.status = 403;
		response.statusDesp = "Forbidden";
		response.content = "403 Forbidden".getBytes();
	}
}
