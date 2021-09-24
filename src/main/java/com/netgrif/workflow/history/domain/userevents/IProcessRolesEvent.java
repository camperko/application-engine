package com.netgrif.workflow.history.domain.userevents;

import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;

import java.util.Collection;

public interface IProcessRolesEvent {

    void setProcessRoles(Collection<ProcessRole> roles);

    Collection<ProcessRole> getProcessRoles();
}