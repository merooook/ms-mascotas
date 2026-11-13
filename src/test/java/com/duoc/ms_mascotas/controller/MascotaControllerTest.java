package com.duoc.ms_mascotas.controller;

import com.duoc.ms_mascotas.exception.SolicitudInvalidaException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.exception.AccesoDenegadoException;
import com.duoc.ms_mascotas.exception.RecursoNoEncontradoException;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.service.MascotaService;
import com.duoc.ms_mascotas.service.MascotaService.ResultadoConfirmacion;
import com.duoc.ms_mascotas.service.S3Service;

/*
 * Pruebas de los endpoints con la cadena de seguridad REAL (SecurityConfig, validación, manejo de errores) y los
 * servicios simulados. Antes se probaba el controller a mano, sin filtros, y por eso un hueco como el de
 * /mascotas/presigned-url público pasó desapercibido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"MONGODB_URI=mongodb://localhost:27017/mascotas_db",
		"internal.api-token=" + MascotaControllerTest.TOKEN_INTERNO
})
public class MascotaControllerTest {

	static final String TOKEN_INTERNO = "token-interno-de-prueba-de-al-menos-32-caracteres";

	private static final String CUERPO_CREAR = """
			{"tipoMascota":"PERRO","nombre":"Luna","estado":"EXTRAVIADO","comuna":"Viña del Mar",
			 "emailContacto":"dueno@example.com","ubicacion":{"latitud":-33.03,"longitud":-71.55}}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MascotaService mascotaService;

	@MockitoBean
	private S3Service s3Service;

	private RequestPostProcessor usuario(String sub) {
		return jwt().jwt(j -> j.subject(sub));
	}

	private RequestPostProcessor administrador() {
		return jwt().jwt(j -> j.subject("admin-1")).authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
	}

	private MascotaResponseDTO respuesta() {
		return MascotaResponseDTO.builder().idMascota("m1").nombre("Luna").build();
	}

	// ---------- lectura pública (invitados) ----------

	@Test
	void listarEsPublicoYNoLlevaIdentidad() throws Exception {
		when(mascotaService.listarConFiltros(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 20)));

		mockMvc.perform(get("/mascotas")).andExpect(status().isOk());

		verify(mascotaService).listarConFiltros(isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
	}

	@Test
	void listarFiltraPorComuna() throws Exception {
		when(mascotaService.listarConFiltros(isNull(), isNull(), isNull(), eq("Viña del Mar"), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 20)));

		mockMvc.perform(get("/mascotas").param("comuna", "Viña del Mar")).andExpect(status().isOk());
	}

	@Test
	void listarConSesionUsaElSubDelToken() throws Exception {
		when(mascotaService.listarConFiltros(any(), any(), any(), any(), any(Pageable.class)))
				.thenReturn(Page.empty(PageRequest.of(0, 20)));

		mockMvc.perform(get("/mascotas").with(usuario("sub-a"))).andExpect(status().isOk());

		verify(mascotaService).listarConFiltros(eq("sub-a"), isNull(), isNull(), isNull(), any(Pageable.class));
	}

	@Test
	void detalleEsPublico() throws Exception {
		when(mascotaService.obtenerPorId(eq("m1"), isNull())).thenReturn(Optional.of(respuesta()));

		mockMvc.perform(get("/mascotas/m1")).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Luna"));
	}

	@Test
	void detalleInexistenteDevuelve404() throws Exception {
		when(mascotaService.obtenerPorId(eq("nada"), isNull())).thenReturn(Optional.empty());

		mockMvc.perform(get("/mascotas/nada")).andExpect(status().isNotFound());
	}

	@Test
	void detalleConSesionDejaAlDuenoVerSuReporte() throws Exception {
		when(mascotaService.obtenerPorId("m1", "sub-a")).thenReturn(Optional.of(respuesta()));

		mockMvc.perform(get("/mascotas/m1").with(usuario("sub-a"))).andExpect(status().isOk());
	}

	// ---------- presigned-url: exige sesión ----------

	@Test
	void presignedUrlSinSesionDevuelve401() throws Exception {
		/* antes caía en la regla pública /mascotas/{id}: cualquiera pedía URLs firmadas de subida a S3 */
		mockMvc.perform(get("/mascotas/presigned-url").param("fileName", "foto.jpg")
				.param("contentType", "image/jpeg").param("fileSize", "1000"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(s3Service);
	}

	@Test
	void presignedUrlConSesionDevuelveLaUrlFirmada() throws Exception {
		when(s3Service.generarUrlFirmada(eq("sub-a"), eq("foto.jpg"), eq("image/jpeg"), eq(1000L)))
				.thenReturn(Map.of("url", "https://bucket/x?firma", "key", "mascotas/x"));

		mockMvc.perform(get("/mascotas/presigned-url").with(usuario("sub-a")).param("fileName", "foto.jpg")
				.param("contentType", "image/jpeg").param("fileSize", "1000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.key").value("mascotas/x"));
	}

	@Test
	void presignedUrlSinParametrosObligatoriosDevuelve400() throws Exception {
		mockMvc.perform(get("/mascotas/presigned-url").with(usuario("sub-a")).param("fileName", "foto.jpg"))
				.andExpect(status().isBadRequest());
	}

	// ---------- crear ----------

	@Test
	void crearSinSesionDevuelve401() throws Exception {
		mockMvc.perform(post("/mascotas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_CREAR))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(mascotaService);
	}

	@Test
	void crearUsaElSubDelTokenYNoUnHeaderDeIdentidad() throws Exception {
		when(mascotaService.crearMascota(any(CrearMascotaDTO.class), eq("sub-a"), any())).thenReturn(respuesta());

		mockMvc.perform(post("/mascotas").with(usuario("sub-a")).header("X-User-Id", "sub-de-la-victima")
				.contentType(MediaType.APPLICATION_JSON).content(CUERPO_CREAR))
				.andExpect(status().isCreated());

		verify(mascotaService).crearMascota(any(CrearMascotaDTO.class), eq("sub-a"), any());
		verify(mascotaService, never()).crearMascota(any(CrearMascotaDTO.class), eq("sub-de-la-victima"), any());
	}

	@Test
	void crearConLatitudFueraDeRangoDevuelve400NoUn500() throws Exception {
		String fueraDeRango = CUERPO_CREAR.replace("-33.03", "999");

		mockMvc.perform(post("/mascotas").with(usuario("sub-a")).contentType(MediaType.APPLICATION_JSON).content(fueraDeRango))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(mascotaService);
	}

	@Test
	void crearConUbicacionSinLatitudDevuelve400NoUnNullPointer() throws Exception {
		String sinLatitud = CUERPO_CREAR.replace("\"latitud\":-33.03,", "");

		mockMvc.perform(post("/mascotas").with(usuario("sub-a")).contentType(MediaType.APPLICATION_JSON).content(sinLatitud))
				.andExpect(status().isBadRequest());
	}

	@Test
	void noSePuedeCrearUnReporteYaReunificado() throws Exception {
		String reunificado = CUERPO_CREAR.replace("\"estado\":\"EXTRAVIADO\"", "\"estado\":\"REUNIFICADO\"");

		mockMvc.perform(post("/mascotas").with(usuario("sub-a")).contentType(MediaType.APPLICATION_JSON).content(reunificado))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(mascotaService);
	}

	// ---------- editar / cambiar estado / eliminar ----------

	@Test
	void actualizarSinSesionDevuelve401() throws Exception {
		mockMvc.perform(patch("/mascotas/m1").contentType(MediaType.APPLICATION_JSON).content("{\"comuna\":\"Quilpué\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void actualizarUsaElSubDelTokenAunqueElClienteMandeOtroXUserId() throws Exception {
		/* el hallazgo 2: con un token válido de A, mandar X-User-Id de B permitía editar los reportes de B */
		when(mascotaService.actualizarMascota(eq("m1"), any(ActualizarMascotaDTO.class), eq("sub-a"))).thenReturn(respuesta());

		mockMvc.perform(patch("/mascotas/m1").with(usuario("sub-a")).header("X-User-Id", "sub-de-la-victima")
				.contentType(MediaType.APPLICATION_JSON).content("{\"comuna\":\"Quilpué\"}"))
				.andExpect(status().isOk());

		verify(mascotaService).actualizarMascota(eq("m1"), any(ActualizarMascotaDTO.class), eq("sub-a"));
		verify(mascotaService, never()).actualizarMascota(any(), any(), eq("sub-de-la-victima"));
	}

	@Test
	void cambiarEstadoUsaElSubDelToken() throws Exception {
		when(mascotaService.cambiarEstado("m1", Estado.ENCONTRADO, "sub-a")).thenReturn(respuesta());

		mockMvc.perform(patch("/mascotas/m1/estado").with(usuario("sub-a")).header("X-User-Id", "sub-de-la-victima")
				.contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ENCONTRADO\"}"))
				.andExpect(status().isOk());

		verify(mascotaService).cambiarEstado("m1", Estado.ENCONTRADO, "sub-a");
	}

	@Test
	void cambiarEstadoSinSesionDevuelve401() throws Exception {
		mockMvc.perform(patch("/mascotas/m1/estado").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ENCONTRADO\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void eliminarUsaElSubDelTokenYResponde204() throws Exception {
		mockMvc.perform(delete("/mascotas/m1").with(usuario("sub-a")).header("X-User-Id", "sub-de-la-victima"))
				.andExpect(status().isNoContent());

		verify(mascotaService).eliminarMascota("m1", "sub-a");
	}

	@Test
	void eliminarSinSesionDevuelve401() throws Exception {
		mockMvc.perform(delete("/mascotas/m1")).andExpect(status().isUnauthorized());
		verifyNoInteractions(mascotaService);
	}

	// ---------- mis mascotas ----------

	@Test
	void misMascotasSinSesionDevuelve401() throws Exception {
		mockMvc.perform(get("/mascotas/mis-mascotas")).andExpect(status().isUnauthorized());
	}

	@Test
	void misMascotasUsaElSubDelToken() throws Exception {
		when(mascotaService.misMascotas(eq("sub-a"), any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));

		mockMvc.perform(get("/mascotas/mis-mascotas").with(usuario("sub-a"))).andExpect(status().isOk());

		verify(mascotaService).misMascotas(eq("sub-a"), any(Pageable.class));
	}

	// ---------- confirmación por correo (pública, la autoriza el token) ----------

	@Test
	void confirmarReunificacionEsPublicaYTraduceElResultado() throws Exception {
		when(mascotaService.confirmarReunificacion("m1", "t-ok")).thenReturn(ResultadoConfirmacion.CONFIRMADA);
		when(mascotaService.confirmarReunificacion("m1", "t-malo")).thenReturn(ResultadoConfirmacion.TOKEN_INVALIDO);
		when(mascotaService.confirmarReunificacion("m1", "t-viejo")).thenReturn(ResultadoConfirmacion.TOKEN_EXPIRADO);

		mockMvc.perform(post("/mascotas/m1/confirmar-reunificacion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"t-ok\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("CONFIRMADA"));
		mockMvc.perform(post("/mascotas/m1/confirmar-reunificacion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"t-malo\"}")).andExpect(status().isNotFound());
		mockMvc.perform(post("/mascotas/m1/confirmar-reunificacion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"t-viejo\"}")).andExpect(status().isGone());
	}

	// ---------- administración ----------

	@Test
	void administracionExigeElRolAdministrador() throws Exception {
		when(mascotaService.listarParaModeracion(any(), any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));

		mockMvc.perform(get("/mascotas/admin").param("estado", "PENDIENTE")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/mascotas/admin").param("estado", "PENDIENTE").with(usuario("sub-a"))).andExpect(status().isForbidden());
		mockMvc.perform(get("/mascotas/admin").param("estado", "PENDIENTE").with(administrador())).andExpect(status().isOk());
	}

	// ---------- errores: cada tipo con su código, sin stack traces ----------

	@Test
	void unReporteInexistenteDevuelve404() throws Exception {
		doThrow(new RecursoNoEncontradoException("Mascota no encontrada")).when(mascotaService).eliminarMascota("x", "sub-a");

		mockMvc.perform(delete("/mascotas/x").with(usuario("sub-a")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Mascota no encontrada"));
	}

	@Test
	void unReporteAjenoDevuelve403() throws Exception {
		doThrow(new AccesoDenegadoException("No tienes permiso para eliminar esta mascota"))
				.when(mascotaService).eliminarMascota("ajena", "sub-a");

		mockMvc.perform(delete("/mascotas/ajena").with(usuario("sub-a"))).andExpect(status().isForbidden());
	}

	@Test
	void unaReglaDeNegocioIncumplidaDevuelve400() throws Exception {
		doThrow(new SolicitudInvalidaException("No se puede cambiar el estado de una mascota reunificada"))
				.when(mascotaService).cambiarEstado("m1", Estado.ENCONTRADO, "sub-a");

		mockMvc.perform(patch("/mascotas/m1/estado").with(usuario("sub-a")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"estado\":\"ENCONTRADO\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void unFiltroConValorInvalidoDevuelve400NoUn500() throws Exception {
		mockMvc.perform(get("/mascotas").param("estado", "FOO")).andExpect(status().isBadRequest());
	}

	@Test
	void unJsonMalformadoDevuelve400() throws Exception {
		mockMvc.perform(post("/mascotas").with(usuario("sub-a")).contentType(MediaType.APPLICATION_JSON).content("{no es json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unMetodoNoSoportadoDevuelve405() throws Exception {
		mockMvc.perform(put("/mascotas/m1").with(usuario("sub-a")).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void unaRutaInexistenteDevuelve404() throws Exception {
		mockMvc.perform(get("/mascotas/m1/no/existe").with(usuario("sub-a"))).andExpect(status().isNotFound());
	}

	// ---------- /internal: solo con el secreto de servicio ----------

	private Mascota mascotaExtraviada() {
		return Mascota.builder().idMascota("m1").nombre("Luna").usuarioId("sub-dueno").emailContacto("dueno@example.com")
				.tokenReunificacion("tok-123").build();
	}

	@Test
	void internalSinCredencialDevuelve401YNoToca() throws Exception {
		mockMvc.perform(post("/internal/mascotas/m1/contacto")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/internal/mascotas/m1/chat")).andExpect(status().isUnauthorized());

		verifyNoInteractions(mascotaService);
	}

	@Test
	void internalConElTokenIncorrectoSeRechaza() throws Exception {
		mockMvc.perform(post("/internal/mascotas/m1/contacto").header("X-Internal-Token", "no-es-el-secreto"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(mascotaService);
	}

	@Test
	void unJwtDeUsuarioNoSustituyeAlSecretoDeServicio() throws Exception {
		/* un usuario normal, aunque tenga sesión válida, no puede usar los endpoints internos */
		mockMvc.perform(post("/internal/mascotas/m1/contacto").with(usuario("sub-a"))).andExpect(status().isForbidden());
		mockMvc.perform(get("/internal/mascotas/m1/chat").with(usuario("sub-a"))).andExpect(status().isForbidden());

		verifyNoInteractions(mascotaService);
	}

	@Test
	void internalConElSecretoCorrectoEntregaElContacto() throws Exception {
		when(mascotaService.obtenerParaContacto("m1")).thenReturn(Optional.of(mascotaExtraviada()));
		when(mascotaService.generarTokenConfirmacion("m1")).thenReturn(mascotaExtraviada());

		mockMvc.perform(post("/internal/mascotas/m1/contacto").header("X-Internal-Token", TOKEN_INTERNO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailDestino").value("dueno@example.com"))
				.andExpect(jsonPath("$.tokenReunificacion").value("tok-123"));
	}

	@Test
	void elContactoInternoYaNoSeAtiendePorGetPorqueTieneEfectoSecundario() throws Exception {
		mockMvc.perform(get("/internal/mascotas/m1/contacto").header("X-Internal-Token", TOKEN_INTERNO))
				.andExpect(status().isMethodNotAllowed());

		verify(mascotaService, never()).generarTokenConfirmacion(any());
	}

	@Test
	void internalChatEntregaElDuenoYSuCorreoSoloConElSecreto() throws Exception {
		when(mascotaService.obtenerParaContacto("m1")).thenReturn(Optional.of(mascotaExtraviada()));

		mockMvc.perform(get("/internal/mascotas/m1/chat").header("X-Internal-Token", TOKEN_INTERNO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.duenoSub").value("sub-dueno"))
				.andExpect(jsonPath("$.duenoEmail").value("dueno@example.com"));
	}

	// ---------- revisión V2: correo verificado, cuotas, Swagger, errores genéricos ----------

	@Test
	void elCorreoSoloSeTomaDelHeaderDelBffCuandoLlegaConElSecretoDeServicio() throws Exception {
		when(mascotaService.crearMascota(any(CrearMascotaDTO.class), eq("sub-a"), any())).thenReturn(respuesta());

		mockMvc.perform(post("/mascotas").with(usuario("sub-a")).header("X-User-Email", "verificado@example.com")
				.header("X-Internal-Token", TOKEN_INTERNO)
				.contentType(MediaType.APPLICATION_JSON).content(CUERPO_CREAR)).andExpect(status().isCreated());
		/* sin el secreto un X-User-Email es de un cliente cualquiera: se ignora (y el body tampoco cuenta) */
		mockMvc.perform(post("/mascotas").with(usuario("sub-b")).header("X-User-Email", "de-un-tercero@example.com")
				.contentType(MediaType.APPLICATION_JSON).content(CUERPO_CREAR));
		when(mascotaService.crearMascota(any(CrearMascotaDTO.class), eq("sub-c"), any())).thenReturn(respuesta());
		mockMvc.perform(post("/mascotas").with(usuario("sub-c")).header("X-User-Email", "de-un-tercero@example.com")
				.header("X-Internal-Token", "token-equivocado-token-equivocado-123456")
				.contentType(MediaType.APPLICATION_JSON).content(CUERPO_CREAR)).andExpect(status().isCreated());

		verify(mascotaService).crearMascota(any(CrearMascotaDTO.class), eq("sub-a"), eq("verificado@example.com"));
		verify(mascotaService).crearMascota(any(CrearMascotaDTO.class), eq("sub-c"), org.mockito.ArgumentMatchers.isNull());
	}

	@Test
	void ahoraElCorreoDelBodyYaNoEsObligatorio() throws Exception {
		when(mascotaService.crearMascota(any(CrearMascotaDTO.class), eq("sub-a"), any())).thenReturn(respuesta());
		String sinCorreo = """
				{"tipoMascota":"PERRO","nombre":"Luna","estado":"EXTRAVIADO","comuna":"Viña del Mar",
				 "ubicacion":{"latitud":-33.03,"longitud":-71.55}}""";

		mockMvc.perform(post("/mascotas").with(usuario("sub-a")).contentType(MediaType.APPLICATION_JSON).content(sinCorreo))
				.andExpect(status().isCreated());
	}

	@Test
	void unUsuarioSoloPuedeCrearUnaCantidadDeReportesPorDia() throws Exception {
		when(mascotaService.crearMascota(any(CrearMascotaDTO.class), eq("sub-spam"), any())).thenReturn(respuesta());
		for (int i = 0; i < 10; i++) {
			mockMvc.perform(post("/mascotas").with(usuario("sub-spam")).contentType(MediaType.APPLICATION_JSON).content(CUERPO_CREAR))
					.andExpect(status().isCreated());
		}

		mockMvc.perform(post("/mascotas").with(usuario("sub-spam")).contentType(MediaType.APPLICATION_JSON).content(CUERPO_CREAR))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"));
	}

	@Test
	void lasUrlsDeSubidaTambienTienenCuotaPorUsuario() throws Exception {
		when(s3Service.generarUrlFirmada(eq("sub-subidas"), any(), any(), org.mockito.ArgumentMatchers.anyLong()))
				.thenReturn(Map.of("url", "https://bucket/x?firma", "key", "mascotas/x"));
		for (int i = 0; i < 30; i++) {
			mockMvc.perform(get("/mascotas/presigned-url").with(usuario("sub-subidas")).param("fileName", "foto.jpg")
					.param("contentType", "image/jpeg").param("fileSize", "1000")).andExpect(status().isOk());
		}

		mockMvc.perform(get("/mascotas/presigned-url").with(usuario("sub-subidas")).param("fileName", "foto.jpg")
				.param("contentType", "image/jpeg").param("fileSize", "1000"))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void swaggerEstaApagadoPorDefecto() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().is4xxClientError());
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().is4xxClientError());
	}

	@Test
	void elMensajeDeUnaExcepcionDeLibreriaNoSeDevuelveAlCliente() throws Exception {
		doThrow(new IllegalArgumentException("detalle interno: campo X de la clase Y"))
				.when(mascotaService).eliminarMascota(any(), any());

		mockMvc.perform(delete("/mascotas/m1").with(usuario("sub-a")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Solicitud inválida"));
	}

	@Test
	void elMensajeDeUnaReglaPropiaDelServicioSiSeDevuelve() throws Exception {
		doThrow(new SolicitudInvalidaException("La imagen debe pesar entre 1 byte y 5 MB"))
				.when(mascotaService).eliminarMascota(any(), any());

		mockMvc.perform(delete("/mascotas/m1").with(usuario("sub-a")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La imagen debe pesar entre 1 byte y 5 MB"));
	}
}
