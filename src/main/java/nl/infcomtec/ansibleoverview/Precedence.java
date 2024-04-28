/*
 * Copyright (c) 2024 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.ansibleoverview;

/**
 * Ansible precedence.
 *
 * @author walter
 */
public enum Precedence {
    Defaults, Global, Group, Host, Role
}
