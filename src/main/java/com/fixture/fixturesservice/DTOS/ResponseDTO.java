package com.fixture.fixturesservice.DTOS;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
public class ResponseDTO implements Serializable {
    private String message;
    private boolean success;
}
