package com.duoc.ms_mascotas.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
	void listarMascotasSinHeaderRespondeBadRequest() throws Exception {
		mockMvc.perform(get("/mascotas"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void obtenerMascotaInexistenteRespondeNotFound() throws Exception {
		when(mascotaService.obtenerPorId("desconocida")).thenReturn(Optional.empty());

		mockMvc.perform(get("/mascotas/desconocida").header("X-User-Id", "usuario-1"))
				.andExpect(status().isNotFound());
	}

	@Test
	void eliminarMascotaRespondeNoContent() throws Exception {
		mockMvc.perform(delete("/mascotas/mascota-1").header("X-User-Id", "usuario-1"))
				.andExpect(status().isNoContent());

		verify(mascotaService).eliminarMascota("mascota-1", "usuario-1");
	}
}
