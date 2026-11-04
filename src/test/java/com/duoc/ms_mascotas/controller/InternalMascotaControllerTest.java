package com.duoc.ms_mascotas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/* La lógica de los endpoints internos. El control de acceso (secreto de servicio) se prueba con la cadena real en MascotaControllerTest. */
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

	// ---------- chat ----------

	@Test
	void chatDevuelveElDuenoYSuCorreoParaAvisarle() throws Exception {
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
	void chatRespondeNotFoundSiElReporteNoExisteOEstaPendienteORechazado() throws Exception {
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

	// ---------- contacto (POST: puede emitir un token) ----------

	@Test
	void contactoEntregaElCorreoYElTokenDeReunificacion() throws Exception {
		Mascota conToken = mascota("m1", "sub-dueno");
		conToken.setTokenReunificacion("tok-123");
		when(mascotaService.obtenerParaContacto("m1")).thenReturn(Optional.of(mascota("m1", "sub-dueno")));
		when(mascotaService.generarTokenConfirmacion("m1")).thenReturn(conToken);

		mockMvc.perform(post("/internal/mascotas/m1/contacto"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mascotaId").value("m1"))
				.andExpect(jsonPath("$.emailDestino").value("dueno@example.com"))
				.andExpect(jsonPath("$.nombreMascota").value("Firu"))
				.andExpect(jsonPath("$.tokenReunificacion").value("tok-123"));
	}

	@Test
	void contactoDeUnReporteNoAprobadoDevuelve404SinEmitirToken() throws Exception {
		when(mascotaService.obtenerParaContacto("m2")).thenReturn(Optional.empty());

		mockMvc.perform(post("/internal/mascotas/m2/contacto")).andExpect(status().isNotFound());

		verify(mascotaService, never()).generarTokenConfirmacion(any());
	}
}
