package com.fmworkflow.auth.service;

import com.fmworkflow.auth.domain.Role;
import com.fmworkflow.auth.domain.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleService implements IRoleService {

    @Autowired
    private RoleRepository repository;

    @Override
    public List<Role> findAll() {
        return repository.findAll();
    }
}