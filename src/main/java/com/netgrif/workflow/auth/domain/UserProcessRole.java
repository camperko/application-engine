package com.netgrif.workflow.auth.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Set;

@Entity
public class UserProcessRole {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Getter @Setter
    private Long id;

    @Column(unique = true)
    @Getter @Setter
    private String roleId;

    @Getter @Setter
    private String netId;

    @JsonIgnore
    @ManyToMany(mappedBy = "userProcessRoles")
    private Set<User> users;

    public UserProcessRole() {
    }

    public UserProcessRole(String roleId) {
        this.roleId = roleId;
    }

    public UserProcessRole(String roleId, String netId) {
        this.roleId = roleId;
        this.netId = netId;
    }
}