/*
 * Copyright (c) 2024 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.ansibleoverview;

import java.util.LinkedList;
import java.util.List;

/**
 * Ansible variable (strictly speaking a constant).
 */
public class Variable {

    /**
     * Precedence
     */
    public final Precedence level;
    /**
     * if not null, host level
     */
    public final String host;
    /**
     * if not null, group level
     */
    public final String group;
    /**
     * if not null, role level
     */
    public final String role;
    /**
     * fileId we found this
     */
    public final FileId fileId;
    /**
     * Value of the variable
     */
    public final String value;

    public Variable(String role, String host, String group, FileId fileId, String value) {
        Main.addHost(group, host);
        this.host = host;
        this.group = group;
        this.role = role;
        this.fileId = fileId;
        this.value = value;
        if (null != role) {
            level = Precedence.Role;
        } else if (null != host) {
            level = Precedence.Host;
        } else if (null != group) {
            level = Precedence.Group;
        } else if (null != fileId && fileId.path.endsWith(".cfg")) {
            level = Precedence.Global;
        } else {
            level = Precedence.Defaults;
        }
    }

    public static void putVar(String name, String role, String host, String group, FileId fileId, String value) {
        Variable var = new Variable(role, host, group, fileId, value);
        List<Variable> get = Main.ansVars.get(name);
        if (null == get) {
            get = new LinkedList<>();
            Main.ansVars.put(name, get);
        }
        get.add(var);
    }

    public static Variable getVar(String name, String host, String group, String role) {
        List<Variable> get = Main.ansVars.get(name);
        if (null == get) {
            return null;
        }
        Variable ret = null;
        for (Variable v : get) {
            if (null != role && null != v.role && role.equals(v.role)) {
                if (null == ret || ret.level.ordinal() < v.level.ordinal()) {
                    ret = v;
                }
            }
            if (null != host && null != v.host && host.equalsIgnoreCase(v.host)) {
                if (null == ret || ret.level.ordinal() < v.level.ordinal()) {
                    ret = v;
                }
            }
            if (null != group && null != v.group && group.equals(v.group)) {
                if (null == ret || ret.level.ordinal() < v.level.ordinal()) {
                    ret = v;
                }
            }
            if (null == ret || ret.level.ordinal() < v.level.ordinal()) {
                ret = v;
            }
        }
        return ret;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nVariable{");
        sb.append("level=").append(level);
        if (null != host) {
            sb.append(", host=").append(host);
        }
        if (null != group) {
            sb.append(", group=").append(group);
        }
        if (null != role) {
            sb.append(", role=").append(role);
        }
        sb.append(", fileId=").append(fileId);
        sb.append(", value=").append(value);
        sb.append("\n}\n");
        return sb.toString();
    }

}
