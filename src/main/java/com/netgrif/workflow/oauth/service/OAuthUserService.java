package com.netgrif.workflow.oauth.service;

import com.netgrif.workflow.auth.domain.*;
import com.netgrif.workflow.auth.domain.repositories.UserRepository;
import com.netgrif.workflow.auth.service.AbstractUserService;
import com.netgrif.workflow.auth.web.requestbodies.UpdateUserRequest;
import com.netgrif.workflow.configuration.properties.NaeOAuthProperties;
import com.netgrif.workflow.oauth.domain.OAuthUser;
import com.netgrif.workflow.oauth.domain.QOAuthUser;
import com.netgrif.workflow.oauth.domain.RemoteGroupResource;
import com.netgrif.workflow.oauth.domain.RemoteUserResource;
import com.netgrif.workflow.oauth.domain.repositories.OAuthUserRepository;
import com.netgrif.workflow.oauth.service.interfaces.IOAuthUserService;
import com.netgrif.workflow.oauth.service.interfaces.IRemoteGroupResourceService;
import com.netgrif.workflow.oauth.service.interfaces.IRemoteUserResourceService;
import com.netgrif.workflow.startup.SystemUserRunner;
import com.querydsl.core.types.dsl.BooleanExpression;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class OAuthUserService extends AbstractUserService implements IOAuthUserService {

    private IUser cachedSystemUser;

    @Autowired
    protected NaeOAuthProperties oAuthProperties;

    @Autowired
    protected OAuthUserRepository repository;

    @Autowired
    protected UserRepository userRepository;

    protected IRemoteUserResourceService<RemoteUserResource> remoteUserResourceService;
    protected IRemoteGroupResourceService<RemoteGroupResource, RemoteUserResource> remoteGroupResourceService;

    public OAuthUserService(IRemoteUserResourceService<RemoteUserResource> remoteUserResourceService,
                            IRemoteGroupResourceService<RemoteGroupResource, RemoteUserResource> remoteGroupResourceService) {
        this.remoteUserResourceService = remoteUserResourceService;
        this.remoteGroupResourceService = remoteGroupResourceService;
    }

    @Override
    public OAuthUser findByOAuthId(String id) {
        return repository.findByOauthId(id);
    }

    @Override
    public OAuthUser findByUsername(String username) {
        RemoteUserResource res = remoteUserResourceService.findUserByUsername(username);
        OAuthUser oAuthUser = repository.findByOauthId(res.getId());
        if (oAuthUser == null) {
            oAuthUser = fromUserRepresentation(res);
        } else {
            loadUser(oAuthUser, res);
        }
        return oAuthUser;
    }

    @Override
    public IUser findByAuth(Authentication auth) {
        return findById(((LoggedUser) auth.getPrincipal()).getId(), false);
    }

    @Override
    public IUser save(IUser user) {
        return repository.save((OAuthUser) user);
    }

    @Override
    public IUser saveNew(IUser user) {
        addDefaultRole(user);
        addDefaultAuthorities(user);
        user = save(user);
        upsertGroupMember(user);
        return user;
    }

    @Override
    public AnonymousUser saveNewAnonymous(AnonymousUser user) {
        addDefaultRole(user);
        addDefaultAuthorities(user);

        return userRepository.save(user);
    }

    @Override
    public IUser update(IUser user, UpdateUserRequest updates) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IUser findById(String id, boolean small) {
        Optional<User> dbUser = userRepository.findById(id);
        if (dbUser.isPresent()) return dbUser.get();

        OAuthUser user = findByOAuthId(id);
        loadUser(user, small);
        return user;
    }

    @Override
    public IUser resolveById(String id, boolean small) {
        Optional<User> dbUser = userRepository.findById(id);
        if (dbUser.isPresent()) return dbUser.get();

        OAuthUser user = findByOAuthId(id);
        if (user == null) {
            user = createNewUser(id);
        }
        loadUser(user, small);
        return user;
    }

    @Override
    public IUser findByEmail(String email, boolean small) {
        User dbUser = userRepository.findByEmail(email);
        if (dbUser != null) return dbUser;
        return fromUserRepresentation(remoteUserResourceService.findByEmail(email));
    }

    @Override
    public IUser findAnonymousByEmail(String email, boolean small) {
        return userRepository.findByEmail(email);
    }

    @Override
    public List<IUser> findAll(boolean small) {
        return remoteUserResourceService.listUsers(Pageable.unpaged()).stream().map(this::fromUserRepresentation).collect(Collectors.toList());
    }

    @Override
    public Page<IUser> findAllCoMembers(LoggedUser loggedUser, boolean small, Pageable pageable) {
        Page<RemoteUserResource> page = remoteUserResourceService.listUsers(pageable);
        return new PageImpl<>(
                page.getContent().stream().map(this::fromUserRepresentation).collect(Collectors.toList()),
                pageable, page.getTotalElements());
    }

    @Override
    public Page<IUser> findAllActiveByProcessRoles(Set<String> roleIds, boolean small, Pageable pageable) {
        Page<OAuthUser> users = repository.findDistinctByStateAndProcessRoles__idIn(UserState.ACTIVE, new ArrayList<>(roleIds), pageable);
        return changeType(users, pageable);
    }

    @Override
    public List<IUser> findAllByProcessRoles(Set<String> roleIds, boolean small) {
        List<OAuthUser> users = repository.findAllByProcessRoles__idIn(new ArrayList<>(roleIds));
        return changeType(users);
    }

    @Override
    public IUser getLoggedOrSystem() {
        try {
            return getLoggedUser();
        } catch (NullPointerException e) {
            return getSystem();
        }
    }

    @Override
    public IUser getSystem() {
        if (cachedSystemUser != null) {
            return cachedSystemUser;
        } else {
            User system = userRepository.findByEmail(SystemUserRunner.SYSTEM_USER_EMAIL);
            cachedSystemUser = system;
            return system;
        }
    }

    @Override
    public IUser getLoggedUser() {
        LoggedUser loggedUser = (LoggedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!loggedUser.isAnonymous()) {
            return findById(loggedUser.getId(), false);
        }
        return loggedUser.transformToAnonymousUser();
    }

    @Override
    public void deleteUser(IUser user) {
        repository.delete((OAuthUser) user);
    }

    @Override
    public Page<IUser> searchAllCoMembers(String query, LoggedUser principal, Boolean small, Pageable pageable) {
        Page<RemoteUserResource> page = remoteUserResourceService.searchUsers(query, pageable, small);
        return new PageImpl<>(
                page.getContent().stream().map(this::fromUserRepresentation).collect(Collectors.toList()),
                pageable, page.getTotalElements());
    }

    @Override
    public Page<IUser> searchAllCoMembers(String query, List<ObjectId> roleIds, List<ObjectId> negateRoleIds, LoggedUser loggedUser, Boolean small, Pageable pageable) {
        if ((roleIds == null || roleIds.isEmpty()) && (negateRoleIds == null || negateRoleIds.isEmpty())) {
            return searchAllCoMembers(query, loggedUser, small, pageable);
        }
        BooleanExpression predicate = QOAuthUser.oAuthUser.processRoles.any()._id.in(negateRoleIds != null ? negateRoleIds : new ArrayList<>()).not();
        if (roleIds != null && !roleIds.isEmpty()) {
            predicate = predicate.and(QUser.user.processRoles.any()._id.in(roleIds));
        }
        Page<OAuthUser> users = repository.findAll(predicate, pageable);
        return new PageImpl<>(users.getContent().stream()
                .peek(it -> loadUser(it, small))
                .collect(Collectors.toList()), pageable, users.getTotalElements());
    }

    protected OAuthUser fromUserRepresentation(RemoteUserResource representation) {
        if (representation == null)
            return null;

        OAuthUser oAuthUser = new OAuthUser();
        oAuthUser.setOauthId(representation.getId());
        loadUser(oAuthUser, representation);
        return oAuthUser;
    }

    protected void loadUser(OAuthUser user, boolean small) {
        RemoteUserResource userRepresentation = remoteUserResourceService.findUser(user.getOauthId());
        loadUser(user, userRepresentation);
        if (!small) {
            user.setRemoteGroups(remoteGroupResourceService.groupsOfUser(user.getOauthId()));
        }
    }

    protected void loadUser(OAuthUser user, RemoteUserResource resource) {
        user.setName(resource.getFirstName());
        user.setSurname(resource.getLastName());
        user.setEmail(resource.getEmail());
    }

    public void clearCache() {
        cachedSystemUser = null;
    }

    protected OAuthUser createNewUser(String id) {
        OAuthUser user = new OAuthUser();
        user.setOauthId(id);
        user.setState(UserState.ACTIVE);
        user = (OAuthUser) saveNew(user);
        return user;
    }

}
