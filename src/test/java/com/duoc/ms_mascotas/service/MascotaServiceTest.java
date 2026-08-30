package com.duoc.ms_mascotas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.model.TipoMascota;
import com.duoc.ms_mascotas.repository.MascotaRepository;

@ExtendWith(MockitoExtension.class)
public class MascotaServiceTest {

	@Mock
	private MascotaRepository mascotaRepository;

	@Mock
	private MongoTemplate mongoTemplate;

	@InjectMocks
	private MascotaService mascotaService;

	private Mascota mascota;

	@BeforeEach
	void setUp() {
		mascota = Mascota.builder()
				.idMascota("mascota-1")
				.usuarioId("usuario-1")
				.tipoMascota(TipoMascota.PERRO)
				.nombre("Luna")
				.estado(Estado.EXTRAVIADO)
				.caracteristicas(Map.of("raza", "mestiza"))
				.build();
	}

	@Test
	void crearMascotaAsignaEstadoYMapeaLaRespuesta() {
		CrearMascotaDTO dto = new CrearMascotaDTO(TipoMascota.PERRO, "Luna", "negro", null,
				Estado.EXTRAVIADO, null, "raza mestiza", null);
		when(mascotaRepository.save(any(Mascota.class))).thenReturn(mascota);

		var respuesta = mascotaService.crearMascota(dto, "usuario-1");

		assertThat(respuesta.getIdMascota()).isEqualTo("mascota-1");
		assertThat(respuesta.getUsuarioId()).isEqualTo("usuario-1");
		assertThat(respuesta.getEstado()).isEqualTo(Estado.EXTRAVIADO);
		verify(mascotaRepository).save(any(Mascota.class));
	}

	@Test
	void listarTodasFiltraPorEstado() {
		PageRequest pageable = PageRequest.of(0, 10);
		when(mascotaRepository.findByEstado(Estado.EXTRAVIADO, pageable))
				.thenReturn(new PageImpl<>(java.util.List.of(mascota), pageable, 1));

		Page<?> resultado = mascotaService.listarTodas(Estado.EXTRAVIADO, pageable);

		assertThat(resultado).hasSize(1);
		verify(mascotaRepository).findByEstado(Estado.EXTRAVIADO, pageable);
	}

	@Test
	void actualizarMascotaRechazaUsuarioSinPermiso() {
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThatThrownBy(() -> mascotaService.actualizarMascota("mascota-1", new ActualizarMascotaDTO(), "otro-usuario"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("No tienes permiso para actualizar esta mascota");
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void cambiarEstadoRechazaMascotaReunificada() {
		mascota.setEstado(Estado.REUNIFICADO);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThatThrownBy(() -> mascotaService.cambiarEstado("mascota-1", Estado.ENCONTRADO, "usuario-1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("No se puede cambiar el estado de una mascota reunificada");
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void eliminarMascotaSoloEliminaSiPerteneceAlUsuario() {
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		mascotaService.eliminarMascota("mascota-1", "usuario-1");

		verify(mascotaRepository).deleteById("mascota-1");
	}
}
