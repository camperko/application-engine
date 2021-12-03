package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRolePermission;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowAuthorizationService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WorkflowAuthorizationService extends AbstractAuthorizationService implements IWorkflowAuthorizationService {

    @Autowired
    private IWorkflowService workflowService;

    @Autowired
    private IPetriNetService petriNetService;

    @Autowired
    private IUserService userService;

    @Override
    public boolean canCallDelete(LoggedUser user, String caseId) {
        Case requestedCase = workflowService.findOne(caseId);
        return user.isAdmin() || userHasAtLeastOneRolePermission(user.transformToUser(), requestedCase.getPetriNet(), ProcessRolePermission.DELETE)
                || userHasUserListPermission(user.transformToAnonymousUser(), requestedCase, ProcessRolePermission.DELETE);
    }

    @Override
    public boolean canCallCreate(LoggedUser user, String netId) {
        PetriNet net = petriNetService.getPetriNet(netId);
        return user.isAdmin() || userHasAtLeastOneRolePermission(user.transformToUser(), net, ProcessRolePermission.CREATE);
    }

    @Override
    public boolean userHasAtLeastOneRolePermission(IUser user, PetriNet net, ProcessRolePermission... permissions) {
        Map<String, Boolean> aggregatePermissions = getAggregatePermissions(user, net.getPermissions());

        for (ProcessRolePermission permission : permissions) {
            if (hasRestrictedPermission(aggregatePermissions.get(permission.toString()))) {
                return false;
            }
        }

        return Arrays.stream(permissions).anyMatch(permission -> hasPermission(aggregatePermissions.get(permission.toString())));
    }

    @Override
    public boolean userHasUserListPermission(IUser user, Case useCase, ProcessRolePermission... permissions) {
        Map<String, Map<String, Boolean>> users = useCase.getUsers();

        if (!users.containsKey(user.getStringId()))
            return false;

        Map<String, Boolean> userPermissions = users.get(user.getStringId());

        for (ProcessRolePermission permission : permissions) {
            Boolean hasPermission = userPermissions.get(permission.toString());
            if (hasPermission != null && hasPermission) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Boolean> getAggregatePermissions(IUser user, PetriNet net) {
        Map<String, Boolean> aggregatePermissions = new HashMap<>();

        Set<String> userProcessRoleIDs = user.getProcessRoles().stream().map(role -> role.get_id().toString()).collect(Collectors.toSet());

        for (Map.Entry<String, Map<String, Boolean>> role : net.getPermissions().entrySet()) {
            if (userProcessRoleIDs.contains(role.getKey())) {
                for (Map.Entry<String, Boolean> permission : role.getValue().entrySet()) {
                    if (aggregatePermissions.containsKey(permission.getKey())) {
                        aggregatePermissions.put(permission.getKey(), aggregatePermissions.get(permission.getKey()) || permission.getValue());
                    } else {
                        aggregatePermissions.put(permission.getKey(), permission.getValue());
                    }
                }
            }
        }

        return aggregatePermissions;
    }
}
