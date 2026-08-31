package com.duoc.ms_mascotas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.duoc.ms_mascotas.service.MascotaService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"MONGODB_URI=mongodb://localhost:27017/mascotas_db",
		"spring.mvc.servlet.path=/api/v1"
})
class MsMascotasApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MascotaService mascotaService;

	@Test
	void dockerProfileStartsAndExposesVersionedApi() throws Exception {
		when(mascotaService.listarConFiltros(eq("integration-test"), isNull(), isNull(), any(Pageable.class)))
				.thenReturn(Page.empty());
		mockMvc.perform(get("/api/v1/mascotas").header("X-User-Id", "integration-test"))
				.andExpect(status().isOk());
	}

}
