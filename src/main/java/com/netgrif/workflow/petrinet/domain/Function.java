package com.netgrif.workflow.petrinet.domain;


import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Function extends PetriNetObject {

    @Getter
    @Setter
    private String definition;

    @Getter
    @Setter
    private String name;

    public Function() {
        this.setObjectId(new ObjectId());
    }

    public Function clone() {
        Function clone = new Function();
        clone.setObjectId(this.getObjectId());
        clone.setImportId(this.importId);
        clone.setDefinition(this.definition);
        clone.setName(this.name);
        return clone;
    }
}
