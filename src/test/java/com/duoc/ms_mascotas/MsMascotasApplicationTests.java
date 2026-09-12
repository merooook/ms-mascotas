package com.duoc.ms_mascotas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.duoc.ms_mascotas.service.MascotaService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Ya no fija spring.mvc.servlet.path=/api/v1: ese prefijo era el que se
// revirtió en la reunión del 30-ago (commit 9bbdb32) por chocar con el BFF
// (ProxyService le quita /api/v1 antes de reenviar). El controller vive en
// /mascotas directo — este smoke test valida que el contexto completo
// levanta (seguridad, Eureka/Config deshabilitados en el perfil test,
// Mongo, etc.) y que el listado responde para invitados sin X-User-Id.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"MONGODB_URI=mongodb://localhost:27017/mascotas_db"
})
class MsMascotasApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MascotaService mascotaService;

	@Test
	void contextLoadsAndListadoEsAccesibleParaInvitados() throws Exception {
		when(mascotaService.listarConFiltros(isNull(), isNull(), isNull(), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 20)));
		mockMvc.perform(get("/mascotas"))
				.andExpect(status().isOk());
	}

}
