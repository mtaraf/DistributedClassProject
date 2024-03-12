package coordinatedCheckpoint;

import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Arrays;

import coordinatedCheckpoint.server.Node;
import coordinatedCheckpoint.shared.Message;
import coordinatedCheckpoint.shared.RemoteFunctions;

public class RunSystem {
    private static boolean DEBUG = true;
    
    static Registry registry = null;

    static int[][] things = {{1, 3}, {2, 3}, {3, 1}, {4, 2}, {0, 2}};

    static final int NUMBER_OF_NODES = 5;
    
    public static void main(String[] args) throws RemoteException, AlreadyBoundException {
        System.out.println("Type exit below to exit");
        
        // SETUP
        //Create Registry
        try {
            registry = LocateRegistry.createRegistry(1099);
        } catch (Exception e) {
            System.out.println("Registry was unable to be created because:");
            System.out.println(e.getMessage());
            System.exit(0);
        }

        // Create nodes
        Queue<Node> nodeList = new LinkedList<Node>();
        for (int i = 0; i < NUMBER_OF_NODES; i++) {
            try {
                nodeList.add(CreateNode(i));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        // Acquire a node array for sending to specific nodes when rebooting system
        Object[] nodeObjectArray = nodeList.toArray();
        Node[] nodeArray = Arrays.copyOf(nodeObjectArray, nodeObjectArray.length, Node[].class);

        // Connect all the nodes to each other
        for (int i = 0; i < NUMBER_OF_NODES; i++) {
            try {
                Node temp = (Node) registry.lookup("Node" + i);
                temp.CreateNodeConnections();
            } catch (NotBoundException e) {
                printe("Node name does not exist.", e);
            }
        }

        // Creates the node that supplies the messages
        Node sensor = CreateNode(-1, new int[] {1, 4});
        sensor.CreateNodeConnections();
        
        // Creates the coordinator
        Node coordinator = createCoordinator();
        

        String earlyTerm = "false";
        try {
            File terminateCheck = new File("EarlyTermination.txt");
            if (!terminateCheck.exists()) {
				terminateCheck.createNewFile();
				System.out.println("Created early teminate file");
			}
            Scanner termReader;
            termReader = new Scanner(terminateCheck);
            while (termReader.hasNextLine()) {
              earlyTerm = termReader.nextLine();
            }
            termReader.close();
        } catch (FileNotFoundException e) {
        	printe("Early Termination file does not exist.", e);
		} catch (IOException error) {
			printe("Error creating Early Termination file.", error);
		}
        
        Scanner in = new Scanner(System.in);
        boolean normalRun = true;
        if (earlyTerm.equals("true")) {
        	System.out.println("System terminated before message sending was complete. Complete prior message send? (Y/N)");
        	while (true) {
        		String input = in.nextLine();
        		if (input.toLowerCase().equals("y")) {
        			normalRun = false;
        			break;
        		} else if (input.toLowerCase().equals("n")) {
        			System.out.println("Continuing normal runtime.");
        			try {
        			FileWriter checkpointClear = new FileWriter("Checkpoint.txt",false);
	    			checkpointClear.write("");
	    			checkpointClear.close();
        			} catch (IOException error) {
        				printe("Error clearing checkpoint file.", error);
        			}
        			break;
        		} else {
        			System.out.println("Invalid input. Please enter (y/n).");
        		}
        	}
        }
        
        if (normalRun) {
	        // TESTING
	        // Creating messages to bounce around (for testing)
	        sensor.StartSendToNode(new Message("Message1", new String[] {"Node1", "Node2", "Node3", "Node4"}));
	        sensor.StartSendToNode(new Message("Message2", new String[] {"Node4", "Node2", "Node1", "Node3"}));
	        sensor.StartSendToNode(new Message("Message3", new String[] {"Node4", "Node2", "Node1", "Node3"}));
	        
	        // Wating for the messages to be bounce around for a bit
		    sleep(5);
	
	        // Initiates the checkpoint
	        coordinator.checkpoint();
        } else {
            // runs system using messages from checkpoint.txt
        	try {
                File checkpoint = new File("Checkpoint.txt");
                Scanner checkpointReader;
                checkpointReader = new Scanner(checkpoint);
                String message = "";
                String messageName = "";
                String remainingVisits = "";
                String visit = "";
                int index = -1;
                int length = 0;
                String nodeNumberString = ""; // gets the node that the message was in
                int nodeNumber = -1; // will be the int version of nodeNumber
                String[] allVisits;
                Queue<String> visitList = new LinkedList<String>();
                while (checkpointReader.hasNextLine()) {
                  message = checkpointReader.nextLine();
                  if (checkpointReader.hasNextLine()) {
                  	remainingVisits = checkpointReader.nextLine();
                  } else {
                	  remainingVisits = "";
                  }
                  index = message.indexOf(":");
                  length = message.length();
                  messageName = message.substring(0,index);
                  nodeNumberString = message.substring(length - 1);
                  nodeNumber = Integer.parseInt(nodeNumberString);
                  message = message.substring(index + 2, length - 1);
                  index = remainingVisits.indexOf(",");
                  //System.out.println(remainingVisits + " this is remaining visits");
                  //System.out.println(index + " this is index");
                  while (index > 0) {
	                  visit = remainingVisits.substring(0,index);
	                  //System.out.println(visit + " this is visit");
	                  visitList.add(visit);
	                  remainingVisits = remainingVisits.substring(index + 1);
	                  index = remainingVisits.indexOf(",");
                  }
                  Object[] tempArray = visitList.toArray();
                  allVisits = Arrays.copyOf(tempArray, tempArray.length, String[].class); // converts object array to string array
                  visitList.clear();
                  /*for (String name : allVisits) {
                	  System.out.println(name + " to Visit");
                  }*/
                  Message send = new Message(messageName, allVisits);
                  send.contents = message; // includes the visited nodes except for the one that it was in during checkpoint
                  //System.out.println(nodeNumber);
                  if (nodeNumber == -1) {
                	  nodeNumber = 0;
                  }
                  nodeArray[nodeNumber].StartSendToNode(send);
                }
                checkpointReader.close();
            } catch (FileNotFoundException e) {
            	printe("Early Termination file does not exist.", e);
    		}
            // Initiates the checkpoint
            coordinator.checkpoint();
        }
        
        // Creates shutdown hook
        ShutDown shutdown = new ShutDown();
        Runtime.getRuntime().addShutdownHook(shutdown);

        // EXITING
        // Lets the user kill the nodes by typing "exit"
        while (in.hasNext()) {
            if (in.nextLine().toLowerCase().equals("exit")) {
                in.close();
                System.exit(0);
            }
        }

        // Printing out what was saved during the latest checkpoint
        for (int i = 0; i < NUMBER_OF_NODES; i++) {
            try {
                Node temp = (Node) registry.lookup("Node" + i);
                // System.out.println(temp.toString());
                System.out.println(temp.lastCheckpoint);
            } catch (NotBoundException e) {
                printe("Node name does not exist.", e);
            }
        }
        
	}

    // A way to call sleep without having to see the try catch
	private static void sleep(int seconds) {
		try {
			TimeUnit.SECONDS.sleep(seconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


    // Creates a generic node
    public static Node CreateNode(int i) throws RemoteException {
        return CreateNode(i, things[i]);
    }
    public static Node CreateNode(int i, int[] connectionIDs) throws RemoteException {
        Node temp = new Node(i, connectionIDs);
        RemoteFunctions stub = temp; //new int[] {1, 2}
        Thread tempThread = new Thread(temp);
        try {
            registry.bind("Node" + i, stub);
        } catch (AlreadyBoundException e) {
            printe("Failed to bind new node to given name", e);
            return null;
        }
        tempThread.start();
        if (DEBUG) {
            System.out.println("Node"+ i + " start successful!");
        }
        return temp;
    }

    // Creates a coordinator (comes preloaded with connections to all n nodes)
    public static Node createCoordinator() throws RemoteException {
        Node output = new Node(NUMBER_OF_NODES);
        output.CreateNodeConnections();
        RemoteFunctions stub = output;
        Thread tempThread = new Thread(output);
        try {
            registry.bind("Node" + -5, stub);
        } catch (AlreadyBoundException e) {
            printe("Failed to bind new node to given name", e);
        }
        tempThread.start();
        return output;
    }

    // Prints the given error e with the formating Jarod likes
    public static void printe(String content, Exception e) {
        System.out.println("ERROR: " + content);
        System.out.println("ERROR: " + e + "\n");
    }
    
    // Shutdown hook that runs when exit command is run
    private static class ShutDown extends Thread {
    	public void run() {
    		try {
    			System.out.println(Node.currentMessageCount);
    			File terminateFile = new File("EarlyTermination.txt");
    			if (!terminateFile.exists()) {
    				terminateFile.createNewFile();
    				System.out.println("Created early teminate file");
    			}
    			FileWriter terminateCheck = new FileWriter("EarlyTermination.txt", false);
    			if (Node.currentMessageCount > 0) {
	    			terminateCheck.write("true");
    			} else {
    				terminateCheck.write("false");
    				File nodeCheckpointFile = new File("Checkpoint.txt");
	    			if (!nodeCheckpointFile.exists()) {
	    				nodeCheckpointFile.createNewFile();
	    				System.out.println("Created new checkpoint file: " + nodeCheckpointFile.getName());
	    			}
	    			// clears checkpoint file if system ran normally
	    			FileWriter checkpointClear = new FileWriter("Checkpoint.txt",false);
	    			checkpointClear.write(""); // comment out if you wish to see checkpoint.txt with checkpoint values after normal runtime
	    			checkpointClear.close();
    			}
    			terminateCheck.close();
    		} catch (IOException e) {
    			
    		}
    	}
    }

}
