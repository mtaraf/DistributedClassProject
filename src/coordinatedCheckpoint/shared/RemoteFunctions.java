package coordinatedCheckpoint.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteFunctions extends Remote {
    public void SendToNode(Message message) throws RemoteException;
    public void getPauseMessage() throws RemoteException;
    public void NodeFinnishedCheckpointing(String nodeName) throws RemoteException;
    public void CheckpointingIsDone() throws RemoteException;
}
