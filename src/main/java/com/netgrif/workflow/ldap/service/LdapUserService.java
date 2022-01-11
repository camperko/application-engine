package com.netgrif.workflow.ldap.service;


import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.ldap.domain.LdapUser;
import com.netgrif.workflow.auth.service.UserService;
import com.netgrif.workflow.ldap.domain.repository.LdapUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import javax.naming.Name;

@Slf4j
@Service
@Primary
@ConditionalOnExpression("${nae.ldap.enabled}")
public class LdapUserService extends UserService {

    @Autowired
    private LdapUserRepository ldapUserRepository;

    public LdapUser findByDn(Name dn) {
        return ldapUserRepository.findByDn(dn.toString());
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
            userFromLdap = new LdapUser(new ObjectId(user.getStringId()));
        } else if (userFromLdap == null) {
            userFromLdap = new LdapUser();
        }
        userFromLdap.loadFromUser(user);
        return userFromLdap;
    }

}