package com.duoc.ms_mascotas.DTO;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.TipoMascota;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/* Validación de entradas de los DTOs (Bean Validation), sin levantar Spring */
class ValidacionDtoTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void abrir() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    private CrearMascotaDTO valido() {
        CrearMascotaDTO dto = new CrearMascotaDTO();
        dto.setTipoMascota(TipoMascota.PERRO);
        dto.setEstado(Estado.EXTRAVIADO);
        dto.setComuna("Viña del Mar");
        dto.setEmailContacto("dueno@example.com");
        dto.setUbicacion(new UbicacionDTO(-33.03, -71.55));
        return dto;
    }

    private Set<String> camposInvalidos(Object dto) {
        return validador.validate(dto).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void unReporteCorrectoNoTieneErrores() {
        assertThat(validador.validate(valido())).isEmpty();
    }

    @Test
    void laUbicacionSeValidaDentroDelReporte() {
        CrearMascotaDTO dto = valido();
        dto.setUbicacion(new UbicacionDTO(999.0, -71.55));

        assertThat(camposInvalidos(dto)).contains("ubicacion.latitud");
    }

    @Test
    void laLatitudYLaLongitudTienenRango() {
        assertThat(camposInvalidos(new UbicacionDTO(90.1, 0.0))).containsExactly("latitud");
        assertThat(camposInvalidos(new UbicacionDTO(-90.1, 0.0))).containsExactly("latitud");
        assertThat(camposInvalidos(new UbicacionDTO(0.0, 180.1))).containsExactly("longitud");
        assertThat(camposInvalidos(new UbicacionDTO(0.0, -180.1))).containsExactly("longitud");
        assertThat(validador.validate(new UbicacionDTO(90.0, 180.0))).isEmpty();
        assertThat(validador.validate(new UbicacionDTO(-90.0, -180.0))).isEmpty();
    }

    @Test
    void unaUbicacionSinLatitudONoTieneErrorDeNullPointer() {
        assertThat(camposInvalidos(new UbicacionDTO(null, -71.0))).containsExactly("latitud");
        assertThat(camposInvalidos(new UbicacionDTO(-33.0, null))).containsExactly("longitud");
    }

    @Test
    void unReporteNoPuedeNacerReunificado() {
        CrearMascotaDTO dto = valido();
        dto.setEstado(Estado.REUNIFICADO);

        assertThat(camposInvalidos(dto)).containsExactly("estadoInicialValido");
    }

    @Test
    void elEstadoInicialPuedeSerExtraviadoOEncontrado() {
        for (Estado estado : new Estado[] { Estado.EXTRAVIADO, Estado.ENCONTRADO }) {
            CrearMascotaDTO dto = valido();
            dto.setEstado(estado);

            assertThat(validador.validate(dto)).as(estado.name()).isEmpty();
        }
    }

    @Test
    void elTopeDeCaracteristicasYDeTextosSeMantiene() {
        CrearMascotaDTO dto = valido();
        dto.setNombre("n".repeat(101));
        dto.setDescripcion("d".repeat(1001));
        dto.setFotografia("f".repeat(501));

        assertThat(camposInvalidos(dto)).contains("nombre", "descripcion", "fotografia");
    }

    @Test
    void laEdicionTambienValidaLaUbicacion() {
        ActualizarMascotaDTO dto = new ActualizarMascotaDTO();
        dto.setUbicacion(new UbicacionDTO(-33.0, 999.0));

        assertThat(camposInvalidos(dto)).contains("ubicacion.longitud");
    }

    @Test
    void elNombreLaComunaYLaDescripcionNoAdmitenCaracteresDeControl() {
        CrearMascotaDTO dto = valido();
        dto.setNombre("Firu\r\nBcc: victima@example.com");
        dto.setComuna("Viña\ndel Mar");
        dto.setDescripcion("texto\r\notra linea");

        assertThat(camposInvalidos(dto)).contains("nombre", "comuna", "descripcion");
    }

    @Test
    void laDescripcionSiPuedeTenerSaltosDeLineaNormalesYTabulaciones() {
        CrearMascotaDTO dto = valido();
        dto.setDescripcion("Línea uno\nLínea dos\tcon tabulación, ñandú y acentos áéíóú");

        assertThat(validador.validate(dto)).isEmpty();
    }

    @Test
    void laEdicionTampocoAdmiteCaracteresDeControl() {
        ActualizarMascotaDTO dto = new ActualizarMascotaDTO();
        dto.setComuna("Viña\r\n");
        dto.setDescripcion("x\u0000y");

        assertThat(camposInvalidos(dto)).contains("comuna", "descripcion");
    }

    @Test
    void elCorreoYaNoEsObligatorioPeroSiVieneDebeSerValido() {
        CrearMascotaDTO dto = valido();
        dto.setEmailContacto(null);
        assertThat(validador.validate(dto)).isEmpty();

        dto.setEmailContacto("no-es-un-correo");
        assertThat(camposInvalidos(dto)).contains("emailContacto");
    }
}
