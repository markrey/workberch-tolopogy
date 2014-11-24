package main.java.parser.model;

import main.java.utils.constants.WorkberchConstants;

public class WorkberchLink {

	String sourceNode;
	String sourceOutput;
	
	String destNode;
	String destOutput;
	
	
	public String getSourceNode() {
		return sourceNode;
	}
	public void setSourceNode(final String sourceNode) {
		this.sourceNode = sourceNode;
	}
	public String getSourceOutput() {
		return sourceOutput;
	}
	public void setSourceOutput(final String sourceOutput) {
		this.sourceOutput = sourceOutput;
	}
	public String getDestNode() {
		return destNode;
	}
	public void setDestNode(final String destNode) {
		this.destNode = destNode;
	}
	public String getDestOutput() {
		return destOutput;
	}
	public void setDestOutput(final String destOutput) {
		this.destOutput = destOutput;
	}
	
	
	public String getStormSourceField() {
		return getSourceNode() + WorkberchConstants.NAME_DELIMITER + getSourceOutput();
	}
	
	public String getStormDestField() {
		return getDestNode() + WorkberchConstants.NAME_DELIMITER + getDestOutput();
	}
	
	
}
