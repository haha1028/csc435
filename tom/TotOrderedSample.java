
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;

public class TotOrderedSample {
	public static int LogicalTime = 0;
	public static int LocalTimer = 0;
	public static int MaxQueue = 100;
	public static boolean DEBUG = false;
	public static int MAXDELAY = 5;
	public static Random rand = new Random();
	public static Comparator<Message> comparator = new MessageComparator();
	// Might want to implement this as priority queue for simplicity. Priority
	// not necessary however:
	public static Queue<Message> SavedAckQueue = new LinkedList<Message>(); // Ack
																			// arrives
																			// before
																			// message
	public static PriorityQueue<Message> NewMessageQueue = // The messages we
															// are to process
	new PriorityQueue<Message>(MaxQueue, comparator);
	public static PriorityQueue<Message> SendQueue = // Fake some network
														// latency
	new PriorityQueue<Message>(MaxQueue, comparator);
	public static PriorityQueue<Message> WaitForAcksQueue = // Wait for
															// Acknowledgments
															// from other procs
	new PriorityQueue<Message>(MaxQueue, comparator);
	public static Iterator<Message> AckIt = WaitForAcksQueue.iterator();
	public static PriorityQueue<Message> ExecuteQueue = // Pass the message to
														// the "application"
	new PriorityQueue<Message>(MaxQueue, comparator); // After waiting for msgs
														// from one proc
	public static int ProcessID;
	public static Message Dummy = new Message(); // So we can access methods in
													// Message
	public static Initializer Dummy2 = new Initializer(); // So we can access
															// methods in
															// Initializer
	public static int MsgNumberCounter = 1000;

	public static void main(String[] args) {
		if (args.length == 1) {
			ProcessID = Integer.parseInt(args[0]);
		} else if (args.length == 2) { // accept any second arg as DEBUG
										// trigger.
			ProcessID = Integer.parseInt(args[0]);
			TotOrderedSample.DEBUG = true;
		} else {
			System.out.println("<1 or 2 or 3> is required argument for process ID.");
			return;
		}

		Dummy2.EnqueueDataFile(); // Read in the data

		LogicalTimeLooper LT = new LogicalTimeLooper(); // create a DIFFERENT
														// thread
		Thread t = new Thread(LT);
		t.start(); // ...and start it, to increment the logical timer.
	}
}

class Initializer {
	void EnqueueDataFile() {
		String strLine;
		int ProcID;

		try {
			@SuppressWarnings("resource")
			BufferedReader in = new BufferedReader(new FileReader("TotOrdered.dat"));
			while ((strLine = in.readLine()) != null) {
				if (strLine.indexOf("//") > -1) {
				} // ignore comment lines
				else {
					@SuppressWarnings("resource")
					Scanner scan = new Scanner(strLine);
					if (TotOrderedSample.DEBUG)
						System.out.println(strLine); // <-- show the data
					ProcID = scan.nextInt();
					if (ProcID == TotOrderedSample.ProcessID) { // Ignore
																// Messages for
																// other
																// processes
						Message m = new Message();
						m.MsgType = "msg";
						m.ProcessID = ProcID;
						m.MsgNumber = scan.nextInt();
						m.MsgActionTime = scan.nextInt(); // Messages sent
															// according to this
															// timer time
						TotOrderedSample.NewMessageQueue.add(m); // Queue them
																	// up, send
																	// them
																	// later
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Problem with TotOrdered.dat file " + e);
		}
		// MUST: You'll need to start all the procsses at once here.
	}
}

class Message {
	int ProcessID;
	int MsgNumber;
	String MsgType; // "ack" or "msg"
	float MsgTimeOfSend; // Logical Time, TimeStamp!
	int MsgActionTime; // Local:timer: Delayed send time OR Time to execute,
						// priority sort field
	int AckMsgNumber; // The number of the message being acknowledged
	boolean[] MsgAck = new boolean[4]; // Default is initialize to false. Won't
										// use [0]

	// We've simulated the creation of new messages to be processed by placing
	// them in
	// a time-sorted queue:
	void CheckNewMessage() {
		Message m = TotOrderedSample.NewMessageQueue.peek();
		if (m != null && m.MsgActionTime <= TotOrderedSample.LocalTimer) { // Java
																			// short-circuit
																			// eval
																			// of
																			// condition
			m = TotOrderedSample.NewMessageQueue.remove();
			if (m.MsgType == "msg") { // This is our initiated message, so:
				m.SendMessage(); // Send to other procs. Add to Wait-For-Acks
									// queue:
				EnqueueFakeAck(m.MsgNumber, 2); // Fake ack from p2
				EnqueueFakeAck(m.MsgNumber, 3); // Fake ack from p3
			} else {
				m.ReceiveMessage();
			}
		}
	}

	void EnqueueFakeAck(int MsgNum, int ProcID) {
		Message m = new Message();
		m.MsgNumber = TotOrderedSample.MsgNumberCounter++; // New message number
		m.AckMsgNumber = MsgNum; // The number of the message being
									// acknowledged.
		m.ProcessID = ProcID;
		m.MsgType = "ack";
		// Faking the logical time of the other (sending) process:
		m.MsgTimeOfSend = (float) ProcID / 10 + (float) TotOrderedSample.LogicalTime + (float) TotOrderedSample.rand.nextInt(5);
		// Faking the time when acknowledgements of the message arrive:
		m.MsgActionTime = TotOrderedSample.LocalTimer + TotOrderedSample.rand.nextInt(2 * TotOrderedSample.MAXDELAY);
		SOP("[FakeAck] AckMsgNum: " + m.MsgNumber + ", Ack for msg: " + m.AckMsgNumber + ", TimeOfSend: " + m.MsgTimeOfSend + ", Proc ID: " + m.ProcessID + ", LocalActionTime: "
				+ m.MsgActionTime);
		TotOrderedSample.NewMessageQueue.add(m);
	}

	// Send the local messages to the other processes:
	void SendMessage() { // Add random latency delay then send.
		this.MsgAck[TotOrderedSample.ProcessID] = true; // Acknowledge this
														// process has received.
		// This is the time stamp, 20.1, or 20.2, etc. to avoid ties:
		this.MsgTimeOfSend = (float) TotOrderedSample.ProcessID / 10 + (float) TotOrderedSample.LogicalTime;
		SOP("[Send] ProcessID: " + TotOrderedSample.ProcessID + " Time stamp: " + (float) this.MsgTimeOfSend);
		// MUST: Add some random latency delay to the arrival time at the other
		// end:
		// Ordinarily should make a COPY of this message and add to local queue
		// with current time
		// Here we delay Msgs radomly so that some arrive before their acks, to
		// make it interesting:
		this.MsgActionTime = TotOrderedSample.LocalTimer + TotOrderedSample.rand.nextInt(TotOrderedSample.MAXDELAY);
		TotOrderedSample.SendQueue.add(this); // Place in latency faking queue
												// to send to other procs
		TotOrderedSample.WaitForAcksQueue.add(this); // Place into the local
														// ack-waiting queue.

		// PrintQueue("Send Q: ", TotOrdered.SendQueue); // print queue if you
		// like
		// PrintQueue("Wait4Acks Q: ", TotOrdered.WaitForAcksQueue); // print
		// queue if you like
	}

	void LowLevelSend() { // Acutally send the message to all other processes
		Message m = TotOrderedSample.SendQueue.peek();
		if (m != null) {
			if (m.MsgActionTime < TotOrderedSample.LocalTimer) {
				RealSend(TotOrderedSample.SendQueue.remove());
			}
		}
	}

	void RealSend(Message m) {
		SOP("[RealSend] Msg " + m.MsgNumber + " has arrived at remote processes after delay.");
	}

	/*
	 * Check to see if all acknowledgements are in, if so send to application:
	 * Each local message its own boolean array with three slots (slot 0 not
	 * used), that represent the three processes and their acknowledgement of
	 * the message.
	 * 
	 * If the message belonged to Process 1, and no acks had been received the
	 * array would look like this a[0,1,0,0] where the element ONE, is set to
	 * true because we own the message so we don't need to ack it. If you get a
	 * response from process three: [0,1,0,1] elements 1 and 3 are set to true.
	 * The process id's coordinate with the array element position. process 1 is
	 * at a[1] process 2 is at a[2]. Once a[1] a[2] and a[3] are all set to
	 * true, [0,1,1,1], you can send the message to the application layer.
	 * Thanks Christina for comment!
	 */

	void AckCheck() {
		PriorityQueue<Message> TempAckQueue = new PriorityQueue<Message>(TotOrderedSample.MaxQueue, TotOrderedSample.comparator);

		Message m = TotOrderedSample.WaitForAcksQueue.poll();
		while (m != null) {
			if (m.MsgAck[1] && m.MsgAck[2] && m.MsgAck[3]) { // Not sure I ever
																// got to this
																// code, test?
				TotOrderedSample.ExecuteQueue.add(m); // Lamport requires all
														// packets from one Proc
														// arrive same order
			} else {
				TempAckQueue.add(m);
			}
			m = TotOrderedSample.WaitForAcksQueue.poll();
		}
		TotOrderedSample.WaitForAcksQueue = TempAckQueue;
		// PrintQueue("WaitForAcksQ: ", WaitForAcksQueue); // Take a look at
		// queue if you like
	}

	// Messages are created locally, and received. Acknowledgements (this dummy
	// program) and
	// messages (full implementation) arrive from other processes. They are
	// processed here.
	boolean ReceiveMessage() {
		boolean AckedTheMessage = false;

		if (TotOrderedSample.LogicalTime < this.MsgTimeOfSend) { // update
																	// LogicalTime
																	// if needed
			SOP("[Received] Updating Logical Time from " + TotOrderedSample.LogicalTime + " to " + ((int) this.MsgTimeOfSend + 1));
			TotOrderedSample.LogicalTime = (int) this.MsgTimeOfSend + 1;
		}

		if (this.MsgType == "msg") {
			// MUST: We would be receiving messages from other procs here so..
			// add message to NewMessage queue.
			return true; // this standalone program will never get here.
		}

		if (this.MsgType == "ack") {
			SOP("[Received] Ack Message Number: " + this.MsgNumber + " for " + this.AckMsgNumber + ", TimeOfSend: " + this.MsgTimeOfSend + ", by proc: " + this.ProcessID);

			PriorityQueue<Message> TempWaitForAcksQueue = new PriorityQueue<Message>(TotOrderedSample.MaxQueue, TotOrderedSample.comparator);

			Message m = TotOrderedSample.WaitForAcksQueue.poll(); // Check for
																	// empty
			while (m != null) {
				if (m.MsgNumber == this.AckMsgNumber) {
					SOP("[Received] Process " + this.ProcessID + " has sent ack for message " + m.MsgNumber);
					m.MsgAck[this.ProcessID] = true; // note that sender has
														// ack'ed this message.
					if (m.MsgAck[1] && m.MsgAck[2] && m.MsgAck[3]) {
						TotOrderedSample.ExecuteQueue.add(m); // fully ack'ed so
																// add to
																// execute queue
					} else {
						TempWaitForAcksQueue.add(m); // not fully ack'ed so put
														// back in the queue
					}
					AckedTheMessage = true;
				} else {
					TempWaitForAcksQueue.add(m);
				}
				m = TotOrderedSample.WaitForAcksQueue.poll();
			}
			TotOrderedSample.WaitForAcksQueue = TempWaitForAcksQueue;
			// PrintQueue("Wait for Acks Q: ", TotOrdered.WaitForAcksQueue); //
			// look at queue?
			return AckedTheMessage;
		}
		return false; // will never get here, but compiler wants it.
	}

	// For each ack in the SavedAck queue, send to receive. Imagine P1 sends
	// message M to P2 and P3.
	// P3 sends an ack to P2 that it has got M. Later P2 gets the actual message
	// M, so P2 must save ack.
	void CheckSavedAck() {
		PriorityQueue<Message> TempSavedAckQueue = new PriorityQueue<Message>(TotOrderedSample.MaxQueue, TotOrderedSample.comparator);

		Message m = TotOrderedSample.SavedAckQueue.poll();
		while (m != null) {
			if (!m.ReceiveMessage()) { // No luck processing ack
				TempSavedAckQueue.add(m); // So add back to SaveAck queue, try
											// later
			}
			m = TotOrderedSample.SavedAckQueue.poll();
		}
		TotOrderedSample.SavedAckQueue = TempSavedAckQueue;
	}

	// This is a sort of catch-all. You might need to extend the delay. Lamport
	// requires all packets
	// from one Proc arrive same order, so we let them settle here beyond
	// MAXDELAY
	void CheckExecuteQueue() {
		PriorityQueue<Message> TempExecuteQueue = new PriorityQueue<Message>(TotOrderedSample.MaxQueue, TotOrderedSample.comparator);
		Message m = TotOrderedSample.ExecuteQueue.poll();
		while (m != null) { // Leave time for order of messages from on proc to
							// settle:
			if (m.MsgActionTime + (2 * TotOrderedSample.MAXDELAY) + 1 < TotOrderedSample.LocalTimer) {
				TempExecuteQueue.add(m); // So add back to Execute queue, try
											// later
			} else {
				SOP("[Execute] sending msg " + m.MsgNumber + " to Application");
				m.SendToApplication();
			}
			m = TotOrderedSample.ExecuteQueue.poll();
		}
		TotOrderedSample.ExecuteQueue = TempExecuteQueue;
	}

	void SendToApplication() {
		// There is no application, so we just print out the message on the
		// console:
		System.out.println("APLICATION has received Message: " + this.MsgNumber + ", Timestamp: " + this.MsgTimeOfSend);
	}

	// System out println if DEBUG is on:
	void SOP(String s) {
		if (TotOrderedSample.DEBUG)
			System.out.println(TotOrderedSample.LocalTimer + "/" + TotOrderedSample.LogicalTime + " " + s);
	}

	// Utility to look at queues:
	void PrintQueue(String s, PriorityQueue<Message> q) {
		System.out.println(s + " (size: " + q.size() + ")");

		Iterator<Message> Itr = q.iterator();
		while (Itr.hasNext()) {
			Message m = (Message) Itr.next();
			System.out.println("  PID: " + m.ProcessID + ", Msg#:  " + m.MsgNumber + ", TimeOfSend " + m.MsgTimeOfSend + ", ActionTime: " + m.MsgActionTime + ", AckMsg#: "
					+ m.AckMsgNumber + ", acks 1: " + m.MsgAck[1] + ", 2: " + m.MsgAck[2] + ", 3: " + m.MsgAck[3]);
		}
	}
}

// Needed for the priority queue implementation...
// Compare the locl "action time" of the messages for sorting:

class MessageComparator implements Comparator<Message> {
	public int compare(Message Msg1, Message Msg2) {
		if (Msg1.MsgActionTime < Msg2.MsgActionTime)
			return -1;
		if (Msg1.MsgActionTime > Msg2.MsgActionTime)
			return 1;
		return 0;
	}
}

class LogicalTimeLooper implements Runnable {
	public static boolean TimerControlSwitch = true;
	public static int LogicalTimeIncrement = TotOrderedSample.ProcessID; // cde
																			// is
																			// this
																			// right?

	public void run() { // RUNning the Logical Time updater
		if (TotOrderedSample.DEBUG)
			System.out.println("In the Time Looper thread");
		int i = 0;

		while (TimerControlSwitch) {
			i++;
			if (i > 17)
				TimerControlSwitch = false;
			try {
				Thread.sleep(50);
			} catch (InterruptedException ex) {
			}
			;
			TotOrderedSample.LocalTimer += 1;
			TotOrderedSample.LogicalTime = TotOrderedSample.LogicalTime + LogicalTimeIncrement;

			TotOrderedSample.Dummy.CheckNewMessage(); // Check for incoming, or
														// self-generated,
														// messages.

			TotOrderedSample.Dummy.LowLevelSend(); // Actually send after faked
													// inet latency delay
			TotOrderedSample.Dummy.AckCheck(); // Check to see if all
												// acknowledgements are in.
			TotOrderedSample.Dummy.CheckSavedAck(); // See if msgs have arrived
													// for saved acks
			TotOrderedSample.Dummy.CheckExecuteQueue(); // See if msgs have
														// arrived for saved
														// acks
		}
	}
}
