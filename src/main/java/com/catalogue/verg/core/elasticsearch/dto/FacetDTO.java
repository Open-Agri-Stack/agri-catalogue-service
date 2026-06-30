package com.catalogue.verg.core.elasticsearch.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FacetDTO implements Serializable {

    private String value;

    private Long count;
}