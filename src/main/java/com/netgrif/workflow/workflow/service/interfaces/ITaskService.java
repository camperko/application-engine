package com.netgrif.workflow.workflow.service.interfaces;

import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.petrinet.domain.throwable.TransitionNotExecutableException;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.EventOutcome;
import com.netgrif.workflow.workflow.domain.Task;
import com.netgrif.workflow.workflow.web.requestbodies.TaskSearchRequest;
import com.netgrif.workflow.workflow.web.responsebodies.TaskReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface ITaskService {

    @Transactional
    void reloadTasks(Case useCase);

    Task findOne(String taskId);

    Page<Task> getAll(LoggedUser loggedUser, Pageable pageable, Locale locale);

    Page<Task> search(List<TaskSearchRequest> requests, Pageable pageable, LoggedUser user, Locale locale, Boolean isIntersection);

    long count(List<TaskSearchRequest> requests, LoggedUser user, Locale locale, Boolean isIntersection);

    Page<Task> findByCases(Pageable pageable, List<String> cases);

    List<Task> findAllById(List<String> ids);

    void createTasks(Case useCase);

    Page<Task> findByUser(Pageable pageable, IUser user);

    Task findById(String id);

    Page<Task> findByTransitions(Pageable pageable, List<String> transitions);

    Page<Task> searchAll(com.querydsl.core.types.Predicate predicate);

    Page<Task> search(com.querydsl.core.types.Predicate predicate, Pageable pageable);

    Task searchOne(com.querydsl.core.types.Predicate predicate);

    @Transactional(rollbackFor = Exception.class)
    void finishTasks(List<Task> tasks, IUser user) throws TransitionNotExecutableException;

    @Transactional
    EventOutcome finishTask(Task task, IUser user) throws TransitionNotExecutableException;

    EventOutcome finishTask(LoggedUser loggedUser, String taskId) throws IllegalArgumentException, TransitionNotExecutableException;

    EventOutcome finishTask(String taskId) throws IllegalArgumentException, TransitionNotExecutableException;

    @Transactional
    void assignTasks(List<Task> tasks, IUser user) throws TransitionNotExecutableException;

    @Transactional
    EventOutcome assignTask(Task task, IUser user) throws TransitionNotExecutableException;

    EventOutcome assignTask(LoggedUser loggedUser, String taskId) throws TransitionNotExecutableException;

    EventOutcome assignTask(String taskId) throws TransitionNotExecutableException;

    @Transactional(rollbackFor = Exception.class)
    void cancelTasks(List<Task> tasks, IUser user);

    @Transactional
    EventOutcome cancelTask(Task task, IUser user);

    EventOutcome cancelTask(LoggedUser loggedUser, String taskId);

    /**
     * cancel task action
     */
    @SuppressWarnings("unused")
    void cancelTasksWithoutReload(Set<String> transitions, String caseId);

    EventOutcome delegateTask(LoggedUser loggedUser, String delegatedId, String taskId) throws TransitionNotExecutableException;

    void resolveUserRef(Case useCase);

    Task resolveUserRef(Task task, Case useCase);

    void delete(Iterable<? extends Task> tasks, Case useCase);

    void delete(Iterable<? extends Task> tasks, String caseId);

    void deleteTasksByCase(String caseId);

    void deleteTasksByPetriNetId(String petriNetId);

    List<TaskReference> findAllByCase(String caseId, Locale locale);

    Task save(Task task);
}