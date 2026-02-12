package com.fixture.fixturesservice.entities;

import com.fixture.fixturesservice.enums.Categoria;
import com.fixture.fixturesservice.enums.Liga;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Getter @Setter
public class Fecha {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private int nroFecha;

    @OneToMany(mappedBy = "fecha", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Partido> partidos = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    private Categoria categoria;
    @Enumerated(EnumType.STRING)
    private Liga liga; // A o B

    public Fecha() {}
    public Fecha(int nro) { this.nroFecha = nro; }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------------------------------------\n");
        sb.append(String.format("FECHA NRO: %d\n", nroFecha));
        sb.append("--------------------------------------------------\n");

        if (partidos == null || partidos.isEmpty()) {
            sb.append("Sin partidos programados.\n");
        } else {
            for (Partido p : partidos) {
                String localNom = (p.getLocal() != null) ? p.getLocal().getNombre() : "TBD";
                String visitaNom = (p.getVisitante() != null) ? p.getVisitante().getNombre() : "TBD";
                String sedeNom = (p.getCancha() != null) ? p.getCancha().getName() : "SIN SEDE";

                sb.append(String.format("  %-15s vs. %-15s | Sede: %-15s\n",
                        localNom, visitaNom, sedeNom));
            }
        }
        sb.append("--------------------------------------------------\n");
        return sb.toString();
    }
    public void addPartido(Partido partido) {
        partidos.add(partido);
        partido.setFecha(this);
    }
}
