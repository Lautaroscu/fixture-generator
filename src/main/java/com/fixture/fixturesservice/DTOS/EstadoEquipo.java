package com.fixture.fixturesservice.DTOS;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EstadoEquipo {
    int ultimasConsecutivas;
    boolean usoQuiebre;
    int totalPartidosLocal;

    public EstadoEquipo(){}
    public EstadoEquipo(EstadoEquipo estadoEquipo) {
        ultimasConsecutivas = estadoEquipo.ultimasConsecutivas;
        usoQuiebre = estadoEquipo.usoQuiebre;
        totalPartidosLocal = estadoEquipo.totalPartidosLocal;
    }
    public void actualizar(boolean esLocal) {
        if (esLocal) {
            if (this.ultimasConsecutivas > 0) this.ultimasConsecutivas++;
            else this.ultimasConsecutivas = 1;
            this.totalPartidosLocal++;
        } else {
            if (this.ultimasConsecutivas < 0) this.ultimasConsecutivas--;
            else this.ultimasConsecutivas = -1;
        }
    }

    }

