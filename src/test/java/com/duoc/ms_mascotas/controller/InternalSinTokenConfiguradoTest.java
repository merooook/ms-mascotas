package com.duoc.ms_mascotas.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.duoc.ms_mascotas.service.MascotaService;

/* Falla cerrado: si INTERNAL_API_TOKEN no está configurado (o es corto), /internal/** rechaza todo, incluso un header vacío o "cualquier cosa". */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"MONGODB_URI=mongodb://localhost:27017/mascotas_db",
		"internal.api-token="
})
class InternalSinTokenConfiguradoTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MascotaService mascotaService;

	@Test
	void sinSecretoConfiguradoNingunaLlamadaInternaPasa() throws Exception {
		mockMvc.perform(post("/internal/mascotas/m1/contacto")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/internal/mascotas/m1/contacto").header("X-Internal-Token", "")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/internal/mascotas/m1/chat").header("X-Internal-Token", "cualquier-cosa")).andExpect(status().isUnauthorized());

		verifyNoInteractions(mascotaService);
	}
}
