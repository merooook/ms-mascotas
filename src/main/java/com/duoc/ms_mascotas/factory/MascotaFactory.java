package com.duoc.ms_mascotas.factory;

import org.springframework.stereotype.Component;

import com.duoc.ms_mascotas.mascotas.ConejoMascota;
import com.duoc.ms_mascotas.mascotas.GatoMascota;
import com.duoc.ms_mascotas.mascotas.OtraMascota;
import com.duoc.ms_mascotas.mascotas.PerroMascota;
import com.duoc.ms_mascotas.model.Mascota;

@Component
public class MascotaFactory {

    public Mascota crearMascota(String especie) {
        if (especie == null) {
            throw new IllegalArgumentException("La especie es requerida");
        }

        return switch (especie.trim().toLowerCase()) {
            case "perro" -> new PerroMascota();
            case "gato" -> new GatoMascota();
            case "conejo" -> new ConejoMascota();
            case "otro" -> new OtraMascota();
            default -> throw new IllegalArgumentException("Especie no válida: " + especie);
        };
    }
}
