package com.netgrif.workflow.workflow.service.interfaces;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netgrif.workflow.petrinet.domain.DataGroup;
import com.netgrif.workflow.petrinet.domain.Transition;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.dataset.FileField;
import com.netgrif.workflow.petrinet.domain.dataset.FileListField;
import com.netgrif.workflow.petrinet.domain.dataset.logic.*;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.Action;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.Task;
import com.netgrif.workflow.workflow.domain.eventoutcomes.dataoutcomes.GetDataEventOutcome;
import com.netgrif.workflow.workflow.domain.eventoutcomes.dataoutcomes.SetDataEventOutcome;
import com.netgrif.workflow.workflow.service.FileFieldInputStream;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public interface IDataService {

    GetDataEventOutcome getData(String taskId);

    GetDataEventOutcome getData(Task task, Case useCase);

    SetDataEventOutcome setData(String taskId, ObjectNode values);

    SetDataEventOutcome setData(Task task, ObjectNode values);

    FileFieldInputStream getFile(Case useCase, Task task, FileField field, boolean forPreview);

    FileFieldInputStream getFileByName(Case useCase, FileListField field, String name);

    FileFieldInputStream getFileByTask(String taskId, String fieldId, boolean forPreview) throws FileNotFoundException;

    FileFieldInputStream getFileByTaskAndName(String taskId, String fieldId, String name);

    FileFieldInputStream getFileByCase(String caseId, Task task,  String fieldId, boolean forPreview);

    FileFieldInputStream getFileByCaseAndName(String caseId, String fieldId, String name);

    InputStream download(String url) throws IOException;

    ChangedFieldByFileFieldContainer saveFile(String taskId, String fieldId, MultipartFile multipartFile);

    ChangedFieldByFileFieldContainer saveFiles(String taskId, String fieldId, MultipartFile[] multipartFile);

    boolean deleteFile(String taskId, String fieldId);

    boolean deleteFileByName(String taskId, String fieldId, String name);

    List<DataGroup> getDataGroups(String taskId, Locale locale);

    Page<Task> setImmediateFields(Page<Task> tasks);

    List<Field> getImmediateFields(Task task);

    ChangedFieldsTree runActions(List<Action> actions, String useCaseId, String taskId, Transition transition);

    ChangedFieldsTree runActions(List<Action> actions, String useCaseId, Task task, Transition transition);

    void validateCaseRefValue(List<String> value, List<String> allowedNets) throws IllegalArgumentException;

}