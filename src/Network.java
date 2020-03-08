import java.io.*;
import java.util.*;

/*
Class to simulate the network. System design directions:

- Synchronous communication: each round lasts for 20ms
- At each round the network receives the messages that the nodes want to send and delivers them
- The network should make sure that:
	- A node can only send messages to its neighbours
	- A node can only send one message per neighbour per round
- When a node fails, the network must inform all the node's neighbours about the failure
*/

public class Network {

    private static List<Node> nodes;
    private int round;
    private int period = 20;
    private Map<Integer, String> msgToDeliver; //Integer for the id of the sender and String for the message
    private Map<Integer, String> treeBoardcastMsg; //Message slot for broadcast
    private List<Integer> elect_round;
    private Map<Integer, ArrayList<String>> elect_nodes;
    private List<Integer> fail_round;
    private Map<Integer, String> fail_node;
    private Map<String, Node> savedNode;
    private int totalThread;
    private boolean has_written_partb;
    private int max_round;
    int last_opt_round;
    public final String lock = "";

    public void NetSimulator() throws IOException, InterruptedException {
        //list to store all nodes
        nodes = new ArrayList<Node>();
        //map to store what to deliver each round
        msgToDeliver = new HashMap<Integer, String>();

        treeBoardcastMsg = new HashMap<Integer, String>();
        //map to match the id to the node
        savedNode = new HashMap<String, Node>();
        //list to store all elect rounds
        elect_round = new ArrayList<Integer>();
        //map to store which nodes start to elect on specific round
        elect_nodes = new HashMap<Integer, ArrayList<String>>();
        //list to store all elect rounds
        fail_round = new ArrayList<Integer>();
        //map to store which nodes start to elect on specific round
        fail_node = new HashMap<Integer, String>();

        has_written_partb = false;


        round = 1;

        this.parseFile("input/ds_graph.txt");
        this.parseElectFile("input/ds_elect.txt");

        for (Node node : nodes) {
            node.setNetwork(this);
        }

        this.appendLog("\nPart A");
        startElection();
    }

    public synchronized boolean get_partbwritten()
    {
        return this.has_written_partb;
    }

    public synchronized void write_partb()
    {
        appendLog("\nPart B");
        this.has_written_partb = true;
    }

    public void startElection(){
        long start_time = System.currentTimeMillis();
        boolean thing_todo = false;
        int leader_node = -1;
        ThreadGroup threadGroup;

        /*
        Code to call methods for parsing the input file, initiating the system and producing the log can be added here.
        */

        while (round<max_round)
        {
            System.out.println("This is round"+round+"\n");
            thing_todo = false;
            start_time = System.currentTimeMillis();

            while ((System.currentTimeMillis() - start_time) < period)
            {
                if(!thing_todo &&round==1)
                {
                    for (Node node : nodes)
                    {
                        node.start();
                    }
                }
                if(!thing_todo && round>1)
                {
                    deliverMessages();
                    deliverTreeBroadcastMessages();
                    synchronized (Network.class)
                    {
                        Network.class.notifyAll();
                    }
                }
                if(!thing_todo)
                {
                    threadGroup = Thread.currentThread().getThreadGroup();
                    while(threadGroup.getParent() != null){
                        threadGroup = threadGroup.getParent();
                    }
                    totalThread = threadGroup.activeCount();
                }
                thing_todo=true;
            }
            if(totalThread==6)
            {
                System.out.println("all the nodes quit, network quits");
                break;
            }
            round++;
        }
        for (Node node:nodes)
        {
            node.interrupt();
        }
        appendLog("simulation completed");
    }

    public int getRound()
    {
        return this.round;
    }

    public int getNetSize(){ return nodes.size();}

    private void parseFile(String filename) throws IOException {
   		/*
   		Code to parse the file can be added here. Notice that the method's descriptor must be defined.
   		*/

        FileInputStream inputStream = new FileInputStream(filename);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        String str = null;

        String previous_node = null;

        Node start_node = null;

        while((str = bufferedReader.readLine()) != null)
        {

            String [] node_in_line = str.split("\\s+");

            Node head_node;

            //add or get head node
            if(!savedNode.containsKey(node_in_line[0]))
            {
                head_node = new Node(Integer.parseInt(node_in_line[0]));
                savedNode.put(node_in_line[0],head_node);
                nodes.add(head_node);
            }
            else
            {
                head_node = savedNode.get(node_in_line[0]);
            }

            /*
            add next and previous node
             */
            if(start_node==null)
                start_node = head_node;

            if(previous_node!=null)
            {
                savedNode.get(previous_node).next_neighbour = head_node;
                head_node.prev_neighbour = savedNode.get(previous_node);
                if(!savedNode.get(previous_node).getNeighbors().contains(head_node))
                    savedNode.get(previous_node).addNeighbour(head_node);
                if(!head_node.getNeighbors().contains(savedNode.get(previous_node)))
                    head_node.addNeighbour(savedNode.get(previous_node));
            }
            previous_node = node_in_line[0];

            //add neighbours
            Node nei_node;
            for(int i=1;i<node_in_line.length;i++)
            {
                if(!savedNode.containsKey(node_in_line[i]))
                {
                    nei_node = new Node(Integer.parseInt(node_in_line[i]));
                    savedNode.put(node_in_line[i],nei_node);
                    nodes.add(nei_node);

                }
                else
                {
                    nei_node = savedNode.get(node_in_line[i]);
                }
                head_node.addNeighbour(nei_node);
            }

        }

        //link the tail and the head of the ring
        if(start_node!=null)
        {
            savedNode.get(previous_node).next_neighbour = start_node;
            start_node.prev_neighbour = savedNode.get(previous_node);
            start_node.addNeighbour(savedNode.get(previous_node));
            savedNode.get(previous_node).addNeighbour(start_node);
        }

        //close
        inputStream.close();
        bufferedReader.close();
    }

    private void parseElectFile(String filename) throws IOException {
   		/*
   		Code to parse the file can be added here. Notice that the method's descriptor must be defined.
   		*/

        FileInputStream inputStream = new FileInputStream(filename);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        String str;

        String elect_parse = "ELECT";
        
        String fail_parse = "FAIL";


        while((str = bufferedReader.readLine()) != null) {

            String[] element_in_line = str.split("\\s+");

            String opt = element_in_line[0];

            String opt_round = element_in_line[1];

            last_opt_round = Integer.parseInt(opt_round);

            if(opt.equals(elect_parse))
            {
                for (int i=2; i<element_in_line.length;i++)
                {
                    savedNode.get(element_in_line[i]).setElectRound(Integer.parseInt(opt_round));
                }
            }

            if(opt.equals(fail_parse))
            {
                savedNode.get(element_in_line[2]).setFailRound(Integer.parseInt(opt_round));
            }

        }

        /*
        calculate the max round for the general elect and fail elect(by tree algorithm)
        then quit the all the threads after max round
         */
        max_round = last_opt_round+3*getNetSize()+1;

        //close
        inputStream.close();
        bufferedReader.close();
    }

    public synchronized void addMessage(int id, String m){
		/*
		At each round, the network collects all the messages that the nodes want to send to their neighbours.
		Implement this logic here.
		*/
        this.msgToDeliver.put(id, m);
    }

    public synchronized void addBroadcastMessage(int id, String m){
		/*
		At each round, the network collects all the messages that the nodes want to send to their neighbours.
		Implement this logic here.
		*/
        this.treeBoardcastMsg.put(id, m);
    }

    public synchronized void deliverTreeBroadcastMessages() {
		/*
		This is to broadcast message.
		0 means no message to send or the sender will send the message to all his neighbours
		*/

        try {
            for (Integer key : treeBoardcastMsg.keySet()) {
                String message_to_deliver = treeBoardcastMsg.get(key);
                Node sender = savedNode.get(Integer.toString(key));

                /*
                If message equals to zero, it means no broadcast message will be sent in this round
                */
                if(message_to_deliver.equals(Character.toString('0')))
                {
                    continue;
                }
                if(message_to_deliver.startsWith("TREECONSTRUCT"))
                {
                    /*
                    tree construct
                     */
                    Node parent = savedNode.get(sender.parent);
                    for (Node node:sender.getNeighbors())
                    {
                        if(node!=parent)
                            node.receiveMsg(message_to_deliver);
                    }
                }
                else if(message_to_deliver.startsWith("TREELEADER"))
                {
                    /*
                    tree leader is to be sent to child to inform them the new leader
                     */
                    String[] whole_message = message_to_deliver.split("\\s+");
                    for (int i=2; i<whole_message.length;i++)
                    {
                        Node child = savedNode.get(whole_message[i]);
                        child.receiveMsg(message_to_deliver);
                    }
                }
                else  if(message_to_deliver.startsWith("BREAK"))
                {
                    /*
                    break message is sent to child to let them quit the network
                     */
                    String[] whole_message = message_to_deliver.split("\\s+");
                    for (int i=1; i<whole_message.length;i++)
                    {
                        Node child = savedNode.get(whole_message[i]);
                        child.receiveMsg(message_to_deliver);
                    }
                }
                treeBoardcastMsg.put(key, Character.toString('0'));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public synchronized void deliverMessages() {
		/*
		This is to deliver message that has single destination.
		0 means no message to send or the sender will send the message to all his neighbours.
		*/
        try {

            for (Integer key : msgToDeliver.keySet()) {

                String message_to_deliver = msgToDeliver.get(key);
                String[] whole_message = message_to_deliver.split("\\s+");
                Node sender = savedNode.get(Integer.toString(key));
                if(message_to_deliver.equals(Character.toString('0')))
                {
                    continue;
                }
                else if(message_to_deliver.startsWith("TREEREPLAY")||message_to_deliver.startsWith("ACK"))
                {
                    String node_to_send = whole_message[1];
                    savedNode.get(node_to_send).receiveMsg(message_to_deliver);
                }
                else if(message_to_deliver.startsWith("TREEELECT"))
                {
                    Node parent = savedNode.get(sender.parent);
                    if(parent!=null)
                        parent.receiveMsg(message_to_deliver);
                }
                else{
                    sender.getNextNode().receiveMsg(message_to_deliver);
                }
                msgToDeliver.put(key, Character.toString('0'));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public synchronized void informNodeFailure(int id) {
		/*
		Method to inform the neighbours of a failed node about the event.
		*/
        Node failed_node = savedNode.get(Integer.toString(id));
        for (int i=0; i<failed_node.myNeighbours.size();i++)
        {
            if(i==0)
            {
                failed_node.myNeighbours.get(i).receiveMsg("FAIL " + id + " START ELECT");
            }
            else
                failed_node.myNeighbours.get(i).receiveMsg("FAIL " + id);
        }
        savedNode.remove(Integer.toString(id));
        nodes.remove(failed_node);

    }

    public synchronized void appendLog(String newLog)
    {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("log.txt", true));
            bw.write(newLog);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) throws IOException, InterruptedException {
		/*
		Your main must get the input file as input.
		*/
        Network netWork = new Network();
        netWork.NetSimulator();
    }

}

