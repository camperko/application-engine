package com.netgrif.workflow.workflow.service.interfaces;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRolePermission;
import com.netgrif.workflow.petrinet.domain.roles.RolePermission;
import com.netgrif.workflow.workflow.domain.Case;

public interface IWorkflowAuthorizationService {

    boolean canCallDelete(LoggedUser user, String caseId);

    boolean canCallCreate(LoggedUser user, String netId);

    boolean userHasAtLeastOneRolePermission(User user, PetriNet net, ProcessRolePermission... permissions);

    boolean userHasUserListPermission(User user, Case useCase, ProcessRolePermission... permissions);
}
