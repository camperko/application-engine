package com.netgrif.workflow.petrinet.domain.views;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BooleanImageView extends View {

    private String trueImage;

    private String falseImage;
}