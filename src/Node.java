import java.util.*;
import java.io.*;

/* Class to represent a node. Each node must run on its own thread.*/

public class Node extends Thread {

    private int id;
    private boolean participant = false;
    private boolean leader = false;
    private int leader_id = 0;
    private int fail_round = 0;
    private Network network;

    // Neighbouring nodes
    public List<Node> myNeighbours;

    // Queues for the incoming messages
    public List<String> incomingMsg;

    public Node next_neighbour;

    public Node prev_neighbour;

    public boolean start_election = false;

    public boolean fail_election = false;

    public Node(int id){

        this.id = id;

        myNeighbours = new ArrayList<Node>();
        incomingMsg = new ArrayList<String>();
    }

    public void setLeaderId(int incom_id)
    {
        this.leader_id = incom_id;
    }

    public void setElection(boolean start_election)
    {
        this.start_election = start_election;
    }

    public boolean isLeader()
    {
        return this.leader;
    }

    public Node getNextNode()
    {
        return this.next_neighbour;
    }


    public void setParticipant(boolean condition)
    {
        participant = condition;
    }

    public void setNetwork(Network network)
    {
        this.network = network;
        System.out.println("thread "+id+" has set network");
    }
    // Basic methods for the Node class

    public void setFail_round(int fail_round)
    {
        this.fail_round = fail_round;
    }

    public int getNodeId() {
		/*
		Method to get the Id of a node instance
		*/
		return this.id;
    }

    public boolean isNodeLeader() {
		/*
		Method to return true if the node is currently a leader
		*/
		return this.leader;
    }

    public List<Node> getNeighbors() {
		/*
		Method to get the neighbours of the node
		*/
		return this.myNeighbours;
    }

    public void addNeighbour(Node n) {
		/*
		Method to add a neighbour to a node
		*/
		this.myNeighbours.add(n);
    }



    public void receiveMsg(String m) {
		/*
		Method that implements the reception of an incoming message by a node
		*/
		//这里就单纯receive，在线程启动的时候做运算找最大的，每个participant只会发一次，incoming每次保留最大，所以如果收到两次且第二次和自己incoming里的一样，认一个Leader
        this.incomingMsg.add(m);
        System.out.println(id + "has received message " +  m +" in round "+network.getRound());

    }

    public void sendMsg(String m) {
		/*
		Method that implements the sending of a message by a node.
		The message must be delivered to its recepients through the network.
		This method need only implement the logic of the network receiving an outgoing message from a node.
		The remainder of the logic will be implemented in the network class.
		*/
		if(m.startsWith("FAILELECT"))
        {

        }
		network.addMessage(id, m);
        incomingMsg.clear();
    }

    public void general_elect()
    {
        incomingMsg.sort(Collections.reverseOrder());
        System.out.println(id+" has incoming message "+incomingMsg);

        //if not participant send the max of income and id, set participant
        if(!participant)
        {
            System.out.println(id + "not participant and sends "+incomingMsg.get(0) +" to "+ next_neighbour+" in round "+network.getRound());
            this.sendMsg(Integer.toString(Math.max(Integer.parseInt(incomingMsg.get(0)), id)));
            this.setParticipant(true);
        }
        //if participant, send m.id if m.id bigger or do nothing if m.id smaller
        else {
            if(Integer.parseInt(incomingMsg.get(0))>id)
            {
                System.out.println(id + "participant and sends "+incomingMsg.get(0) +" to "+ next_neighbour+" in round "+network.getRound());
                this.sendMsg(incomingMsg.get(0));
            }
            else if(Integer.parseInt(incomingMsg.get(0))==id)
            {
                this.leader = true;
                this.setLeaderId(id);
                this.sendMsg("LEADER "+id);
            }
        }
    }

    public void fail_elect()
    {
        if(fail_election)
        {
            for ()
        }
    }

    public void run() {
        while (true)
        {
            if(network.getRound()==fail_round)
            {
                network.informNodeFailure(id);
                fail_round = 0;
                System.out.println("This node "+id +" fails and quit");
                break;
            }
            if(leader)
                System.out.println("the leader is "+id +" and this is round"+ network.getRound());
            if(incomingMsg.size() == 0 && participant && start_election)
            {
                System.out.println(id + "start election and send its id to "+ next_neighbour+" in round "+network.getRound());
                this.sendMsg("ELECT "+Integer.toString(id));
                this.start_election = false;
            }
            if(incomingMsg.size() > 0)
            {
                // judge what type of the message and preprocess it
                for (int i=0; i<incomingMsg.size();i++)
                {
                    String message = incomingMsg.get(i);
                    String[] whole_message = message.split("\\s+");
                    if(message.startsWith("ELECT")||message.startsWith("FORWARD"))
                    {
                        System.out.println();
                        incomingMsg.set(i, whole_message[1]);
                    }
                    /*if a message starts with leader, that means that this election has been done
                    even if other sends elect or forward, it will ignore them and send the leader*/
                    else if(message.startsWith("LEADER"))
                    {
                        if(Integer.parseInt(whole_message[1]) == id)
                        {
                            System.out.println(id+" get his own leader message "+whole_message[1] +" and stop election "+" in round "+network.getRound());
                        }
                        else
                        {
                            System.out.println(id+" set its leader "+whole_message[1] +" and forward "+" in round "+network.getRound());
                            this.setLeaderId(Integer.parseInt(whole_message[1]));
                            this.sendMsg("LEADER "+whole_message[1]);
                        }
                        incomingMsg.clear();
                        this.setParticipant(false);
                        break;
                    }

                }

                if(!incomingMsg.isEmpty())
                {
                    general_elect();
                }

            }
            incomingMsg.clear();
            System.out.println(id + "is now waiting"+" in round "+network.getRound());
            try {
                // after send all the message, release the lock and wait for notify
                synchronized(Network.class) {
                    Network.class.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

}