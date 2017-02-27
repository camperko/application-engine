package com.fmworkflow.workflow.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fmworkflow.auth.domain.LoggedUser;
import com.fmworkflow.auth.domain.User;
import com.fmworkflow.auth.domain.UserRepository;
import com.fmworkflow.petrinet.domain.Arc;
import com.fmworkflow.petrinet.domain.PetriNet;
import com.fmworkflow.petrinet.domain.Transition;
import com.fmworkflow.petrinet.domain.dataset.Field;
import com.fmworkflow.petrinet.domain.roles.ProcessRoleRepository;
import com.fmworkflow.petrinet.domain.throwable.TransitionNotStartableException;
import com.fmworkflow.workflow.domain.Case;
import com.fmworkflow.workflow.domain.CaseRepository;
import com.fmworkflow.workflow.domain.Task;
import com.fmworkflow.workflow.domain.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TaskService implements ITaskService {

    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private CaseRepository caseRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProcessRoleRepository roleRepository;

    @Override
    public List<Task> getAll(LoggedUser loggedUser) {
//        User user = userRepository.findOne(loggedUser.getId());
//        List<String> roles = new LinkedList<>(user.getUserProcessRoles()).stream().map(UserProcessRole::getRoleId).collect(Collectors.toList());
//        return taskRepository.findAllByAssignRoleIn(roles);
        List<Task> tasks =taskRepository.findAll();
        tasks.forEach(task -> {
            if (task.getUser() != null) {
                User user = task.getUser();
                user.setRoles(new HashSet<>());
                task.setUser(user);
            }
        });
        return tasks;
    }

    @Override
    public List<Task> findByCaseId(String caseId) {
        return taskRepository.findByCaseId(caseId);
    }

    @Override
    public Task findById(Long id) {
        return taskRepository.findOne(id);
    }

    @Override
    public void createTasks(Case useCase) {
        PetriNet net = useCase.getPetriNet();
        Collection<Transition> transitions = net.getTransitions().values();

        for (Transition transition : transitions) {
            if (isExecutable(transition, net, useCase)) {
                Task task = new Task();
                task.setTitle(transition.getTitle());
                task.setCaseId(useCase.get_id().toString());
                task.setTransitionId(transition.getObjectId().toString());
                task.setCaseColor(useCase.getColor());
                task.setPriority(transition.getPriority());
                task = taskRepository.save(task);
                task.setVisualId(net.getInitials());
                task.setAssignRole(net.getRoles().get(transition.getRoles().keySet().stream().findFirst().orElseGet(null)).getStringId());
                taskRepository.save(task);
            }
        }
    }

    private boolean isExecutable(Transition transition, PetriNet net, Case useCase) {
        Collection<Arc> arcsOfTransition = net.getArcsOfTransition(transition);
        Map<String, Integer> activePlaces = useCase.getActivePlaces();

        if (arcsOfTransition == null)
            return true;

        for (Arc arc : arcsOfTransition) {
            if (arc.getDestination() == transition) {
                if (placeIsNotActive(activePlaces, arc) || placeHasInsufficientTokens(activePlaces, arc))
                    return false;
            }
        }

        return true;
    }

    private boolean placeHasInsufficientTokens(Map<String, Integer> activePlaces, Arc arc) {
        return activePlaces.get(arc.getSourceId().toString()) < arc.getMultiplicity();
    }

    private boolean placeIsNotActive(Map<String, Integer> activePlaces, Arc arc) {
        return !activePlaces.containsKey(arc.getSourceId().toString());
    }

    @Override
    public List<Task> findByUser(User user) {
        List<Task> tasks =taskRepository.findByUser(user);
        tasks.forEach(task -> {
            User user1 = task.getUser();
            user1.setRoles(new HashSet<>());
            task.setUser(user1);
        });
        return tasks;
    }

    @Override
    public List<Task> findUserFinishedTasks(User user) {
        List<Task> tasks =taskRepository.findByUserAndFinishDateNotNull(user);
        tasks.forEach(task -> {
            User user1 = task.getUser();
            user1.setRoles(new HashSet<>());
            task.setUser(user1);
        });
        return tasks;
    }

    @Override
    public void finishTask(Long userId, Long taskId) throws Exception {
        Task task = taskRepository.findOne(taskId);
        if (!task.getUser().getId().equals(userId)) {
            throw new Exception("User that is not assigned tried to finish task");
        }

        Case useCase = caseRepository.findOne(task.getCaseId());
        useCase.getPetriNet().initializeArcs();
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());

        useCase.finishTransition(transition);
        task.setFinishDate(LocalDateTime.now());

        caseRepository.save(useCase);
        taskRepository.save(task);

        createTasks(useCase);
    }

    @Override
    public void assignTask(User user, Long taskId) throws TransitionNotStartableException { // TODO: 5. 2. 2017 make transactional
        Task task = taskRepository.findOne(taskId);
        Case useCase = caseRepository.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());

        useCase.startTransition(transition);
        task.setUser(user);
        task.setStartDate(LocalDateTime.now());

        caseRepository.save(useCase);
        taskRepository.save(task);
    }

    @Override
    public List<Field> getData(Long taskId) {
        Task task = taskRepository.findOne(taskId);
        Case useCase = caseRepository.findOne(task.getCaseId());
        Transition transition = useCase.getPetriNet().getTransition(task.getTransitionId());

        Set<String> fieldsIds = transition.getDataSet().keySet();
        List<Field> dataSetFields = new ArrayList<>();
        fieldsIds.forEach(fieldId -> {
            Field field = useCase.getPetriNet().getDataSet().get(fieldId);
            field.setType(null);
            field.setValue(useCase.getDataSetValues().get(fieldId));
            field.setLogic(transition.getDataSet().get(fieldId).apply(JsonNodeFactory.instance.objectNode()));

            dataSetFields.add(field);
        });

        return dataSetFields;
    }

    //TODO: 26.2.2016 generalize values
    @Override
    public void setDataFieldsValues(Long taskId, Map<String, String> values) {
        Task task = taskRepository.findOne(taskId);
        Case useCase = caseRepository.findOne(task.getCaseId());

        values.forEach((key, value) -> useCase.getDataSetValues().put(key, value));
        caseRepository.save(useCase);
    }
}