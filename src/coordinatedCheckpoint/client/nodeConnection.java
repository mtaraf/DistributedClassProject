package coordinatedCheckpoint.client;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import coordinatedCheckpoint.shared.Message;
import coordinatedCheckpoint.shared.RemoteFunctions;

public class NodeConnection {
	
	// Connection details
    RemoteFunctions node;
	String bindname = "Server";
	String hostIP = "localhost";
	int portnumber = 1099;
	
	// Creates a new connection to the target node
    public NodeConnection(String targetNodeName) {
        bindname = targetNodeName;
		try {
            Registry registry = LocateRegistry.getRegistry(hostIP, portnumber);
            node = (RemoteFunctions) registry.lookup(bindname);
        } catch (RemoteException e) {
            System.out.println("Was unable to connect to " + bindname + " on " + hostIP + " on port " + portnumber + ".");
        } catch (NotBoundException e) {
            System.out.println("Was unable to connect to " + bindname + " on " + hostIP + " on port " + portnumber + ".");
        }
	}

    // Sends the message 'message' to the node
    public void SendToNode(Message message) {
        try {
            node.SendToNode(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // Notifies the target node that a checkpoint is starting
    public void sendPauseMessage() {
        try {
            node.getPauseMessage();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    // Notifies the target node that the calling node has finnished the checkpointing process
    public void IFinnishedCheckpointing(String nodeName) {
        try {
            node.NodeFinnishedCheckpointing(nodeName);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    // Notifies the target node that the checkpointing process is complete
    public void CheckpointingIsDone() {
        try {
            node.CheckpointingIsDone();
        } catch (RemoteException e) {
        e.printStackTrace();
        }
    }
}
