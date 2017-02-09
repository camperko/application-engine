package com.fmworkflow.auth.domain;

import org.springframework.security.core.GrantedAuthority;

import javax.jws.soap.SOAPBinding;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class LoggedUser extends org.springframework.security.core.userdetails.User {

    private Long id;
    private String fullName;
    private Map<String, List<String>> workflowRoles;

    public LoggedUser(Long id, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.id = id;
        this.workflowRoles = new HashMap<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Map<String, List<String>> getWorkflowRoles() {
        return workflowRoles;
    }

    public void setWorkflowRoles(Map<String, List<String>> workflowRoles) {
        this.workflowRoles = workflowRoles;
    }

    public User transformToUser(){
        User user = new User();
        user.setId(this.id);
        user.setEmail(getUsername());
        String[] names = this.fullName.split(" ");
        user.setName(names[0]);
        user.setSurname(names[1]);
        user.setPassword(getPassword());

        return user;
    }
}
