package com.netgrif.workflow.workflow.domain.eventoutcomes.taskoutcomes;

import com.netgrif.workflow.workflow.domain.Task;
import lombok.Data;

@Data
public class FinishTaskEventOutcome extends TaskEventOutcome{

    public FinishTaskEventOutcome() {
    }

    public FinishTaskEventOutcome(Task task) {
        super(task);
    }
}
