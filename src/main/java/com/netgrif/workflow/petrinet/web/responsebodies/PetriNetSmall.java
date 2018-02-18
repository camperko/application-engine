package com.netgrif.workflow.petrinet.web.responsebodies;

import com.netgrif.workflow.auth.domain.Author;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.PetriNetObject;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.Locale;

public class PetriNetSmall extends PetriNetObject {

    @Getter @Setter
    private String title;

    @Getter @Setter
    private String initials;

    @Getter @Setter
    private String icon;

    // TODO: 18. 3. 2017 replace with Spring auditing
    @Getter @Setter
    private LocalDateTime creationDate;

    @Getter @Setter
    private Author author;

    @Getter @Setter
    private Integer places;

    @Getter @Setter
    private Integer transitions;

    @Getter @Setter
    private Integer arcs;

    @Getter @Setter
    private Integer dataSet;

    @Getter @Setter
    private Integer actions;

    @Getter @Setter
    private Integer roles;

    public PetriNetSmall(ObjectId id, String title, String initials) {
        super._id = id;
        this.title = title;
        this.initials = initials;
    }

    public static PetriNetSmall fromPetriNet(PetriNet original, Locale locale){
        String title = locale != null ? original.getTitle().getTranslation(locale) : original.getTitle().getDefaultValue();
        PetriNetSmall small = new PetriNetSmall(original.getObjectId(), title,original.getInitials());
        small.setIcon(original.getIcon());
        small.setAuthor(original.getAuthor() == null ? new Author(null, "nae@netgrif.com", "System") : original.getAuthor());
        small.setCreationDate(original.getCreationDate());
        small.setPlaces(original.getPlaces().size());
        small.setTransitions(original.getTransitions().size());
        small.setArcs(Integer.parseInt(original.getArcs().entrySet().stream().reduce(new AbstractMap.SimpleEntry<>("0",null),
                (x,y) -> new AbstractMap.SimpleEntry<>((Integer.parseInt(x.getKey()) + y.getValue().size()) + "", null)).getKey()));
        small.setDataSet(original.getDataSet().size());
        small.setActions(original.getDataSet().values().stream().mapToInt(f -> f.getActions() == null ? 0 : f.getActions().size()).sum());
        small.setRoles(original.getRoles().size());

        return small;
    }

    @Override
    public String toString() {
        return "PetriNetSmall{" +
                "title='" + title + '\'' +
                ", initials='" + initials + '\'' +
                ", icon='" + icon + '\'' +
                ", creationDate=" + creationDate +
                ", author=" + author +
                ", places=" + places +
                ", transitions=" + transitions +
                ", arcs=" + arcs +
                ", dataSet=" + dataSet +
                ", roles=" + roles +
                '}';
    }
}