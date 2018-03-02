package com.netgrif.workflow.orgstructure.service;

import com.netgrif.workflow.orgstructure.domain.Member;

import java.util.Collection;
import java.util.Set;

public interface IMemberService {

    Member save(Member member);

    Set<Member> findAllByGroups(Collection<Long> groupIds);

    Member findByEmail(String email);
}