import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
    private Map<Integer, String> treeBoardcastMsg; //Integer for the id of the sender and String for the message
    private List<Integer> elect_round;
    private Map<Integer, ArrayList<String>> elect_nodes;
    private List<Integer> fail_round;
    private Map<Integer, String> fail_node;
    private Map<String, Node> savedNode;
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


        round = 1;

        this.parseFile("input/ds_graph.txt");
        this.parseElectFile("input/ds_fail.txt");

        for (Node node : nodes) {
            node.setNetwork(this);
        }

        startElection();
    }

    public void startElection(){
        long start_time = System.currentTimeMillis();
        boolean thing_todo = false;
        int leader_node = -1;

        /*
        Code to call methods for parsing the input file, initiating the system and producing the log can be added here.
        */
        System.out.println("This is round"+round+"\n");
        while (round<150)
        {
            thing_todo = false;
            start_time = System.currentTimeMillis();
            while ((System.currentTimeMillis() - start_time) < period)
            {
                if(!thing_todo &&elect_round.contains(round))
                {
                    System.out.println("This is elect round"+round);
                    for (int i=0; i<elect_nodes.get(round).size();i++)
                    {
                        savedNode.get(elect_nodes.get(round).get(i)).setParticipant(true);
                        savedNode.get(elect_nodes.get(round).get(i)).setElection(true);
                    }
                }
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
                thing_todo=true;
            }
            round++;
        }
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
                System.out.println("node "+previous_node+" and "+node_in_line[0]+" becomes neighbours");
            }
            previous_node = node_in_line[0];

            //add neighbours
            Node nei_node;
            for(int i=1;i<node_in_line.length;i++)
            {
                if(!savedNode.containsKey(node_in_line[i]))
                {
                    System.out.println(node_in_line[i]+" is not in hash table");
                    nei_node = new Node(Integer.parseInt(node_in_line[i]));
                    savedNode.put(node_in_line[i],nei_node);
                    nodes.add(nei_node);

                }
                else
                {
                    System.out.println(node_in_line[i]+" is already in hash table");
                    nei_node = savedNode.get(node_in_line[i]);
                }
                System.out.println("node "+node_in_line[0]+" add neighbor node "+node_in_line[i]);
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
            System.out.println("node "+previous_node+" and "+start_node+" becomes neighbours");
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

            if(element_in_line[0].equals(elect_parse))
            {
                elect_round.add(Integer.parseInt(element_in_line[1]));

                ArrayList<String> temp = new ArrayList<String>();

                for (int i = 2; i < element_in_line.length; i++) {

                    System.out.println(element_in_line[i] + " is going to start elect in round " + element_in_line[1]);

                    temp.add(element_in_line[i]);
                }
                elect_nodes.put(Integer.parseInt(element_in_line[1]), temp);
            }

            if(element_in_line[0].equals(fail_parse))
            {

                savedNode.get(element_in_line[2]).setFail_round(Integer.parseInt(element_in_line[1]));

            }

        }

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
		At each round, the network delivers all the messages that it has collected from the nodes.
		Implement this logic here.
		The network must ensure that a node can send only to its neighbours, one message per round per neighbour.
		0 means no message to send or the sender will send the message to all his neighbours
		*/

        try {
            for (Integer key : treeBoardcastMsg.keySet()) {
                String message_to_deliver = treeBoardcastMsg.get(key);
                Node sender = savedNode.get(Integer.toString(key));
                Node parent = savedNode.get(sender.parent);
                /*
                If message equals to zero, it means no broadcast message will be sent in this round
                */
                if(message_to_deliver.equals(Character.toString('0')))
                {
                    continue;
                }
                if(message_to_deliver.startsWith("TREECONSTRUCT"))
                {
                    for (Node node:sender.getNeighbors())
                    {
                        if(node!=parent)
                            node.receiveMsg(message_to_deliver);
                    }
                }
                else if(message_to_deliver.startsWith("TREELEADER"))
                {
                    for (String children : sender.children)
                    {
                        Node child = savedNode.get(children);
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
		At each round, the network delivers all the messages that it has collected from the nodes.
		Implement this logic here.
		The network must ensure that a node can send only to its neighbours, one message per round per neighbour.
		0 means no message to send or the sender will send the message to all his neighbours
		*/


            try {
                for (Integer key : msgToDeliver.keySet()) {
                    String message_to_deliver = msgToDeliver.get(key);
                    String[] whole_message = message_to_deliver.split("\\s+");
                    Node sender = savedNode.get(Integer.toString(key));
                    Node parent = savedNode.get(sender.parent);
                    if(message_to_deliver.equals(Character.toString('0')))
                    {
                        continue;
                    }
                    else if(message_to_deliver.startsWith("TREEREPLAY"))
                    {
                        System.out.println("wants to send "+message_to_deliver);
                        String node_to_send = whole_message[1];
                        savedNode.get(node_to_send).receiveMsg(message_to_deliver);
                    }
                    else if(message_to_deliver.startsWith("TREEELECT"))
                    {
                        if(parent!=null)
                            parent.receiveMsg(message_to_deliver);
                    }
                    else
                        sender.getNextNode().receiveMsg(message_to_deliver);
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
        failed_node.next_neighbour.receiveMsg("FAIL " + id);
        for (Node node : failed_node.getNeighbors())
        {
            node.myNeighbours.remove(failed_node);
            if(node.myNeighbours.isEmpty())
            {
                System.out.println("network error");
            }
        }
        nodes.remove(failed_node);
        savedNode.remove(failed_node);
    }


    public static void main(String[] args) throws IOException, InterruptedException {
		/*
		Your main must get the input file as input.
		*/
        Network netWork = new Network();
        netWork.NetSimulator();
    }

}

