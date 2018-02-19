package com.netgrif.workflow.auth.service.interfaces;

import com.netgrif.workflow.auth.domain.UserProcessRole;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;

import java.util.Collection;
import java.util.List;

public interface IUserProcessRoleService {

    List<UserProcessRole> findAllMinusDefault();

    UserProcessRole findDefault();

    List<UserProcessRole> saveRoles(Collection<ProcessRole> values, String netId);
}