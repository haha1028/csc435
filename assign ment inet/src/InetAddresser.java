import java.io.*; // Get the Input Output libraries
import java.net.*; // Get the Java networking libraries

public class InetAddresser {
	public static void main(String args[]) {
		printLocalAddress();
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		try {
			String name;
			do {
				System.out.print("Enter a hostname or an IP address, (quit) to end: ");
				System.out.flush();
				name = in.readLine();
				if (name.indexOf("quit") < 0)
					printRemoteAddress(name);
			} while (name.indexOf("quit") < 0);
			System.out.println("exit");
		} catch (IOException x) {
			x.printStackTrace();
		}
	}

	static void printLocalAddress() {
		try {
			System.out.print("Clark Elliott's INET addresser program, 1.7\n");
			InetAddress me = InetAddress.getLocalHost();
			System.out.println("My local name is: " + me.getHostName());
			System.out.println("My local IP address is: " + toText(me.getAddress()));
		} catch (UnknownHostException x) {
			System.out.println("I appear to be unknown to myself. Firewall?:");
			x.printStackTrace();
		}
	}

	static void printRemoteAddress(String name) {
		try {
			System.out.println("Looking up " + name + "...");
			InetAddress machine = InetAddress.getByName(name);
			System.out.println("Host name : " + machine.getHostName());
			System.out.println("Host IP : " + toText(machine.getAddress()));
		} catch (UnknownHostException ex) {
			System.out.println("Failed to lookup " + name);
		}
	}

	static String toText(byte ip[]) { /* Make portable for 128 bit format */
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < ip.length; ++i) {

			if (i > 0)
				result.append(".");
			result.append(0xff & ip[i]);
		}
		return result.toString();
	}
}