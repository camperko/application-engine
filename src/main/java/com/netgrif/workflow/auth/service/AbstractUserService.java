package com.netgrif.workflow.auth.service;

import com.netgrif.workflow.auth.domain.*;
import com.netgrif.workflow.auth.domain.repositories.UserRepository;
import com.netgrif.workflow.auth.service.interfaces.IAuthorityService;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.orgstructure.domain.Member;
import com.netgrif.workflow.orgstructure.groups.interfaces.INextGroupService;
import com.netgrif.workflow.orgstructure.service.IMemberService;
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole;
import com.netgrif.workflow.petrinet.service.interfaces.IProcessRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.netgrif.workflow.startup.SystemUserRunner.*;

public abstract class AbstractUserService implements IUserService {

    @Autowired
    protected IAuthorityService authorityService;

    @Autowired
    protected IProcessRoleService processRoleService;

    @Autowired
    protected IMemberService memberService;

    @Autowired
    protected INextGroupService groupService;

    @Autowired
    protected UserRepository repository;

    @Override
    public Member upsertGroupMember(IUser user) {
        Member member = memberService.findByEmail(user.getEmail());
        if (member == null)
            member = new Member(user.getStringId(), user.getName(), user.getSurname(), user.getEmail());
        member.setGroups(user.getGroups());
        return memberService.save(member);
    }

    @Override
    public void addDefaultRole(IUser user) {
        user.addProcessRole(processRoleService.defaultRole());
    }

    @Override
    public void addDefaultAuthorities(IUser user) {
        if (user.getAuthorities().isEmpty()) {
            HashSet<Authority> authorities = new HashSet<>();
            authorities.add(authorityService.getOrCreate(Authority.user));
            user.setAuthorities(authorities);
        }
    }

    @Override
    public void assignAuthority(String userId, String authorityId) {
        IUser user = resolveById(userId, true);
        Authority authority = authorityService.getOne(authorityId);
        user.addAuthority(authority);
        authority.addUser(user);

        save(user);
    }

    @Override
    public LoggedUser getAnonymousLogged() {
        if (SecurityContextHolder.getContext().getAuthentication().getPrincipal().equals(UserProperties.ANONYMOUS_AUTH_KEY)) {
            getLoggedUser().transformToLoggedUser();
        }
        return (LoggedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Override
    public IUser addRole(IUser user, String roleStringId) {
        ProcessRole role = processRoleService.findById(roleStringId);
        user.addProcessRole(role);
        return save(user);
    }

    @Override
    public IUser removeRole(IUser user, String roleStringId) {
        ProcessRole role = processRoleService.findByImportId(roleStringId);
        user.removeProcessRole(role);
        return save(user);
    }

    @Override
    public IUser createSystemUser() {
        User system = repository.findByEmail(SYSTEM_USER_EMAIL);
        if (system == null) {
            system = new User(SYSTEM_USER_EMAIL, "n/a", SYSTEM_USER_NAME, SYSTEM_USER_SURNAME);
            system.setState(UserState.ACTIVE);
            repository.save(system);
        }
        return system;
    }

    public <T> Page<IUser> changeType(Page<T> users, Pageable pageable) {
        return new PageImpl<>(changeType(users.getContent()), pageable, users.getTotalElements());
    }

    public <T> List<IUser> changeType(List<T> users) {
        return users.stream().map(IUser.class::cast).collect(Collectors.toList());
    }

}