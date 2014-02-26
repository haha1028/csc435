package src;
/* TotOrdered.java 2012-05-20 Clark Ellott Version 1.0

This is throw-away utility code to get you started on the Totally
Ordered Multicasting assignment. It does not actually do anything of
interest, but maniuplates the sort of queues that you'll likely want
for this assignment.  These functions would be part of a middleware
layer that guarantees that copies of the application it supports,
running on different computer systems, will all perform the same
application steps in the same order.

You are free to modify this code at will, as long as you include
your own comments in it.

There is absolutely no requirement that you use any of this code in
your project.

Details:

MAIN
    Checks arguments designating the code as running process 1,
process 2, or process 3. Reads in the data file containing the
application messages. Starts a separate thread containing a loop that
does the processing.

INITIALIZER
    In this uility version contains the method for reading in the data
file. In the full version will also have to coordinate the start of
all three processes.

MESSAGE
    This is the basic data type. It is used to simulate messages
carrying application instructions, and also acknolwedgements that
those instructions have been received by the middleware layer on a
remote machine.  [MsgActionTime] is the sort field for priority
queues, and relates to the local timer (not the local logical time).

CHECK NEW MESSAGE
    Check to see if any new messages have arrived, either from another
process, or locally. In this utility version we just build a priority
queue to simulate these messages. When we get a new message, in this
version we also queue up (with delay) fake acknowledgments that would
arrive from the other processes. If a message's time has arrived, send
it to [ReceiveMessage]

ENQUEUE FAKE ACK
    A priority queue keeping track of the fake acknowledgments.

SEND MESSAGE

    Sends the local messages to all other processes. To simuate
network latency delay we place the messages in a queue to be sent
later at random times.

LOW LEVEL SEND
    Inspect the send queue to see if the delay has been accounted for
so that we can actually send the message.

REAL SEND
    Print on the console that the message has arrived at the remote process.

ACK CHECK
    Check all the messages being prepared for the application level on
this process. When all the acknowledgments are in, place in the
execution queue.

RECEIVE MESSAGE
    Receive messages from other processes (though this is a non-op in
this utility program). Received acknowledgments from other processes
for messages we have sent.

    For acknowledgments that have arrived, check to see if the message
they are acknowledging has arrived; if so, note in the message that it
has been acknowledged by the sending process.

    Update the logical time when any message is received, if necessary.

CHECK SAVED ACK
    Periodically send acknowledgments that have arrived before their
    message to [ReceiveMessage] for processing.

CHECK EXECUTE QUEUE 
    Because with (simulated) random network delay messages may arrive
out of order, instead of simply handing them to the application, save
them in a time-buffering queue so that all previous messages have a
chance to arrive before a message after them is given to the
application. This is a catch-all to guarantee one of the invariants of
Lamport's algorithm, that all messages from a process will arrive in
order.

SEND TO APPLICATION
    Print out on the console that the application has received the
message. In a more interesting version, the application itself
would, after a random processing time, generate a new follow-up
message.

SOP
    A specialized version of System.out.println() that checks to
see if DEBUG is on, and also prints the time and logical time values.

PRINT QUEUE
    Prints out the contents of a prioity queue in a non-destructive way.

MESSAGE COMPARATOR
    The compare function used to set priority order in a priority
queue that contains messages.

LOGICAL TIME LOOPER
    Thread loop that updates both the local timer and also the logical time.

    It also initiates processing checks on all of the priority queues
to see if it is time for the head message to be executed.

THE QUEUES:

SavedAckQueue     // Ack arrives before message
NewMessageQueue   // The messages we are to process
SendQueue         // Fake some network latency
WaitForAcksQueue  // Wait for Acknowledgments from other procs
ExecuteQueue      // Pass the message to the "application"

DATA FILE:

TotOrdered.dat: 
// ProcessNum MsgNum DelayBeforeSend <crlf>  <-- a comment
1 100 5  <crlf> 
1 102 7  <crlf> 
etc.

Messages in the data file that are not for this process are ignored.

In the assignment additional threads may be needed to implement
communication with the other processes.

If so, then there will be synchronization issues to be addressed
so that two threads are not manipulating the queues at the same
time. If you punt on this issue, be sure to note that you understand
where you have done so in your comments. Keep in mind that if
two threads are making queries on one queue, and one of the threads
is modifying the queue, there will be problems.

Use UDP for the sending and receiving of messages between processes.

In these utility functions, for simplicity (but lacking efficiency)
when queues are traversed such that elements might be removed, each
message that is to remain in the queue is copied to a temporary queue, 
and then the queue head pointer is pointed to the temporary queue.

-----------------------------------------------------------------*/
   
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;

public class TotOrdered{
  public static int LogicalTime = 0;
  public static int LocalTimer = 0;
  public static int MaxQueue = 100;
  public static boolean DEBUG = false;
  public static int MAXDELAY = 5;
  public static Random rand = new Random(); 
  public static Comparator<Message> comparator = new MessageComparator();
  // Might want to implement this as priority queue for simplicity. Priority not necessary however:
  public static Queue<Message> SavedAckQueue = new LinkedList<Message>(); // Ack arrives before message
  public static PriorityQueue<Message> NewMessageQueue = // The messages we are to process
    new PriorityQueue<Message>(MaxQueue, comparator);
  public static PriorityQueue<Message> SendQueue = // Fake some network latency
    new PriorityQueue<Message>(MaxQueue, comparator);
  public static PriorityQueue<Message> WaitForAcksQueue =  // Wait for Acknowledgments from other procs
    new PriorityQueue<Message>(MaxQueue, comparator);
  public static Iterator<Message> AckIt = WaitForAcksQueue.iterator();
  public static PriorityQueue<Message> ExecuteQueue = // Pass the message to the "application"
    new PriorityQueue<Message>(MaxQueue, comparator); // After waiting for msgs from one proc
  public static int ProcessID;
  public static Message Dummy = new Message(); // So we can access methods in Message
  public static Initializer Dummy2 = new Initializer(); // So we can access methods in Initializer
  public static int MsgNumberCounter = 1000;


  public static void main(String[] args){
    if (args.length == 1){
      ProcessID = Integer.parseInt(args[0]); 
    }
    else if (args.length ==  2){ // accept any second arg as DEBUG trigger.
      ProcessID = Integer.parseInt(args[0]); 
      TotOrdered.DEBUG = true;
    }
    else{
      System.out.println("<1 or 2 or 3> is required argument for process ID.");
      return;
    }

    Dummy2.EnqueueDataFile(); // Read in the data

    LogicalTimeLooper LT = new LogicalTimeLooper(); // create a DIFFERENT thread
    Thread t = new Thread(LT);
    t.start();  // ...and start it, to increment the logical timer.
  }
}

class Initializer{
  void EnqueueDataFile(){
    String strLine;
    int ProcID;

    try{
      @SuppressWarnings("resource")
	BufferedReader in = new BufferedReader(new FileReader("TotOrdered.dat"));
      while ((strLine = in.readLine()) != null){
	if (strLine.indexOf("//") > -1) {} // ignore comment lines
	else{
	  @SuppressWarnings("resource")
	Scanner scan = new Scanner(strLine);
	  if (TotOrdered.DEBUG) System.out.println(strLine); // <-- show the data
	  ProcID = scan.nextInt();
	  if (ProcID == TotOrdered.ProcessID){ // Ignore Messages for other processes
	    Message m = new Message();
	    m.MsgType = "msg";
	    m.ProcessID  = ProcID;
	    m.MsgNumber = scan.nextInt();
	    m.MsgActionTime = scan.nextInt(); // Messages sent according to this timer time
	    TotOrdered.NewMessageQueue.add(m);  // Queue them up, send them later
	  }
	}
      }
    } catch(Exception e){
      System.out.println("Problem with TotOrdered.dat file " + e);
    }
    // MUST: You'll need to start all the procsses at once here.
  }
}

class Message {
  int ProcessID;
  int MsgNumber;
  String MsgType;      // "ack" or "msg"
  float MsgTimeOfSend; // Logical Time, TimeStamp!
  int MsgActionTime;   // Local:timer: Delayed send time OR Time to execute, priority sort field
  int AckMsgNumber;    // The number of the message being acknowledged
  boolean[] MsgAck = new boolean[4]; // Default is initialize to false. Won't use [0]

  // We've simulated the creation of new messages to be processed by placing them in
  // a time-sorted queue:
  void CheckNewMessage (){
    Message m = TotOrdered.NewMessageQueue.peek();
    if (m != null && m.MsgActionTime <= TotOrdered.LocalTimer){ // Java short-circuit eval of condition
      m = TotOrdered.NewMessageQueue.remove();
      if (m.MsgType == "msg"){          // This is our initiated message, so:
	m.SendMessage();                // Send to other procs. Add to Wait-For-Acks queue:
	EnqueueFakeAck(m.MsgNumber, 2); // Fake ack from p2
	EnqueueFakeAck(m.MsgNumber, 3); // Fake ack from p3
      } else{
	m.ReceiveMessage();
      }
    }
  }

  void EnqueueFakeAck(int MsgNum, int ProcID){
    Message m = new Message();
    m.MsgNumber = TotOrdered.MsgNumberCounter++; // New message number
    m.AckMsgNumber = MsgNum; // The number of the message being acknowledged.
    m.ProcessID = ProcID;
    m.MsgType = "ack";
    // Faking the logical time of the other (sending) process:
    m.MsgTimeOfSend = (float) ProcID / 10 + (float) TotOrdered.LogicalTime +
      (float) TotOrdered.rand.nextInt(5);
    // Faking the time when acknowledgements of the message arrive:
    m.MsgActionTime = TotOrdered.LocalTimer + TotOrdered.rand.nextInt(2 * TotOrdered.MAXDELAY);
    SOP("[FakeAck] AckMsgNum: " + m.MsgNumber + ", Ack for msg: " + 
		       m.AckMsgNumber + ", TimeOfSend: " + m.MsgTimeOfSend + ", Proc ID: " + m.ProcessID +
		       ", LocalActionTime: " + m.MsgActionTime);
    TotOrdered.NewMessageQueue.add(m);
  }


  // Send the local messages to the other processes:
  void SendMessage(){ // Add random latency delay then send.
    this.MsgAck[TotOrdered.ProcessID] = true; // Acknowledge this process has received.
    // This is the time stamp, 20.1, or 20.2, etc. to avoid ties:
    this.MsgTimeOfSend = (float) TotOrdered.ProcessID / 10 + (float) TotOrdered.LogicalTime;
    SOP("[Send] ProcessID: " + TotOrdered.ProcessID +
	" Time stamp: " + (float) this.MsgTimeOfSend);
    // MUST: Add some random latency delay to the arrival time at the other end:
    // Ordinarily should make a COPY of this message and add to local queue with current time
    // Here we delay Msgs radomly so that some arrive before their acks, to make it interesting:
    this.MsgActionTime = TotOrdered.LocalTimer + TotOrdered.rand.nextInt(TotOrdered.MAXDELAY);
    TotOrdered.SendQueue.add(this); // Place in latency faking queue to send to other procs
    TotOrdered.WaitForAcksQueue.add(this); // Place into the local ack-waiting queue.

    // PrintQueue("Send Q: ", TotOrdered.SendQueue);  // print queue if you like
    // PrintQueue("Wait4Acks Q: ", TotOrdered.WaitForAcksQueue); // print queue if you like
  }

  void LowLevelSend(){ // Acutally send the message to all other processes
    Message m = TotOrdered.SendQueue.peek();
    if (m != null){
      if (m.MsgActionTime < TotOrdered.LocalTimer){
	RealSend(TotOrdered.SendQueue.remove());
      }
    }
  }

  void RealSend(Message m){
    SOP("[RealSend] Msg " + m.MsgNumber + " has arrived at remote processes after delay.");
  }

    /* Check to see if all acknowledgements are in, if so send to application:
       Each local message its own boolean array with three slots (slot 0 not used),
       that represent the three processes and their acknowledgement of the message.

       If the message belonged to Process 1, and no acks had been
       received the array would look like this a[0,1,0,0]  where the element
       ONE, is set to true because we own the message so we don't need to ack it.  
       If you get a response from process three: [0,1,0,1] elements 1 and 3
       are set to true. The process id's coordinate with the array element
       position.  process 1 is at a[1] process 2 is at a[2].  Once a[1] a[2] and
       a[3] are all set to true, [0,1,1,1], you can send the message to the
       application layer. Thanks Christina for comment! */

  void AckCheck(){ 
    PriorityQueue<Message> TempAckQueue =  
      new PriorityQueue<Message>(TotOrdered.MaxQueue, TotOrdered.comparator);
    
    Message m = TotOrdered.WaitForAcksQueue.poll();
    while(m != null){
      if (m.MsgAck[1] && m.MsgAck[2] && m.MsgAck[3]){ // Not sure I ever got to this code, test?
	TotOrdered.ExecuteQueue.add(m); // Lamport requires all packets from one Proc arrive same order
      }
      else{
	TempAckQueue.add(m);
      }
      m = TotOrdered.WaitForAcksQueue.poll();
    }
    TotOrdered.WaitForAcksQueue = TempAckQueue;
    // PrintQueue("WaitForAcksQ: ", WaitForAcksQueue); // Take a look at queue if you like
  }

  // Messages are created locally, and received. Acknowledgements (this dummy program) and
  // messages (full implementation) arrive from other processes. They are processed here.
  boolean ReceiveMessage(){
    boolean AckedTheMessage = false;

    if (TotOrdered.LogicalTime < this.MsgTimeOfSend){     // update LogicalTime if needed
      SOP("[Received] Updating Logical Time from " + TotOrdered.LogicalTime + " to " + 
	  ((int) this.MsgTimeOfSend+1));
      TotOrdered.LogicalTime = (int) this.MsgTimeOfSend + 1;
    }

    if (this.MsgType == "msg"){
      // MUST: We would be receiving messages from other procs here so..
      // add message to NewMessage queue.
      return true; // this standalone program will never get here.
    }

    if (this.MsgType == "ack"){
      SOP("[Received] Ack Message Number: " + this.MsgNumber + " for " + this.AckMsgNumber +
	  ", TimeOfSend: " + this.MsgTimeOfSend + ", by proc: " + this.ProcessID);

      PriorityQueue<Message> TempWaitForAcksQueue =  
	new PriorityQueue<Message>(TotOrdered.MaxQueue, TotOrdered.comparator);
      
      Message m = TotOrdered.WaitForAcksQueue.poll(); // Check for empty
      while(m != null){
	if(m.MsgNumber == this.AckMsgNumber){
	  SOP("[Received] Process " + this.ProcessID + " has sent ack for message " + m.MsgNumber);
	  m.MsgAck[this.ProcessID] = true; // note that sender has ack'ed this message.
	  if (m.MsgAck[1] && m.MsgAck[2] && m.MsgAck[3]){
	    TotOrdered.ExecuteQueue.add(m); // fully ack'ed so add to execute queue
	  }
	  else{
	    TempWaitForAcksQueue.add(m); // not fully ack'ed so put back in the queue
	  }
	  AckedTheMessage = true;
	}
	else{
	  TempWaitForAcksQueue.add(m);
	}
	m = TotOrdered.WaitForAcksQueue.poll();
      }
      TotOrdered.WaitForAcksQueue = TempWaitForAcksQueue;
      // PrintQueue("Wait for Acks Q: ", TotOrdered.WaitForAcksQueue); // look at queue?
      return AckedTheMessage;
    }
    return false; // will never get here, but compiler wants it.
  }

  // For each ack in the SavedAck queue, send to receive. Imagine P1 sends message M to P2 and P3.
  // P3 sends an ack to P2 that it has got M. Later P2 gets the actual message M, so P2 must save ack.
  void CheckSavedAck(){
    PriorityQueue<Message> TempSavedAckQueue =  
      new PriorityQueue<Message>(TotOrdered.MaxQueue, TotOrdered.comparator);

    Message m = TotOrdered.SavedAckQueue.poll();
    while(m != null){
      if (!m.ReceiveMessage()){ // No luck processing ack
	TempSavedAckQueue.add(m);  // So add back to SaveAck queue, try later
      }
      m = TotOrdered.SavedAckQueue.poll();
    }
    TotOrdered.SavedAckQueue = TempSavedAckQueue;
  }

  // This is a sort of catch-all. You might need to extend the delay. Lamport requires all packets
  // from one Proc arrive same order, so we let them settle here beyond MAXDELAY
  void CheckExecuteQueue(){ 
    PriorityQueue<Message> TempExecuteQueue =  
      new PriorityQueue<Message>(TotOrdered.MaxQueue, TotOrdered.comparator);
    Message m = TotOrdered.ExecuteQueue.poll();
    while(m != null){  // Leave time for order of messages from on proc to settle:
      if (m.MsgActionTime + (2 * TotOrdered.MAXDELAY) + 1 < TotOrdered.LocalTimer){ 
	TempExecuteQueue.add(m);  // So add back to Execute queue, try later
      } 
      else{
	SOP("[Execute] sending msg " + m.MsgNumber + " to Application");
	m.SendToApplication();
      }
      m = TotOrdered.ExecuteQueue.poll();
    }
    TotOrdered.ExecuteQueue = TempExecuteQueue;
  }

  void SendToApplication(){
    // There is no application, so we just print out the message on the console:
    System.out.println("APLICATION has received Message: " + this.MsgNumber + ", Timestamp: " + this.MsgTimeOfSend);
  }

  // System out println if DEBUG is on:
  void SOP(String s){
    if (TotOrdered.DEBUG) 
      System.out.println(TotOrdered.LocalTimer + "/" + TotOrdered.LogicalTime + " " + s);
  }

  // Utility to look at queues:
  void PrintQueue(String s, PriorityQueue<Message> q){
    System.out.println(s + " (size: " + q.size() + ")");

    Iterator<Message> Itr = q.iterator();
    while (Itr.hasNext()){
      Message m = (Message) Itr.next();
      System.out.println("  PID: " + m.ProcessID + ", Msg#:  " + m.MsgNumber + ", TimeOfSend " + m.MsgTimeOfSend +
			 ", ActionTime: " + m.MsgActionTime + ", AckMsg#: " + m.AckMsgNumber + ", acks 1: " +
			 m.MsgAck[1] + ", 2: " + m.MsgAck[2] + ", 3: " + m.MsgAck[3]);
    }
  }
}

// Needed for the priority queue implementation...
// Compare the locl "action time" of the messages for sorting:

class MessageComparator implements Comparator<Message>{
  public int compare(Message Msg1, Message Msg2){
    if (Msg1.MsgActionTime < Msg2.MsgActionTime) return -1;
    if (Msg1.MsgActionTime > Msg2.MsgActionTime) return 1;
    return 0;
  }
}

class LogicalTimeLooper implements Runnable {
  public static boolean TimerControlSwitch = true;
  public static int LogicalTimeIncrement = TotOrdered.ProcessID; // cde is this right?

  public void run(){ // RUNning the Logical Time updater
    if (TotOrdered.DEBUG) System.out.println("In the Time Looper thread");
    int i = 0;

    while (TimerControlSwitch) {
      i++; if (i > 17) TimerControlSwitch = false;
      try{Thread.sleep(50);} catch(InterruptedException ex) {};
      TotOrdered.LocalTimer += 1;
      TotOrdered.LogicalTime = TotOrdered.LogicalTime + LogicalTimeIncrement;

      TotOrdered.Dummy.CheckNewMessage(); // Check for incoming, or self-generated, messages.

      TotOrdered.Dummy.LowLevelSend();  // Actually send after faked inet latency delay
      TotOrdered.Dummy.AckCheck();      // Check to see if all acknowledgements are in.
      TotOrdered.Dummy.CheckSavedAck(); // See if msgs have arrived for saved acks
      TotOrdered.Dummy.CheckExecuteQueue(); // See if msgs have arrived for saved acks
    }
  }
}


