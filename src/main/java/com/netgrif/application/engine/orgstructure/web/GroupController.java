package com.netgrif.application.engine.orgstructure.web;

import com.netgrif.application.engine.auth.domain.*;
import com.netgrif.application.engine.orgstructure.groups.interfaces.INextGroupService;
import com.netgrif.application.engine.orgstructure.web.responsebodies.Group;
import com.netgrif.application.engine.orgstructure.web.responsebodies.GroupsResource;
import com.netgrif.application.engine.workflow.domain.Case;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.hateoas.MediaTypes;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/group")
@ConditionalOnProperty(
        value = "nae.group.web.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Api(tags = {"Group"}, authorizations = @Authorization("BasicAuth"))
public class GroupController {

    @Autowired
    private INextGroupService service;

    @Authorizations(value = {
            @Authorize(authority = "GROUP_VIEW_ALL")
    })
    @ApiOperation(value = "Get all groups in the system",
            notes = "Caller must have the GROUP_VIEW_ALL authority",
            authorizations = @Authorization("BasicAuth"))
    @RequestMapping(value = "/all", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = GroupsResource.class),
            @ApiResponse(code = 403, message = "Caller doesn't fulfill the authorisation requirements"),
    })
    public GroupsResource getAllGroups() {
        List<Case> groups = service.findAllGroups();
        Set<Group> groupResponse = groups.stream()
                .map(aCase -> new Group(aCase.getStringId(), aCase.getTitle()))
                .collect(Collectors.toCollection(HashSet::new));
        return new GroupsResource(groupResponse);
    }

    @Authorizations(value = {
            @Authorize(authority = "GROUP_VIEW_MY")
    })
    @ApiOperation(value = "Get logged user's groups in the system",
            notes = "Caller must have the GROUP_VIEW_MY authority",
            authorizations = @Authorization("BasicAuth"))
    @RequestMapping(value = "/my", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = GroupsResource.class),
            @ApiResponse(code = 403, message = "Caller doesn't fulfill the authorisation requirements"),
    })
    public GroupsResource getMyGroups(Authentication auth) {
        String loggedUserId = ((LoggedUser)auth.getPrincipal()).getId();
        List<Case> groups = service.findAllGroups();
        Set<Group> groupResponse = groups.stream()
                .filter(aCase -> aCase.getAuthor().getId().equals(loggedUserId))
                .map(aCase -> new Group(aCase.getStringId(), aCase.getTitle()))
                .collect(Collectors.toCollection(HashSet::new));
        return new GroupsResource(groupResponse);
    }

//    @ApiOperation(value = "Get all the user's groups", authorizations = @Authorization("BasicAuth"))
//    @GetMapping(value = "/my", produces = MediaTypes.HAL_JSON_VALUE)
//    public GroupsResource getGroupsOfUser(Authentication auth) {
//        IUser loggedUser = ((LoggedUser) auth.getPrincipal()).transformToUser();
//        List<Long> groupIds = loggedUser.getGroups().stream()
//                .map(Group::getId)
//                .collect(Collectors.toList());
//        Set<Group> groups = service.findAllById(groupIds);
//        return new GroupsMinimalResource(groups);
//    }
}