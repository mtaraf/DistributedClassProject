package coordinatedCheckpoint.shared;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Queue;

public class Message implements Serializable {
    private String name = null;
    private String type = null;
    private Queue<String> toVisit = null;
    public String contents = "";
    public int value = 2;
    private Queue<String> visitedNodes = null;
    
    public Message(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Message(String name, String[] toVisit) {
        this.name = name;
        this.toVisit = new LinkedList<String>();
        visitedNodes = new LinkedList<String>();
        for (String next : toVisit) {
            this.toVisit.add(next);
        }
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String next() {
        return toVisit.poll();
    }

    public void visited(String nodeName) {
        visitedNodes.add(nodeName);
    }

    public void setType(String type) {
        this.type = type;
    }

    public String[] getVistedNodes() {
        String[] output = new String[visitedNodes.size()];
        for (int i = 0; i < output.length; i++) {
            output[i] = visitedNodes.poll();
        }
        return output;
    }
    
    public String getRemainingNodes() {
    	String output = "";
    	for (String item : toVisit) {
    		output += item + ",";
    	}
    	return output;
    }

    @Override
    public String toString() {
        if (name == null) {
            return "null: " + contents;
        } else {
            return name + ": " + contents;
        }
    }
}
