package coordinatedCheckpoint.server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;

import coordinatedCheckpoint.client.NodeConnection;
import coordinatedCheckpoint.shared.Message;
import coordinatedCheckpoint.shared.RemoteFunctions;

public class Node implements RemoteFunctions, Runnable {
	private static boolean DEBUG = true;

	// private static String[][] whereToDirectTypes = {{"1", "2", "3", "4", "5"},  // type = 1
	// 												{"1", "2", "3", "4", "5"},  // type = 2
	// 												{"1", "2", "3", "4", "5"}}; // type = 3

	// This node
	private String nodeName;
	private int nodeId;
	
	// The nodes this node can send messages to (or tell to checkpoint)
	String[] targetnodeNames;
	NodeConnection[] targetNodes;

	// True if the node has stopped processing in order to checkpoint
	boolean CurrentlyCheckpointing = false;
	
	// Tracks the number of messages currently in the system
	public static int currentMessageCount = 0;

	// The node's internal queues
	private Queue<Message> toProcessQueue = new LinkedList<Message>();
	private Queue<Message> toSendQueue = new LinkedList<Message>();
	
	// Used to simulate sending latency
	static Random randomDelayGenerator = null;

	// For the dedicated checkpointing node
	private boolean[] hasRespondedToCheckpoint;
	private boolean checkpointingNode = false;

	public String lastCheckpoint = "";

	// Constructor for a normal node in the system
	public Node(int nodeID, int[] targetNodes) throws RemoteException {
		this.nodeName = "Node" + nodeID;
		this.nodeId = nodeID;
		UnicastRemoteObject.exportObject(this, 0);
		targetnodeNames = new String[targetNodes.length];
		for (int i = 0; i < targetNodes.length; i++) {
			targetnodeNames[i] = "Node" + Integer.toString(targetNodes[i]);
		}
		if (randomDelayGenerator == null) {
			randomDelayGenerator = new Random();
		}
	}
	
	// Constructor for the dedicated checkpointing node
	public Node(int numberOfNodes) throws RemoteException {
		this.nodeName = "Node" + -5;
		checkpointingNode = true;
		UnicastRemoteObject.exportObject(this, 0);
		targetnodeNames = new String[numberOfNodes];
		for (int i = 0; i < numberOfNodes; i++) {
			targetnodeNames[i] = "Node" + Integer.toString(i);
		}
		hasRespondedToCheckpoint = new boolean[numberOfNodes];
		ResetCheckpointRespondedList();
	}

	// Creates all the connections to the target nodes
	public void CreateNodeConnections() {
		targetNodes = new NodeConnection[targetnodeNames.length];
		for (int i = 0; i < targetnodeNames.length; i++) {
				targetNodes[i] = new NodeConnection(targetnodeNames[i]);
		}
	}

	// Accepts a message and puts it in the to process queue
	@Override
	public void SendToNode(Message message) throws RemoteException {
		message.contents += nodeName.replace("Node", "");
		
		//math operations
		if (this.nodeId == 1) {
			message.value += message.value;
		}
		else if (this.nodeId == 2) {
			message.value = (int) Math.pow(message.value,2);
		}
		else if (this.nodeId == 3) {
			message.value *= message.value;
		}
		else if (this.nodeId == 4) {
			message.value -= 1;
		}
		
		message.visited(nodeName);
		toProcessQueue.add(message);
		try {
			TimeUnit.SECONDS.sleep(randomDelayGenerator.nextInt(5));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
		if (DEBUG) {
			System.out.println(nodeName + " just received " + message.getName() + " has "+ message.contents + " value= " + message.value);
		}
		//toProcessQueue.add(message);
	}
	
	// Accepts a message and puts it in the to process queue for the start of message process
		public void StartSendToNode(Message message) throws RemoteException {
			currentMessageCount++;
			message.contents += nodeName.replace("Node", "");
			message.visited(nodeName);
			toProcessQueue.add(message);
			try {
				TimeUnit.SECONDS.sleep(randomDelayGenerator.nextInt(5));
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }
			if (DEBUG) {
				System.out.println(nodeName + " just received " + message.getName() + " has "+ message.contents);
			}
			//toProcessQueue.add(message);
		}

	// Constantly run by the node (what the node is constantly working on)
	@Override
	public void run() {
		while (true) {
			sleep(1);
			if (checkpointingNode) {
				processCheckpoint();
			} else if (!CurrentlyCheckpointing){
				processContents();
			}
		}
	}

	// Checks if every node has checkpointed and if so handles it
	private void processCheckpoint() {
		if (CurrentlyCheckpointing) {
			if (everyNodeResponded()) {
				CurrentlyCheckpointing = false;
				broadcastCheckpointingDone();
				if (DEBUG) {
					System.out.println("Checkpointing completed.");
				}
			}
		}
	}

	// Checks if every node has responded to the checkpointing request
	private boolean everyNodeResponded() {
		for (boolean b : hasRespondedToCheckpoint) {
			if (b == false) {
				return false;
			}
		}
		return true;
	}

	// Sends a message to each node telling it the checkpointing process is over
	private void broadcastCheckpointingDone() {
		for (NodeConnection tellNode : targetNodes) {
			tellNode.CheckpointingIsDone();
		}
	}

	// Processes the contents of the node
	private void processContents() {
		if (!CurrentlyCheckpointing && toProcessQueue.size() > 0) {
			if (DEBUG) {
				String temp = nodeName + " = ";
				int size = toProcessQueue.size();
				for (int i = 0; i < size; i++) {
					Message tempMessage = toProcessQueue.poll();
					temp += tempMessage.getName() + " ";
					toProcessQueue.add(tempMessage);
				}
				System.out.println(temp);
			}
			processMessage(toProcessQueue.poll());
		}
	}

	// A way to call sleep without having to see the try catch
	private void sleep(int seconds) {
		try {
			TimeUnit.SECONDS.sleep(seconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// Processes the given message
	private void processMessage(Message message) {
		String nextNode = message.next();
		if (nextNode == null) {
			System.out.println(message.getName() + " has reached end of path ("+ nodeName + ") " + visitedNodes(message.getVistedNodes()));
			currentMessageCount--;
			return;
		}
		for (int i = 0; i < targetnodeNames.length; i++) {
			if (nextNode.equals(targetnodeNames[i])) {
				targetNodes[i].SendToNode(message);
				return;
			}
		}
		System.out.println("ERROR: " + message.getName() + " (no connection from current node (" + nodeName + ") to the next node (" + nextNode + "))");
	}

	// Returns a human readable list of visited nodes
	private String visitedNodes(String[] vistedNodes) {
		String  output = "and visited: ";
		String[] temp = vistedNodes;
		for (int i = 0; i < temp.length; i++) {
			output += temp[i] + " ";
		}
		return output;
	}

	// Receives the notification to pause for a checkpoint
	@Override
	public void getPauseMessage() throws RemoteException {
		NodeConnection starterNode = new NodeConnection("Node-5");
		CurrentlyCheckpointing = true;
		lastCheckpoint = this.toString();
		boolean checkpointFin = storeCheckpoint(lastCheckpoint);
		System.out.println(lastCheckpoint); // prints what is written to file
		if (DEBUG && checkpointFin) {
			System.out.println(nodeName + " just checkpointed.");
		}
		starterNode.IFinnishedCheckpointing(nodeName);
	}

	// Marks the given node as checkpointed
	@Override
	public void NodeFinnishedCheckpointing(String nodeName) throws RemoteException {
		nodeName = nodeName.replace("Node", "");
		int nodeID;
		try {
			nodeID = Integer.parseInt(nodeName);
		} catch (Exception e) {
			return;
		}
		hasRespondedToCheckpoint[nodeID] = true;
	}

	// Sets every boolean in responded to checkpointed array to false
	private void ResetCheckpointRespondedList() {
		for (int i = 0; i < hasRespondedToCheckpoint.length; i++) {
			hasRespondedToCheckpoint[i] = false;
		}
	}

	// Restarts the processing of the array
	@Override
	public void CheckpointingIsDone() throws RemoteException {
		if (DEBUG) {
			System.out.println(nodeName + " is resuming normal operation");
		}
		CurrentlyCheckpointing = false;
	}


	// Initiates the checkpointing process
	public void checkpoint() {
		System.out.println("Initiating checkpoint.");
        ResetCheckpointRespondedList();
		CurrentlyCheckpointing = true;
		for (NodeConnection connection : targetNodes) {
			connection.sendPauseMessage();
		}
	}
	
	// Writes the toString checkpoint storage to a file in format: nodeName + "Checkpoint.txt"
	public boolean storeCheckpoint(String input) {
		try {
			File nodeCheckpointFile = new File("Checkpoint.txt");
			if (!nodeCheckpointFile.exists()) {
				nodeCheckpointFile.createNewFile();
				System.out.println("Created new checkpoint file: " + nodeCheckpointFile.getName());
			}
			FileWriter nodeCheckpoint = new FileWriter("Checkpoint.txt",true);
			nodeCheckpoint.write(input);
			nodeCheckpoint.close();
			System.out.println("Wrote checkpoint to " + nodeCheckpointFile.getName());
			return true;
		} catch (IOException error) {
			System.out.println("Error in file checkpoint process");
			error.printStackTrace();
			return true;
		}
	}

	// Used as the checkpoint storage for now
	@Override
	public String toString() {
		String output = "";//nodeName + "\n";
		//output += "toProcessQueue\n";
		for (Message message : toProcessQueue) {
			output += message.toString() + "\n";
			output += message.getRemainingNodes() + "\n";
		}
		return output;
	}
}
