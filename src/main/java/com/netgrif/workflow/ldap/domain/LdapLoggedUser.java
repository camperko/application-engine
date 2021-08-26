package com.netgrif.workflow.ldap.domain;


import com.netgrif.workflow.auth.domain.Authority;
import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.auth.domain.UserState;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.stream.Collectors;


public class LdapLoggedUser extends LoggedUser {

    @Getter
    @Setter
    private String dn;

    @Getter
    @Setter
    private String commonName;

    @Getter
    @Setter
    private String uid;

    @Getter
    @Setter
    private String homeDirectory;


    public LdapLoggedUser(Long id, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        super(id, username, password, authorities);
    }


    public LdapLoggedUser(Long id, String username, String password, String dn, String commonName, String uid, String homeDirectory, Collection<? extends GrantedAuthority> authorities) {
        super(id, username, password, authorities);
        this.dn = dn;
        this.commonName = commonName;
        this.uid = uid;
        this.homeDirectory = homeDirectory;
    }


    public User transformToUser() {
        LdapUser user = new LdapUser();
        user.setEmail(getUsername());
        String[] names = this.getFullName().split(" ");
        user.setName(names[0]);
        user.setSurname(names[1]);
        user.setDn(this.dn);
        user.setCommonName(this.commonName);
        user.setUid(this.uid);
        user.setHomeDirectory(homeDirectory);
        user.setState(UserState.ACTIVE);
        user.setAuthorities(getAuthorities().stream().map(a -> ((Authority) a)).collect(Collectors.toSet()));
//        user.setNextGroups(this.getGroups().);
//        user.setProcessRoles(processRoles.stream().map(roleId -> {
//            ProcessRole role = new ProcessRole();
//            role.set_id(roleId);
//            return role;
//        }).collect(Collectors.toSet()));
//TODO: JOZIKEEE
        return user;
    }
}