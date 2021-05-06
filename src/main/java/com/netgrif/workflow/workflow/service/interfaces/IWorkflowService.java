package com.netgrif.workflow.workflow.service.interfaces;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.I18nString;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.dataset.logic.ChangedField;
import com.netgrif.workflow.petrinet.domain.dataset.logic.ChangedFieldsTree;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.Action;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.Task;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface IWorkflowService {

    Case save(Case useCase);

    Case findOne(String caseId);

    List<Case> findAllById(List<String> ids);

    Page<Case> getAll(Pageable pageable);

    Case resolveUserRef(Case useCase);

    Case createCase(String netId, String title, String color, LoggedUser user, Locale locale);

    Case createCase(String netId, String title, String color, LoggedUser user);

    Page<Case> findAllByAuthor(Long authorId, String petriNet, Pageable pageable);

    void deleteCase(String caseId);

    void deleteSubtreeRootedAt(String caseId);

    void deleteInstancesOfPetriNet(PetriNet net);

    void updateMarking(Case useCase);

    Page<Case> searchAll(Predicate predicate);

    Case searchOne(Predicate predicate);

    Map<String, I18nString> listToMap(List<Case> cases);

    List<Field> getData(String caseId);

    Page<Case> search(Map<String, Object> request, Pageable pageable, LoggedUser user, Locale locale);

    long count(Map<String, Object> request, LoggedUser user, Locale locale);

//    List<Case> getCaseFieldChoices(Pageable pageable, String caseId, String fieldId);

    boolean removeTasksFromCase(Iterable<? extends Task> tasks, String caseId);

    boolean removeTasksFromCase(Iterable<? extends Task> tasks, Case useCase);

    Case decrypt(Case useCase);

    Page<Case> search(Predicate predicate, Pageable pageable);

    ChangedFieldsTree runActions(List<Action> actions, String useCaseId);

    void runActions(List<Action> actions);
}