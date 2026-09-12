package com.duoc.ms_mascotas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.service.MascotaService;

@ExtendWith(MockitoExtension.class)
public class MascotaControllerTest {

	@Mock
	private MascotaService mascotaService;

	@InjectMocks
	private MascotaController mascotaController;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(mascotaController)
				// Sin esto, Spring no sabe resolver el parámetro Pageable del
				// controller cuando se dispara vía MockMvc (no vía llamada directa).
				.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
				.build();
	}

	@Test
	void listarMascotasDelegaAlServicio() {
		PageRequest pageable = PageRequest.of(0, 20);
		when(mascotaService.listarConFiltros(eq("usuario-1"), isNull(), isNull(), eq(pageable)))
				.thenReturn(Page.empty());

		Page<?> resultado = mascotaController.listarMascotas("usuario-1", null, null, pageable);

		org.assertj.core.api.Assertions.assertThat(resultado).isEmpty();
		verify(mascotaService).listarConFiltros("usuario-1", null, null, pageable);
	}

	@Test
	void listarMascotasSinHeaderPermiteAccesoDeInvitado() throws Exception {
		// Page.empty() sin argumentos usa Pageable.unpaged(), que Jackson no
		// sabe serializar (UnsupportedOperationException) — hay que darle un
		// Pageable real, como el que resuelve PageableHandlerMethodArgumentResolver.
		when(mascotaService.listarConFiltros(isNull(), isNull(), isNull(), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 20)));

		mockMvc.perform(get("/mascotas"))
				.andExpect(status().isOk());
	}

	@Test
	void obtenerMascotaSinHeaderPermiteAccesoDeInvitado() throws Exception {
		when(mascotaService.obtenerPorId("mascota-1"))
				.thenReturn(Optional.of(MascotaResponseDTO.builder().idMascota("mascota-1").build()));

		mockMvc.perform(get("/mascotas/mascota-1"))
				.andExpect(status().isOk());
	}

	@Test
	void obtenerMascotaInexistenteRespondeNotFound() throws Exception {
		when(mascotaService.obtenerPorId("desconocida")).thenReturn(Optional.empty());

		mockMvc.perform(get("/mascotas/desconocida"))
				.andExpect(status().isNotFound());
	}

	// Antes de agregar spring-boot-starter-validation al pom, este mismo
	// request pasaba (201) porque @Valid no tenía validador que ejecutar —
	// ni el estado ni el emailContacto faltantes se detectaban.
	@Test
	void crearMascotaSinEmailDeContactoRespondeBadRequest() throws Exception {
		mockMvc.perform(post("/mascotas")
						.header("X-User-Id", "usuario-1")
						.contentType("application/json")
						.content("{\"tipoMascota\":\"PERRO\",\"estado\":\"EXTRAVIADO\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void eliminarMascotaRespondeNoContent() throws Exception {
		mockMvc.perform(delete("/mascotas/mascota-1").header("X-User-Id", "usuario-1"))
				.andExpect(status().isNoContent());

		verify(mascotaService).eliminarMascota("mascota-1", "usuario-1");
	}
}
