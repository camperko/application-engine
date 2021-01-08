package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRolePermission;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowAuthorizationService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowAuthorizationService implements IWorkflowAuthorizationService {

    @Autowired
    private IWorkflowService workflowService;

    @Autowired
    private IPetriNetService petriNetService;

    @Override
    public boolean canCallDelete(LoggedUser user, String caseId) {
        Case requestedCase = workflowService.findOne(caseId);
        return user.isAdmin() || userHasAtLeastOneRolePermission(user.transformToUser(), requestedCase.getPetriNet(), ProcessRolePermission.DELETE);
    }

    @Override
    public boolean canCallCreate(LoggedUser user, String netId) {
        PetriNet net = petriNetService.getPetriNet(netId);
        return user.isAdmin() || userHasAtLeastOneRolePermission(user.transformToUser(), net, ProcessRolePermission.CREATE);
    }

    @Override
    public boolean userHasAtLeastOneRolePermission(User user, PetriNet net, ProcessRolePermission... permissions) {
        Map<String, Boolean> aggregatePermissions = getAggregatePermissions(user, net);

        for (ProcessRolePermission permission : permissions) {
            Boolean hasPermission = aggregatePermissions.get(permission.toString());
            if (hasPermission != null && hasPermission) {
                return true;
            }
        }

        return false;
    }

    private Map<String, Boolean> getAggregatePermissions(User user, PetriNet net) {
        Map<String, Boolean> aggregatePermissions = new HashMap<>();

        Set<String> userProcessRoleIDs = new LinkedHashSet<>();
        for (ProcessRole role : user.getProcessRoles()) {
            userProcessRoleIDs.add(role.get_id().toString());
        }

        Map<String, Map<String, Boolean>> roles = new HashMap<>();
        for (Map.Entry<String, Set<ProcessRolePermission>> entry : net.getProcessRoles().entrySet()) {
            if(roles.containsKey(entry.getKey()) && roles.get(entry.getKey()) != null)
                roles.get(entry.getKey()).putAll(parsePermissionMap(entry.getValue()));
            else
                roles.put(entry.getKey(),parsePermissionMap(entry.getValue()));
        }

        for (Map.Entry<String, Map<String, Boolean>> role : roles.entrySet()) {
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

    private Map<String, Boolean> parsePermissionMap(Set<ProcessRolePermission> permissions){
        Map<String, Boolean> map = new HashMap<>();
        permissions.forEach(perm -> map.put(perm.toString(),true));
        return map;
    }
}
