package com.netgrif.workflow.ldap.service;


import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.auth.domain.RegisteredUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.auth.domain.ldapUser.LdapUser;
import com.netgrif.workflow.auth.service.UserService;
import com.netgrif.workflow.event.events.user.UserRegistrationEvent;
import com.netgrif.workflow.ldap.domain.repository.LdapUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import javax.naming.Name;


//TODO: JOZIKE saveNew

@Slf4j
@ConditionalOnExpression("${nae.ldap.enabled}")
public class LdapUserService extends UserService {

    @Autowired
    private LdapUserRepository ldapUserRepository;

    public LdapUser findByDn(Name dn) {
        return ldapUserRepository.findByDn(dn.toString());
    }


    @Override
    public IUser saveNew(IUser user) {
        addDefaultRole(user);
        addDefaultAuthorities(user);

        IUser savedUser = ldapUserRepository.save((LdapUser) user);
        groupService.createGroup(user);
        groupService.addUserToDefaultGroup(user);
        publisher.publishEvent(new UserRegistrationEvent(savedUser));
        return savedUser;
    }

    protected LdapUser getUserFromLdap(IUser user) {
        if (user instanceof LdapUser) {
            return (LdapUser) user;
        } else {
            return transformToUserFromLdap(user);
        }
    }


    public LdapUser transformToUserFromLdap(IUser user) {

        LdapUser userFromLdap = ldapUserRepository.findByEmail(user.getEmail());
        if (userFromLdap == null && user.getStringId() != null) {
            userFromLdap = new LdapUser(user.getStringId());
        } else if (userFromLdap == null) {
            userFromLdap = new LdapUser();
        }
        userFromLdap.loadFromUser(user);
        return userFromLdap;
    }

}