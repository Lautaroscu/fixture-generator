package com.fixture.fixturesservice.DTOS;

import java.util.List;
import java.util.Map;

public class EquipoConfig {
    public String nombre;
    public String localidad;
    public String divisionMayor; // "A" | "B"
    public Map<String, Boolean> categorias;
    public boolean estadioPropio;
    public String clubPadre;
    public String estadioLocal;
    public String divisionInfantiles; // <--- AGREGAR ESTO
    public int jerarquia;
    public List<String> restricciones;
}
