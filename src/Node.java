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
    private int idle_round = 0;
    private List<Integer> elect_round;
    private  boolean networkConn = true;
    private Network network;
    private int all_child_count;

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
        elect_round = new ArrayList<Integer>();
        all_child_count = 0;


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

    public void setFailRound(int fail_round)
    {
        this.fail_round = fail_round;
    }

    public void setElectRound(int elect_round)
    {
        this.elect_round.add(elect_round);
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
        if(m.startsWith("TREECONSTRUCT")||m.startsWith("TREELEADER")||m.startsWith("BREAK"))
            network.addBroadcastMessage(id, m);
        else
        {
            network.addMessage(id, m);
        }

    }

    private void start_general_elect()
    {
        System.out.println(id + " start election and send its id to "+ next_neighbour.getNodeId()+" in round "+network.getRound());
        this.sendMsg("ELECT "+Integer.toString(id));
        this.participant = true;
    }

    private void general_elect(String max_recv)
    {
        /*
		This is the basic election which applies in a complete ring
		*/

        /*
        if not participant send the max of income and id, set participant
        */
        if(!participant)
        {
            System.out.println(id + " not participant and sends "+Math.max(Integer.parseInt(max_recv), id) +" to "+ next_neighbour.getNodeId()+" in round "+network.getRound());
            this.sendMsg("FORWARD "+Math.max(Integer.parseInt(max_recv), id));
            this.setParticipant(true);
        }
        /*
        if participant, send m.id if m.id bigger or do nothing if m.id smaller
        */
        else {
            if(Integer.parseInt(max_recv)>id)
            {
                System.out.println(id + " participant and sends "+max_recv +" to "+ next_neighbour.getNodeId()+" in round "+network.getRound());
                this.sendMsg("FORWARD "+max_recv);
            }
            /*
            if receive the id of himself, set it leader and send leader message
             */
            else if(Integer.parseInt(max_recv)==id)
            {
                this.leader = true;
                this.setLeaderId(id);
                this.sendMsg("LEADER "+id);
                network.appendLog("Leader Node "+id);
            }
        }
    }

    private void start_tree_construct()
    {
        /*
		This is method to send tree construction message.
		*/
        sendMsg("TREECONSTRUCT " + id);
    }

    private void start_tree_elect()
    {
        System.out.println(id + "start tree election and send its id to "+ parent+" in round "+network.getRound());
        this.tree_elect();
        this.has_sent = true;
    }

    private void tree_elect()
    {
        /*
		This is method to send election message in tree.
		*/
        sendMsg("TREEELECT " + parent +" "+ id +" CHILD "+ Integer.toString(all_child_count+1));
    }

    private void informNodeFailure()
    {
        network.informNodeFailure(id);
        fail_round = 0;
        System.out.println("This node "+id +" fails and quit");
    }

    private String get_max_recv_id(String[] whole_message, String max_recv_id)
    {
        String incom_id = whole_message[1];
        if(max_recv_id.isEmpty())
            max_recv_id = incom_id;
        else
        {
            if(max_recv_id.compareTo(incom_id)<0)
                max_recv_id = incom_id;
        }
        return max_recv_id;
    }

    private void send_leader(String[] whole_message)
    {
        String leader_id = whole_message[1];
        if(Integer.parseInt(leader_id) == id)
        {
            System.out.println(id+" get his own leader message "+leader_id +" and stop election "+" in round "+network.getRound());
            this.setParticipant(false);
        }
        else
        {
            System.out.println(id+" set its leader "+leader_id +" and forward "+" in round "+network.getRound());
            this.setLeaderId(Integer.parseInt(leader_id));
            this.sendMsg("LEADER "+leader_id);
        }
        incomingMsg.clear();
        this.setParticipant(false);
    }

    private void deal_tree_construct(String[] whole_message)
    {
        String p_id = whole_message[1];
        if(parent.isEmpty())
        {
            parent = p_id;
            sendMsg("TREEREPLAY "+p_id+" "+ id +" AGREE");
            sendMsg("TREECONSTRUCT " + id);
        }
    }

    private void deal_tree_reply(String[] whole_message)
    {
        /*
        add children if get agree reply
         */
        String child_id = whole_message[2];
        String answer = whole_message[3];
        if(answer.equals("AGREE"))
        {
            System.out.println(id+ " agree "+child_id +" to be his child in round "+ network.getRound());
            children.add(child_id);
        }
    }

    private void deal_tree_elect(String[] whole_message)
    {
        String child_id = whole_message[2];
        all_child_count += Integer.parseInt(whole_message[4]);

        int children_count = children.size();
        int max_id = id;
        children_msg.add(child_id);
        if(children_msg.size() == children_count)
        {
           System.out.println("Node "+ id +" have children count" +Integer.toString(all_child_count+1));
            for(String id: children_msg)
            {
                if(Integer.parseInt(id)>max_id)
                    max_id = Integer.parseInt(id);
            }
            if(parent.isEmpty()&&!children.isEmpty())
            {
                /*
                if root find the size of tree is smaller than network, it means some nodes is separated
                then it will send break message to all nodes to quit the network.
                 */
                if((all_child_count+1)!=network.getNetSize())
                {
                    System.out.println("Network is disconnected! Send message to break!");
                    String break_msg = "BREAK";
                    for (String child : children)
                    {
                        break_msg = break_msg.concat(" "+child);
                    }
                    sendMsg(break_msg);
                    networkConn = false;
                }
                else
                {
                    System.out.println("get the new leader "+ max_id);
                    network.appendLog("Leader Node "+max_id);
                    String leader_msg = "TREELEADER "+max_id;
                    for (String child : children)
                    {
                        leader_msg = leader_msg.concat(" "+child);
                    }
                    this.leader_id = max_id;
                    this.has_sent = false;
                    all_child_count = 0;
                    children.clear();
                    sendMsg(leader_msg);
                }
            }
            else
                sendMsg("TREEELECT " + parent +" " + max_id + " CHILD "+ Integer.toString(all_child_count+1));
            children_msg.clear();
        }
    }

    private void deal_tree_leader(String[] whole_message)
    {
        int tree_leader = Integer.parseInt(whole_message[1]);
        this.leader_id = tree_leader;
        this.has_sent = false;
        this.all_child_count = 0;
        String new_leader_msg = "TREELEADER "+tree_leader;
        for (String child : children)
        {
            new_leader_msg = new_leader_msg.concat(" "+child);
        }
        parent = "";
        if(!children.isEmpty())
        {
            children.clear();
            sendMsg(new_leader_msg);
        }
        else
        {
            System.out.println("leaf node "+id+" has received leader and set leader id "+leader_id);
        }

    }

    private void reset_neighbour(String[] whole_message)
    {
        /*
        When the neighbour fails, remove the neighbour from its neighbour list
         */
        String failed_id = whole_message[1];
        Iterator<Node> iter = myNeighbours.iterator();

        while (iter.hasNext()) {
            Node node = iter.next();

            if (node.getNodeId()==Integer.parseInt(failed_id))
                iter.remove();
            if(this.next_neighbour==node)
            {
                this.next_neighbour = null;
            }
            else if(this.prev_neighbour==node)
            {
                this.prev_neighbour = null;
            }
        }
        if(myNeighbours.size()==0)
            networkConn = false;
    }

    private void quit_network(String[] whole_message)
    {
        String new_break_msg = "BREAK";
        for (String child : children)
        {
            new_break_msg = new_break_msg.concat(" "+child);
        }
        sendMsg(new_break_msg);
        networkConn = false;
    }

    public void run() {
        while (true)
        {
            try {
                String max_recv_id="";
                /*
                this is to count the round a node does not receive message
                if a node has a parent but doesn't have children and doesn't receive any message
                for more than one round, then it could be the leaf node, cause all its TREECONSTRUCT
                message are refused
                 */
                if(incomingMsg.size() == 0)
                    idle_round++;
                else
                    /*
                    if a node receive message after one round, then clear the idle count and recount
                     */
                    idle_round = 0;

                if(network.getRound()==fail_round)
                {
                    informNodeFailure();
                    break;
                }

                if(this.elect_round.contains(network.getRound()))
                {
                    start_general_elect();
                }

                /*
                if no construct and reply message comes to leaf node. Leaf node start election.
                 */
                if(idle_round > 1 && !parent.isEmpty() && children.isEmpty() && !has_sent)
                {
                    idle_round = 0;
                    start_tree_elect();
                }

                if(incomingMsg.size() > 0)
                {
                    /*
                    judge what type of the message and preprocess it
                    */
                    for (int i=0; i<incomingMsg.size();i++) {
                        String message = incomingMsg.get(i);
                        String[] whole_message = message.split("\\s+");
                        /*
                        if a message starts with leader, that means that this election has been done
                        even if other sends elect or forward, it will ignore them and send the leader
                        */
                        if (message.startsWith("LEADER")) {
                            send_leader(whole_message);
                            break;
                        }

                        /*
                        find the max id in this round
                         */
                        else if (whole_message[0].equals("ELECT") || whole_message[0].equals("FORWARD")) {
                            max_recv_id = get_max_recv_id(whole_message, max_recv_id);
                        } else if (whole_message[0].equals("FAIL")) {
                            /*
                            if only receive a FAIL + id message, just remove the neighbour
                            if receive FAIL + id + START ELECT, then the node could start to build the tree
                            if no one has written the Part B title, then this is the first fail and write the title
                             */
                            reset_neighbour(whole_message);
                            if (whole_message.length > 2)
                            {
                                if (!network.get_partbwritten()) {
                                    network.write_partb();
                                }
                                start_tree_construct();
                            }
                        } else if (whole_message[0].equals("TREECONSTRUCT")) {
                            deal_tree_construct(whole_message);
                        } else if (whole_message[0].equals("TREEREPLAY")) {
                            deal_tree_reply(whole_message);
                        } else if (whole_message[0].equals("TREEELECT")) {
                            deal_tree_elect(whole_message);
                        } else if (whole_message[0].equals("TREELEADER")) {
                            deal_tree_leader(whole_message);
                        } else if (whole_message[0].equals("BREAK")) {
                            quit_network(whole_message);
                        }

                    }

                    if(!networkConn)
                        break;

                    /*
                    After deal with all specific message, deal the general message which
                    are to be sent to next neighbour
                     */
                    if(!max_recv_id.isEmpty())
                    {
                        general_elect(max_recv_id);
                    }

                }
                incomingMsg.clear();


                /*
                after send all the message, release the lock and wait for notify
                 */
                synchronized(Network.class) {
                    Network.class.wait();
                }
            } catch (InterruptedException e) {
                break;
            }
        }

    }

}