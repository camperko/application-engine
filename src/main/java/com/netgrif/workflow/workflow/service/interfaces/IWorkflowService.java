package com.netgrif.workflow.workflow.service.interfaces;

import com.netgrif.workflow.workflow.domain.Case;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IWorkflowService {
    void saveCase(Case useCase);

    Page<Case> getAll(Pageable pageable);

    Page<Case> searchCase(List<String> nets, Pageable pageable);

    void createCase(String netId, String title, String color, Long authorId);

    Page<Case> findAllByAuthor(Long authorId, Pageable pageable);
}