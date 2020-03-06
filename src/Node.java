import com.sun.org.apache.xerces.internal.xs.StringList;

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

    public String parent;

    public List<String> children;

    public List<String> children_msg;

    private boolean has_sent = false;

    public boolean start_election = false;

    public Node(int id){

        this.id = id;
        parent = "";
        children = new ArrayList<String>();
        children_msg = new ArrayList<String>();


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

        this.incomingMsg.add(m);
        System.out.println(id + "has received message " + m +" in round "+network.getRound());

    }

    public void sendMsg(String m) {
		/*
		This is send method. For broadcast message, it sends to broadcast slot in network.
		For single destination message, it sends to direct deliver slot in network.
		*/
        if(m.startsWith("TREECONSTRUCT")||m.startsWith("TREELEADER"))
            network.addBroadcastMessage(id, m);
        else
        {
            network.addMessage(id, m);
        }

    }


    public void general_elect(String max_recv)
    {
        /*
		This is the basic election which applies in a complete ring
		*/
        System.out.println(id+" has incoming message "+max_recv);

        /*
        if not participant send the max of income and id, set participant
        */
        if(!participant)
        {
            System.out.println(id + "not participant and sends "+max_recv +" to "+ next_neighbour+" in round "+network.getRound());
            this.sendMsg("ELECT "+Integer.toString(Math.max(Integer.parseInt(max_recv), id)));
            this.setParticipant(true);
        }
        /*
        if participant, send m.id if m.id bigger or do nothing if m.id smaller
        */
        else {
            if(Integer.parseInt(max_recv)>id)
            {
                System.out.println(id + "participant and sends "+max_recv +" to "+ next_neighbour+" in round "+network.getRound());
                this.sendMsg("FORWARD "+max_recv);
            }
            else if(Integer.parseInt(max_recv)==id)
            {
                this.leader = true;
                this.setLeaderId(id);
                this.setParticipant(false);
                this.sendMsg("LEADER "+id);
            }
        }
    }

    public void tree_construct()
    {
        /*
		This is method to send tree construction message.
		*/
        sendMsg("TREECONSTRUCT " + id);
    }


    public void tree_elect()
    {
        /*
		This is method to send election message in tree.
		*/
        sendMsg("TREEELECT " + parent +" "+ id);
    }

    public void run() {
        while (true)
        {
            if(network.getRound()==108)
                System.out.println(id+" !!! "+parent);
            String max_recv_id="";
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

            if(incomingMsg.size() == 0 && !parent.isEmpty() && children.isEmpty() && !has_sent)
            {
                System.out.println(id + "start tree election and send its id to "+ parent+" in round "+network.getRound());
                this.tree_elect();
                this.has_sent = true;
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
                        if(max_recv_id.isEmpty())
                            max_recv_id = whole_message[1];
                        else
                        {
                            if(max_recv_id.compareTo(whole_message[1])<0)
                                max_recv_id = whole_message[1];
                        }
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
                    else if(whole_message[0].equals("FAIL"))
                    {
                        tree_construct();
                    }

                    else if(whole_message[0].equals("TREECONSTRUCT"))
                    {
                        String p_id = whole_message[1];
                        if(parent.isEmpty())
                        {
                            parent = p_id;
                            sendMsg("TREEREPLAY "+p_id+" "+ id +" AGREE");
                            sendMsg("TREECONSTRUCT " + id);
                        }
//                        else if(!parent.isEmpty())
//                            sendMsg("TREEREPLAY "+p_id+" "+ id +" DISAGREE");
                    }
                    else if(whole_message[0].equals("TREEREPLAY"))
                    {
                        String child_id = whole_message[2];
                        System.out.println(whole_message[3]);
                        if(whole_message[3].equals("AGREE"))
                        {
                            System.out.println(id+ " agree "+child_id +" in round "+ network.getRound());
                            children.add(child_id);
                        }
                    }
                    else if(whole_message[0].equals("TREEELECT"))
                    {
                        String child_id = whole_message[2];
                        int children_count = children.size();
                        int max_id = id;
                        children_msg.add(child_id);
                        if(children_msg.size() == children_count)
                        {
                            if(id==1)
                            {
                                System.out.println("1 has "+ children_msg);
                            }
                            for(String id: children_msg)
                            {
                                if(Integer.parseInt(id)>max_id)
                                    max_id = Integer.parseInt(id);
                            }
                            if(parent.isEmpty())
                            {
                                System.out.println("get the new leader "+ max_id);

                            }
                            else
                                sendMsg("TREEELECT " + parent +" " + max_id);
                            children_msg.clear();
                        }
                    }

                }

                if(!max_recv_id.isEmpty())
                {
                    general_elect(max_recv_id);
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