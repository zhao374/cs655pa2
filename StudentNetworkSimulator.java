import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    private int sendbase=0;
    private Queue<Packet> sent_unack_buffer_a=new LinkedList<Packet>();
    private Queue<Packet> unsent_buffer_a=new LinkedList<Packet>();
    private int seq_num_a=0;
    private int lastSeq=0;
    private int original=0;
    private int retransmission=0;
    private int delivered=0;
    private int ack_sent=0;
    private int corrupted=0;
    private double rtt=0.0;
    private double communication=0.0;

    private int recievebase=0;
    private LinkedList<Packet> recieved_buffer_b=new LinkedList<Packet>();

    private void increase_seqnum(){
        if (seq_num_a + 1 == LimitSeqNo) {
            seq_num_a = 0;
        }
        else {
            seq_num_a++;
        }
    }
    private void increase_sendbase(){
        if (sendbase + 1 == LimitSeqNo) {
            sendbase = 0;
        }
        else {
            sendbase++;
        }
    }
    private int compute_checksum(Packet packet){
        int toret=0;
        for(int i=0;i<packet.getPayload().length();i++){
            toret+=packet.getPayload().charAt(i);
        }
        toret+=packet.getSeqnum();
        toret+=packet.getAcknum();
        return toret;
    }
    private boolean verify_checksum(Packet packet){
        int checksum=compute_checksum(packet);
        if(checksum==packet.getChecksum()){
            return true;
        }else{
            return false;
        }
    }
    private boolean remove_from_sent(int seqnu){
        boolean found=false;
        for(Packet a:sent_unack_buffer_a){
            if(a.getSeqnum()==seqnu){
                found=true;
                break;
            }
        }
        if(found){
            boolean reach=true;
            while(reach){
                if(sent_unack_buffer_a.remove().getSeqnum()==seqnu){
                    reach=false;
                }
            }
        }
        return found;
    }
    private boolean inside_send_window(int seqnu){
        boolean found=false;
        for(int i=0;i<WindowSize;i++){
            if((sendbase+i)%(LimitSeqNo)==seqnu){
                found=true;
            }
        }
        return found;
    }
    private boolean inside_recieve_window(int seqnu){
        boolean found=false;
        for(int i=0;i<WindowSize;i++){
            if((recievebase+i)%(LimitSeqNo)==seqnu){
                found=true;
            }
        }
        return found;
    }

    private boolean inside_recieve_window2(int seqnu){
        boolean found=false;
        for(int i=1;i<WindowSize+1;i++){
            int comp;
            if((recievebase-i)<0){
                comp=LimitSeqNo+(recievebase-i);
            }else{
                comp=recievebase-i;
            }
            if(comp==seqnu){
                found=true;
            }
        }
        return found;
    }
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    double startTime=0;
    double startTime2=0;
    protected void aOutput(Message message)
    {
        original+=1;
        Packet packet=new Packet(seq_num_a,0,0,message.getData());
        packet.setChecksum(compute_checksum(packet));
        if(sent_unack_buffer_a.size()<WindowSize){
            startTime=getTime();
            startTime2=getTime();
            toLayer3(A,packet);
            sent_unack_buffer_a.add(packet);
            startTimer(A,this.RxmtInterval);
        }else{
            unsent_buffer_a.add(packet);
        }
        increase_seqnum();
        return;


    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        boolean checksum=verify_checksum(packet);

        if(sent_unack_buffer_a.size()==0){
            return;
        }
        if(checksum){
            stopTimer(A);
            rtt+=(getTime()-startTime);
            remove_from_sent(packet.getSeqnum());
            if(sent_unack_buffer_a.size()<WindowSize && unsent_buffer_a.size()>0){
                startTime=getTime();
                startTime2=getTime();
                Packet tosent=unsent_buffer_a.poll();
                toLayer3(A,tosent);
                unsent_buffer_a.remove(0);
                sent_unack_buffer_a.add(tosent);
                startTimer(A,this.RxmtInterval);
            }


        }else{
            corrupted++;
        }

    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt()
    {
        stopTimer(A);
        toLayer3(A,sent_unack_buffer_a.peek());
        System.out.println("resending"+sent_unack_buffer_a.peek().getPayload());
        startTimer(A,this.RxmtInterval);
        retransmission++;
        communication+=(getTime()-startTime2);
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        sendbase=0;
    }
    ArrayList<Integer> rec=new ArrayList<Integer>();
    ArrayList<String> rec1=new ArrayList<String>();
    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        boolean checksum=verify_checksum(packet);

        if(checksum){
            if(inside_recieve_window(packet.getSeqnum())){
                recieved_buffer_b.add(packet);
                if (packet.getSeqnum() == recievebase) {
                    int i = packet.getSeqnum();
                    boolean found = true;
                    while(found){
                        found=false;
                        ArrayList<Packet> buffercp=new ArrayList<Packet>();
                        for(Packet k:recieved_buffer_b){
                            buffercp.add(k);
                        }
                        for(Packet pck : buffercp) {
                            if(pck.getSeqnum() == recievebase) {
                                lastSeq = recievebase;
                                found = true;
                                delivered++;
                                toLayer5(pck.getPayload());
                                recieved_buffer_b.remove(pck);
                                recievebase = (recievebase + 1) % LimitSeqNo;
                                break;
                            }
                        }
                    }
                    Packet ackPack=new Packet(lastSeq,1,0);
                    ackPack.setChecksum(compute_checksum(ackPack));
                    toLayer3(B, ackPack);
                    ack_sent+=1;
                }
            }else{
                Packet toret=new Packet(packet.getSeqnum(),1,0);
                toret.setChecksum(compute_checksum(toret));
                ack_sent+=1;
                toLayer3(B,toret);
                return;
            }

        }else{
            corrupted++;
            return;
        }

            }


    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        recievebase=0;
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + original);
        System.out.println("Number of retransmissions by A:" + retransmission);
        System.out.println("Number of data packets delivered to layer 5 at B:" +delivered);
        System.out.println("Number of ACK packets sent by B:" + ack_sent);
        System.out.println("Number of corrupted packets:" + corrupted);
        double corr=corrupted;
        double ratio0=(retransmission-corr)/(original+retransmission+ack_sent);
        System.out.println("Ratio of lost packets:" + ratio0 );

        double ratio1=(corr)/((original+retransmission)+ack_sent-(retransmission-corrupted));
        System.out.println("Ratio of corrupted packets:" + ratio1);
        System.out.println("Average RTT:" + (rtt/original));
        System.out.println("Average communication time:" + (communication+rtt)/(original));
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

}