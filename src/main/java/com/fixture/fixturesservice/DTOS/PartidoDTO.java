package com.fixture.fixturesservice.DTOS;
import com.fixture.fixturesservice.enums.Categoria;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
public class PartidoDTO {

    private String local;
    private String visitante;
    private String cancha;
}