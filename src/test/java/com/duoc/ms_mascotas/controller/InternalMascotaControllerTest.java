package com.duoc.ms_mascotas.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.service.MascotaService;

@ExtendWith(MockitoExtension.class)
class InternalMascotaControllerTest {

	@Mock
	private MascotaService mascotaService;

	@InjectMocks
	private InternalMascotaController controller;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	private Mascota mascota(String id, String usuarioId) {
		return Mascota.builder().idMascota(id).nombre("Firu").usuarioId(usuarioId)
				.emailContacto("dueno@example.com").build();
	}

	@Test
	void devuelveElDuenoParaQueMsChatPuedaIniciarLaConversacion() throws Exception {
		when(mascotaService.obtenerParaContacto("m1")).thenReturn(Optional.of(mascota("m1", "sub-dueno")));

		mockMvc.perform(get("/internal/mascotas/m1/chat"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mascotaId").value("m1"))
				.andExpect(jsonPath("$.nombreMascota").value("Firu"))
				.andExpect(jsonPath("$.duenoSub").value("sub-dueno"))
				// el correo viaja solo para avisarle al dueño que tiene un mensaje nuevo (nunca al frontend)
				.andExpect(jsonPath("$.duenoEmail").value("dueno@example.com"))
				.andExpect(jsonPath("$.emailContacto").doesNotExist())
				.andExpect(jsonPath("$.emailDestino").doesNotExist());
	}

	@Test
	void respondeNotFoundSiElReporteNoExisteOEstaPendienteORechazado() throws Exception {
		/* obtenerParaContacto ya filtra los reportes que no están aprobados */
		when(mascotaService.obtenerParaContacto("m2")).thenReturn(Optional.empty());

		mockMvc.perform(get("/internal/mascotas/m2/chat")).andExpect(status().isNotFound());
	}

	@Test
	void unReporteHistoricoSinUsuarioIdNoAdmiteChat() throws Exception {
		when(mascotaService.obtenerParaContacto("m3")).thenReturn(Optional.of(mascota("m3", null)));
		when(mascotaService.obtenerParaContacto("m4")).thenReturn(Optional.of(mascota("m4", "  ")));

		mockMvc.perform(get("/internal/mascotas/m3/chat")).andExpect(status().isNotFound());
		mockMvc.perform(get("/internal/mascotas/m4/chat")).andExpect(status().isNotFound());
	}
}
