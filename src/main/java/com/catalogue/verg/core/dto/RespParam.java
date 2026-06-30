package com.catalogue.verg.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RespParam {
    private String resmsgid;
    private String msgid;
    private String err;
    private String status;
    private String errmsg;
}