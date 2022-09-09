package com.bettersms.bulksmsV2.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataModel {

    private final String name;

    private final Object value;
}
