package com.netgrif.application.engine.ldap.domain;

import com.netgrif.application.engine.auth.domain.IUser;
import com.netgrif.application.engine.auth.domain.LoggedUser;
import com.netgrif.application.engine.auth.domain.User;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;


@Data
@Document(collection = "user")
public class LdapUser extends User {

    private String dn;

    private String commonName;

    private String uid;

    private String homeDirectory;

    public LdapUser() {
    }

    public LdapUser(ObjectId id) {
        this();
        this._id = id;
        nextGroups = new HashSet<>();
    }

    public LdapUser(String dn, String commonName, String uid, String homeDirectory,
                    String email, String name, String surname, String telNumber) {
        this.setEmail(email);
        this.setName(name);
        this.setSurname(surname);
        this.setDn(dn);
        this.setCommonName(commonName);
        this.setUid(uid);
        this.setHomeDirectory(homeDirectory);
        this.setTelNumber(telNumber);

    }

    @Override
    public String getStringId() {
        return _id.toString();
    }


    @Override
    public LoggedUser transformToLoggedUser() {
        LdapLoggedUser loggedUser = new LdapLoggedUser(this.getStringId(), this.getEmail(), this.getPassword(), getDn(), getCommonName(), getUid(), getHomeDirectory(), this.getAuthorities());
        loggedUser.setFullName(this.getFullName());
        if (!this.getProcessRoles().isEmpty())
            loggedUser.parseProcessRoles(this.getProcessRoles());
        loggedUser.setGroups(this.getNextGroups());

        return loggedUser;
    }

    public void loadFromUser(IUser user) {
        this.setEmail(user.getEmail());
        this.setName(user.getName());
        this.setSurname(user.getSurname());
        this.setAvatar(user.getAvatar());
        this.setTelNumber(user.getTelNumber());
        this.setNextGroups(user.getNextGroups());
        this.setProcessRoles(user.getProcessRoles());
        this.setState(user.getState());
    }
}