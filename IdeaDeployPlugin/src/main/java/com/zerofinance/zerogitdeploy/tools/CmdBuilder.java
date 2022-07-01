package com.zerofinance.zerogitdeploy.tools;

import java.util.List;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * CmdBuilder
 * 
 * <p>
 * <a href="CmdParam.java"><i>View Source</i></a>
 * </p>
 * 
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0
 */
public class CmdBuilder {
    private String workHome;
    private String command;
    private List<String> params;
    private boolean isBatchScript;
    
    public CmdBuilder(String workHome, String command, boolean isBatchScript, List<String> params) {
        this.workHome = workHome;
        this.command = command;
        this.isBatchScript = isBatchScript;
        this.params = params;
    }

    public String getWorkHome() {
        return workHome;
    }

    public void setWorkHome(String workHome) {
        this.workHome = workHome;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
    
    public List<String> getParams() {
        return params;
    }

    public void setParams(List<String> params) {
        this.params = params;
    }
    
    public boolean isBatchScript() {
		return isBatchScript;
	}

	public void setBatchScript(boolean isBatchScript) {
		this.isBatchScript = isBatchScript;
	}

	public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
