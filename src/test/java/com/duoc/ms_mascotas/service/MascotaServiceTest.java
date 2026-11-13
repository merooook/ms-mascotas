package com.duoc.ms_mascotas.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.HashMap;
import com.duoc.ms_mascotas.DTO.UbicacionDTO;
import com.duoc.ms_mascotas.exception.RecursoNoEncontradoException;
import com.duoc.ms_mascotas.exception.AccesoDenegadoException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.DTO.MascotaReportadaEvento;
import com.duoc.ms_mascotas.messaging.SnsEventPublisher;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.EstadoModeracion;
import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.model.TipoMascota;
import com.duoc.ms_mascotas.repository.MascotaRepository;

@ExtendWith(MockitoExtension.class)
public class MascotaServiceTest {

	@Mock
	private MascotaRepository mascotaRepository;

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private SnsEventPublisher snsEventPublisher;

	@Mock
	private FotografiaService fotografiaService;

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
		CrearMascotaDTO dto = new CrearMascotaDTO(TipoMascota.PERRO, "Luna", null,
				Estado.EXTRAVIADO, null, "Viña del Mar", "raza mestiza", null, "reportante@example.com");
		when(mascotaRepository.save(any(Mascota.class))).thenReturn(mascota);

		var respuesta = mascotaService.crearMascota(dto, "usuario-1");

		assertThat(respuesta.getIdMascota()).isEqualTo("mascota-1");
		assertThat(respuesta.getEstado()).isEqualTo(Estado.EXTRAVIADO);
		verify(mascotaRepository).save(any(Mascota.class));
	}

	@Test
	void crearMascotaPersisteElEmailDeContacto() {
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));
		CrearMascotaDTO dto = new CrearMascotaDTO(TipoMascota.PERRO, "Luna", null,
				Estado.EXTRAVIADO, null, "Viña del Mar", null, null, "reportante@example.com");

		mascotaService.crearMascota(dto, "usuario-1");

		org.mockito.ArgumentCaptor<Mascota> captor = org.mockito.ArgumentCaptor.forClass(Mascota.class);
		verify(mascotaRepository).save(captor.capture());
		assertThat(captor.getValue().getEmailContacto()).isEqualTo("reportante@example.com");
	}

	@Test
	void crearMascotaFijaElTipoReporteSegunElEstadoInicial() {
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));
		CrearMascotaDTO dto = new CrearMascotaDTO(TipoMascota.PERRO, "Luna", null,
				Estado.EXTRAVIADO, null, "Viña del Mar", null, null, "reportante@example.com");

		var respuesta = mascotaService.crearMascota(dto, "usuario-1");

		assertThat(respuesta.getTipoReporte()).isEqualTo(Estado.EXTRAVIADO);
	}

	@Test
	void cambiarEstadoAReunificadoConCorreoPublicaEventoDeReunificacion() {
		mascota.setEmailContacto("duena@example.com");
		mascota.setNombre("Firulais");
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));

		mascotaService.cambiarEstado("mascota-1", Estado.REUNIFICADO, "usuario-1");

		org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
		verify(snsEventPublisher).publicar(eq(MascotaService.TIPO_EVENTO_MASCOTA_REUNIFICADA), captor.capture());
		com.duoc.ms_mascotas.DTO.MascotaReunificadaEvento evento =
				(com.duoc.ms_mascotas.DTO.MascotaReunificadaEvento) captor.getValue();
		assertThat(evento.getMascotaId()).isEqualTo("mascota-1");
		assertThat(evento.getNombreMascota()).isEqualTo("Firulais");
		assertThat(evento.getEmailContacto()).isEqualTo("duena@example.com");
	}

	@Test
	void cambiarEstadoAReunificadoSinCorreoNoPublicaEvento() {
		// mascota del setUp() no trae emailContacto
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));

		mascotaService.cambiarEstado("mascota-1", Estado.REUNIFICADO, "usuario-1");

		verify(snsEventPublisher, never()).publicar(eq(MascotaService.TIPO_EVENTO_MASCOTA_REUNIFICADA), any(Object.class));
	}

	@Test
	void generarTokenConfirmacionAsignaTokenYExpiracionFuturaParaReporteExtraviado() {
		mascota.setTipoReporte(Estado.EXTRAVIADO);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));

		Mascota resultado = mascotaService.generarTokenConfirmacion("mascota-1");

		assertThat(resultado.getTokenReunificacion()).isNotBlank();
		assertThat(resultado.getTokenReunificacionExpira()).isAfter(LocalDateTime.now());
	}

	@Test
	void generarTokenConfirmacionNoHaceNadaSiYaEstaReunificada() {
		mascota.setEstado(Estado.REUNIFICADO);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		Mascota resultado = mascotaService.generarTokenConfirmacion("mascota-1");

		assertThat(resultado.getTokenReunificacion()).isNull();
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void crearMascotaQuedaPendienteDeModeracion() {
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));
		CrearMascotaDTO dto = new CrearMascotaDTO(TipoMascota.PERRO, "Luna", null,
				Estado.EXTRAVIADO, null, "Viña del Mar", null, null, "reportante@example.com");

		var respuesta = mascotaService.crearMascota(dto, "usuario-1");

		assertThat(respuesta.getEstadoModeracion()).isEqualTo(EstadoModeracion.PENDIENTE);
	}

	@Test
	void reportePendienteNoSePuedeConsultarPublicamente() {
		mascota.setEstadoModeracion(EstadoModeracion.PENDIENTE);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThat(mascotaService.obtenerPorId("mascota-1", null)).isEmpty();
		assertThat(mascotaService.obtenerParaContacto("mascota-1")).isEmpty();
	}

	@Test
	void reportePendienteSiLoPuedeConsultarSuPropioDueno() {
		mascota.setEstadoModeracion(EstadoModeracion.PENDIENTE);
		mascota.setUsuarioId("usuario-1");
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThat(mascotaService.obtenerPorId("mascota-1", "usuario-1")).isPresent();
		assertThat(mascotaService.obtenerPorId("mascota-1", "otro-usuario")).isEmpty();
	}

	@Test
	void aprobarReportePendienteLoHacePublicable() {
		mascota.setEstadoModeracion(EstadoModeracion.PENDIENTE);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));

		var respuesta = mascotaService.aprobarMascota("mascota-1");

		assertThat(respuesta.getEstadoModeracion()).isEqualTo(EstadoModeracion.APROBADA);
		assertThat(mascotaService.obtenerPorId("mascota-1", null)).isPresent();
	}

	@Test
	void rechazarReportePendienteLoMantieneOculto() {
		mascota.setEstadoModeracion(EstadoModeracion.PENDIENTE);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));

		var respuesta = mascotaService.rechazarMascota("mascota-1");

		assertThat(respuesta.getEstadoModeracion()).isEqualTo(EstadoModeracion.RECHAZADA);
		assertThat(mascotaService.obtenerPorId("mascota-1", null)).isEmpty();
	}

	@Test
	void noSePuedeModerarReporteQueYaNoEstaPendiente() {
		mascota.setEstadoModeracion(EstadoModeracion.APROBADA);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThatThrownBy(() -> mascotaService.rechazarMascota("mascota-1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Solo se pueden moderar reportes pendientes");
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void generarTokenConfirmacionNoHaceNadaSiElReporteEsEncontrado() {
		// quien recibe el correo de un reporte ENCONTRADO es quien encontró la
		// mascota, no el dueño real — no tiene autoridad para confirmar
		mascota.setTipoReporte(Estado.ENCONTRADO);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		Mascota resultado = mascotaService.generarTokenConfirmacion("mascota-1");

		assertThat(resultado.getTokenReunificacion()).isNull();
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void confirmarReunificacionConTokenValidoCambiaEstadoYPublicaEvento() {
		mascota.setEmailContacto("duena@example.com");
		mascota.setTokenReunificacion("token-valido");
		mascota.setTokenReunificacionExpira(LocalDateTime.now().plusDays(1));
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));

		var resultado = mascotaService.confirmarReunificacion("mascota-1", "token-valido");

		assertThat(resultado).isEqualTo(MascotaService.ResultadoConfirmacion.CONFIRMADA);
		assertThat(mascota.getEstado()).isEqualTo(Estado.REUNIFICADO);
		assertThat(mascota.getTokenReunificacion()).isNull();
		verify(snsEventPublisher).publicar(eq(MascotaService.TIPO_EVENTO_MASCOTA_REUNIFICADA), any(Object.class));
	}

	@Test
	void confirmarReunificacionConTokenIncorrectoNoCambiaNada() {
		mascota.setTokenReunificacion("token-real");
		mascota.setTokenReunificacionExpira(LocalDateTime.now().plusDays(1));
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		var resultado = mascotaService.confirmarReunificacion("mascota-1", "token-equivocado");

		assertThat(resultado).isEqualTo(MascotaService.ResultadoConfirmacion.TOKEN_INVALIDO);
		assertThat(mascota.getEstado()).isEqualTo(Estado.EXTRAVIADO);
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void confirmarReunificacionConTokenVencidoNoCambiaNada() {
		mascota.setTokenReunificacion("token-valido");
		mascota.setTokenReunificacionExpira(LocalDateTime.now().minusMinutes(1));
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		var resultado = mascotaService.confirmarReunificacion("mascota-1", "token-valido");

		assertThat(resultado).isEqualTo(MascotaService.ResultadoConfirmacion.TOKEN_EXPIRADO);
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void confirmarReunificacionSiYaEstaReunificadaLoInformaSinTocarNada() {
		mascota.setEstado(Estado.REUNIFICADO);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		var resultado = mascotaService.confirmarReunificacion("mascota-1", "cualquier-token");

		assertThat(resultado).isEqualTo(MascotaService.ResultadoConfirmacion.YA_REUNIFICADA);
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void cambiarEstadoAEncontradoNoPublicaEventoDeReunificacion() {
		mascota.setEmailContacto("duena@example.com");
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));

		mascotaService.cambiarEstado("mascota-1", Estado.ENCONTRADO, "usuario-1");

		verify(snsEventPublisher, never()).publicar(eq(MascotaService.TIPO_EVENTO_MASCOTA_REUNIFICADA), any(Object.class));
	}

	@Test
	void cambiarEstadoNoModificaElTipoReporteOriginal() {
		mascota.setTipoReporte(Estado.EXTRAVIADO);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));

		var respuesta = mascotaService.cambiarEstado("mascota-1", Estado.REUNIFICADO, "usuario-1");

		assertThat(respuesta.getEstado()).isEqualTo(Estado.REUNIFICADO);
		assertThat(respuesta.getTipoReporte())
				.as("el origen del reporte no debe cambiar aunque el estado actual sí")
				.isEqualTo(Estado.EXTRAVIADO);
	}

	@Test
	void tipoReporteNuloEnDocumentoAntiguoYaReunificadoQuedaSinClasificar() {
		mascota.setEstado(Estado.REUNIFICADO);
		mascota.setTipoReporte(null); // documento creado antes de este campo
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		var respuesta = mascotaService.obtenerPorId("mascota-1", null).orElseThrow();

		assertThat(respuesta.getTipoReporte()).isNull();
	}

	@Test
	void laUbicacionPublicaSaleRedondeadaPorPrivacidad() {
		// -33.456789, -70.987654 son las coordenadas EXACTAS guardadas — la
		// respuesta pública nunca debe devolver esta precisión.
		mascota.setUbicacion(new GeoJsonPoint(-70.987654, -33.456789));
		when(mascotaRepository.save(any(Mascota.class))).thenReturn(mascota);
		CrearMascotaDTO dto = new CrearMascotaDTO(TipoMascota.PERRO, "Luna", null,
				Estado.EXTRAVIADO, null, "Viña del Mar", null, null, "reportante@example.com");

		var respuesta = mascotaService.crearMascota(dto, "usuario-1");

		assertThat(respuesta.getUbicacion().getLatitud()).isEqualTo(-33.46);
		assertThat(respuesta.getUbicacion().getLongitud()).isEqualTo(-70.99);
	}

	@Test
	void crearMascotaConUbicacionPublicaEventoParaAlertaDeZona() {
		mascota.setUbicacion(new GeoJsonPoint(-70.987654, -33.456789));
		when(mascotaRepository.save(any(Mascota.class))).thenReturn(mascota);
		CrearMascotaDTO dto = new CrearMascotaDTO(TipoMascota.PERRO, "Luna", null,
				Estado.EXTRAVIADO, null, "Viña del Mar", null, null, "reportante@example.com");

		mascotaService.crearMascota(dto, "usuario-1");

		org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
		verify(snsEventPublisher).publicar(eq(MascotaService.TIPO_EVENTO_MASCOTA_REPORTADA), captor.capture());
		MascotaReportadaEvento evento = (MascotaReportadaEvento) captor.getValue();
		assertThat(evento.getMascotaId()).isEqualTo("mascota-1");
		assertThat(evento.getLatitud()).isEqualTo(-33.456789);
		assertThat(evento.getLongitud()).isEqualTo(-70.987654);
	}

	@Test
	void crearMascotaSinUbicacionNoPublicaEventoDeAlerta() {
		when(mascotaRepository.save(any(Mascota.class))).thenReturn(mascota);
		CrearMascotaDTO dto = new CrearMascotaDTO(TipoMascota.PERRO, "Luna", null,
				Estado.EXTRAVIADO, null, "Viña del Mar", null, null, "reportante@example.com");

		mascotaService.crearMascota(dto, "usuario-1");

		verify(snsEventPublisher, never()).publicar(any(String.class), any(Object.class));
	}

	@Test
	void obtenerParaContactoNoExponeElDTOPublico() {
		mascota.setEmailContacto("duena@example.com");
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		var resultado = mascotaService.obtenerParaContacto("mascota-1");

		assertThat(resultado).isPresent();
		assertThat(resultado.get().getEmailContacto()).isEqualTo("duena@example.com");
	}

	@Test
	void listarConFiltrosFiltraPorComuna() {
		// R-N°5 (Anexo de Requisitos): buscar mascotas por comuna. comuna vive
		// como campo propio de Mascota, no dentro de caracteristicas, para
		// poder filtrar así.
		PageRequest pageable = PageRequest.of(0, 10);
		mascota.setComuna("Viña del Mar");
		when(mongoTemplate.count(any(), eq(Mascota.class))).thenReturn(1L);
		when(mongoTemplate.find(any(), eq(Mascota.class))).thenReturn(java.util.List.of(mascota));

		Page<com.duoc.ms_mascotas.DTO.MascotaResponseDTO> resultado =
				mascotaService.listarConFiltros(null, null, null, "Viña del Mar", pageable);

		assertThat(resultado).hasSize(1);
		assertThat(resultado.getContent().get(0).getComuna()).isEqualTo("Viña del Mar");
	}

	@Test
	void listarTodasFiltraPorEstado() {
		PageRequest pageable = PageRequest.of(0, 10);
		when(mongoTemplate.count(any(), eq(Mascota.class))).thenReturn(1L);
		when(mongoTemplate.find(any(), eq(Mascota.class))).thenReturn(java.util.List.of(mascota));

		Page<?> resultado = mascotaService.listarTodas(Estado.EXTRAVIADO, pageable);

		assertThat(resultado).hasSize(1);
		verify(mongoTemplate).count(any(), eq(Mascota.class));
		verify(mongoTemplate).find(any(), eq(Mascota.class));
	}

	@Test
	void actualizarMascotaRechazaUsuarioSinPermiso() {
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThatThrownBy(() -> mascotaService.actualizarMascota("mascota-1", new ActualizarMascotaDTO(), "otro-usuario"))
				.isInstanceOf(AccesoDenegadoException.class)
				.hasMessage("No tienes permiso para actualizar esta mascota");
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void actualizarMascotaSoloModificaLosCamposEnviados() {
		mascota.setComuna("Santiago");
		mascota.setFotografia("https://imagenes.example/luna.jpg");
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));
		ActualizarMascotaDTO dto = new ActualizarMascotaDTO(null, "Valparaíso", null, null, null);

		var respuesta = mascotaService.actualizarMascota("mascota-1", dto, "usuario-1");

		assertThat(respuesta.getComuna()).isEqualTo("Valparaíso");
		assertThat(mascota.getFotografia()).isEqualTo("https://imagenes.example/luna.jpg");
		assertThat(mascota.getEstado()).isEqualTo(Estado.EXTRAVIADO);
		verify(mascotaRepository).save(mascota);
	}

	@Test
	void actualizarMascotaInexistenteRespondeConErrorYNoPersiste() {
		when(mascotaRepository.findById("desconocida")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> mascotaService.actualizarMascota("desconocida", new ActualizarMascotaDTO(), "usuario-1"))
				.isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("Mascota no encontrada");
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

	@Test
	void eliminarMascotaRechazaUsuarioSinPermiso() {
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThatThrownBy(() -> mascotaService.eliminarMascota("mascota-1", "otro-usuario"))
				.isInstanceOf(AccesoDenegadoException.class)
				.hasMessage("No tienes permiso para eliminar esta mascota");
		verify(mascotaRepository, never()).deleteById("mascota-1");
	}

	// =====================================================================================
	// Correcciones de la revisión de código
	// =====================================================================================

	private CrearMascotaDTO crearDto() {
		CrearMascotaDTO dto = new CrearMascotaDTO();
		dto.setTipoMascota(TipoMascota.PERRO);
		dto.setNombre("Luna");
		dto.setEstado(Estado.EXTRAVIADO);
		dto.setComuna("Viña del Mar");
		dto.setEmailContacto("dueno@example.com");
		dto.setUbicacion(new UbicacionDTO(-33.0345, -71.5512));
		return dto;
	}

	private void guardarDevuelveLoRecibido() {
		/* lenient: en las pruebas donde la validación rechaza antes de guardar, este stub no se usa */
		org.mockito.Mockito.lenient().when(mascotaRepository.save(any(Mascota.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private Mascota aprobadaConZona() {
		Mascota m = Mascota.builder().idMascota("mascota-1").usuarioId("usuario-1").tipoMascota(TipoMascota.PERRO)
				.nombre("Luna").estado(Estado.EXTRAVIADO).estadoModeracion(EstadoModeracion.APROBADA)
				.descripcion("Collar rojo").fotografia("mascotas/foto-vieja")
				.ubicacion(new GeoJsonPoint(-71.5512, -33.0345)).caracteristicas(new HashMap<>()).build();
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(m));
		guardarDevuelveLoRecibido();
		return m;
	}

	// ---------- crear: estado, foto, características ----------

	@Test
	void noSePuedeCrearUnReporteYaReunificado() {
		CrearMascotaDTO dto = crearDto();
		dto.setEstado(Estado.REUNIFICADO);

		assertThatThrownBy(() -> mascotaService.crearMascota(dto, "usuario-1")).isInstanceOf(IllegalArgumentException.class);
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void unaFotografiaDeUnOrigenNoPermitidoSeRechazaAlCrear() {
		CrearMascotaDTO dto = crearDto();
		dto.setFotografia("https://sitio-malo.example/pixel.gif");
		when(fotografiaService.esPermitida("https://sitio-malo.example/pixel.gif")).thenReturn(false);

		assertThatThrownBy(() -> mascotaService.crearMascota(dto, "usuario-1"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fotografía");
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void unaFotografiaPermitidaSeGuarda() {
		CrearMascotaDTO dto = crearDto();
		dto.setFotografia("mascotas/foto-ok");
		when(fotografiaService.esPermitida("mascotas/foto-ok")).thenReturn(true);
		guardarDevuelveLoRecibido();

		var respuesta = mascotaService.crearMascota(dto, "usuario-1");

		assertThat(respuesta.getFotografia()).isEqualTo("mascotas/foto-ok");
	}

	@Test
	void lasCaracteristicasConClavesOValoresPeligrososSeRechazan() {
		for (Map<String, Object> malas : List.of(
				Map.<String, Object>of("a.b", "x"),
				Map.<String, Object>of("$where", "x"),
				Map.<String, Object>of("raza", Map.of("anidado", "x")),
				Map.<String, Object>of("raza", "x".repeat(201)),
				Map.<String, Object>of("k".repeat(51), "x"))) {
			CrearMascotaDTO dto = crearDto();
			dto.setCaracteristicas(malas);

			assertThatThrownBy(() -> mascotaService.crearMascota(dto, "usuario-1"))
					.as(malas.toString()).isInstanceOf(IllegalArgumentException.class);
		}
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void unaDescripcionQueEmpiezaConLlaveSeGuardaComoTextoYNoSeMezclaEnElMapa() {
		CrearMascotaDTO dto = crearDto();
		dto.setDescripcion("{\"raza\":\"inventada\",\"$where\":\"1\"}");
		guardarDevuelveLoRecibido();
		ArgumentCaptor<Mascota> captor = ArgumentCaptor.forClass(Mascota.class);

		mascotaService.crearMascota(dto, "usuario-1");

		verify(mascotaRepository).save(captor.capture());
		assertThat(captor.getValue().getCaracteristicas()).containsOnlyKeys("descripcion");
	}

	// ---------- SNS caído: el cambio ya quedó guardado y la petición no falla ----------

	@Test
	void siSnsFallaCrearMascotaIgualRespondeBien() {
		guardarDevuelveLoRecibido();
		doThrow(new RuntimeException("SNS caído")).when(snsEventPublisher).publicar(anyString(), any());

		var respuesta = mascotaService.crearMascota(crearDto(), "usuario-1");

		assertThat(respuesta.getNombre()).isEqualTo("Luna");
		verify(mascotaRepository).save(any(Mascota.class));
	}

	@Test
	void siSnsFallaCambiarEstadoAReunificadoIgualRespondeBien() {
		mascota.setEmailContacto("dueno@example.com");
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		guardarDevuelveLoRecibido();
		doThrow(new RuntimeException("SNS caído")).when(snsEventPublisher).publicar(anyString(), any());

		var respuesta = mascotaService.cambiarEstado("mascota-1", Estado.REUNIFICADO, "usuario-1");

		assertThat(respuesta.getEstado()).isEqualTo(Estado.REUNIFICADO);
	}

	@Test
	void siSnsFallaConfirmarReunificacionIgualQuedaConfirmada() {
		mascota.setEmailContacto("dueno@example.com");
		mascota.setTokenReunificacion("tok");
		mascota.setTokenReunificacionExpira(LocalDateTime.now().plusDays(1));
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		guardarDevuelveLoRecibido();
		doThrow(new RuntimeException("SNS caído")).when(snsEventPublisher).publicar(anyString(), any());

		assertThat(mascotaService.confirmarReunificacion("mascota-1", "tok"))
				.isEqualTo(MascotaService.ResultadoConfirmacion.CONFIRMADA);
	}

	// ---------- editar: moderación, foto, características, dueño ----------

	@Test
	void cambiarLaFotografiaDevuelveElReporteAModeracion() {
		Mascota m = aprobadaConZona();
		when(fotografiaService.esPermitida("mascotas/foto-nueva")).thenReturn(true);

		mascotaService.actualizarMascota("mascota-1", new ActualizarMascotaDTO(null, null, "mascotas/foto-nueva", null, null), "usuario-1");

		assertThat(m.getEstadoModeracion()).isEqualTo(EstadoModeracion.PENDIENTE);
		assertThat(m.getFotografia()).isEqualTo("mascotas/foto-nueva");
	}

	@Test
	void cambiarLaDescripcionDevuelveElReporteAModeracion() {
		Mascota m = aprobadaConZona();

		mascotaService.actualizarMascota("mascota-1", new ActualizarMascotaDTO(null, null, null, "Ahora con collar azul", null), "usuario-1");

		assertThat(m.getEstadoModeracion()).isEqualTo(EstadoModeracion.PENDIENTE);
	}

	@Test
	void unaEdicionSinCambiosRealesNoVuelveAModeracionNiPisaLaCoordenadaExacta() {
		Mascota m = aprobadaConZona();
		/* el frontend solo conoce la ubicación redondeada (2 decimales): reenviarla no debe contar como un cambio */
		ActualizarMascotaDTO dto = new ActualizarMascotaDTO(new UbicacionDTO(-33.03, -71.55), "Viña del Mar", null, "Collar rojo", null);

		mascotaService.actualizarMascota("mascota-1", dto, "usuario-1");

		assertThat(m.getEstadoModeracion()).isEqualTo(EstadoModeracion.APROBADA);
		assertThat(m.getUbicacion().getY()).isEqualTo(-33.0345);
		assertThat(m.getUbicacion().getX()).isEqualTo(-71.5512);
	}

	@Test
	void moverLaUbicacionAOtraZonaDevuelveElReporteAModeracion() {
		Mascota m = aprobadaConZona();

		mascotaService.actualizarMascota("mascota-1",
				new ActualizarMascotaDTO(new UbicacionDTO(-33.45, -70.66), null, null, null, null), "usuario-1");

		assertThat(m.getEstadoModeracion()).isEqualTo(EstadoModeracion.PENDIENTE);
		assertThat(m.getUbicacion().getY()).isEqualTo(-33.45);
	}

	@Test
	void unaFotografiaNoPermitidaSeRechazaAlEditarYNoSeGuarda() {
		aprobadaConZona();
		when(fotografiaService.esPermitida("https://sitio-malo.example/x.png")).thenReturn(false);

		assertThatThrownBy(() -> mascotaService.actualizarMascota("mascota-1",
				new ActualizarMascotaDTO(null, null, "https://sitio-malo.example/x.png", null, null), "usuario-1"))
				.isInstanceOf(IllegalArgumentException.class);
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void unReporteNoPuedeSuperarElTopeDeCaracteristicasAlEditar() {
		Mascota m = aprobadaConZona();
		for (int i = 0; i < 19; i++) {
			m.getCaracteristicas().put("clave" + i, "v");
		}
		Map<String, Object> nuevas = new HashMap<>();
		nuevas.put("extra1", "v");
		nuevas.put("extra2", "v");

		assertThatThrownBy(() -> mascotaService.actualizarMascota("mascota-1",
				new ActualizarMascotaDTO(null, null, null, null, nuevas), "usuario-1"))
				.isInstanceOf(IllegalArgumentException.class);
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void unDocumentoHistoricoSinUsuarioIdDaAccesoDenegadoNoNullPointer() {
		mascota.setUsuarioId(null);
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThatThrownBy(() -> mascotaService.actualizarMascota("mascota-1", new ActualizarMascotaDTO(), "usuario-1"))
				.isInstanceOf(AccesoDenegadoException.class);
		assertThatThrownBy(() -> mascotaService.cambiarEstado("mascota-1", Estado.ENCONTRADO, "usuario-1"))
				.isInstanceOf(AccesoDenegadoException.class);
		assertThatThrownBy(() -> mascotaService.eliminarMascota("mascota-1", "usuario-1"))
				.isInstanceOf(AccesoDenegadoException.class);
	}

	@Test
	void sinIdentidadNadiaEsDuenoDeNada() {
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		assertThatThrownBy(() -> mascotaService.eliminarMascota("mascota-1", null)).isInstanceOf(AccesoDenegadoException.class);
	}

	@Test
	void cambiarElEstadoDeUnReporteInexistenteDevuelveNoEncontrado() {
		when(mascotaRepository.findById("nada")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> mascotaService.cambiarEstado("nada", Estado.ENCONTRADO, "usuario-1"))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	// ---------- eliminar: sin datos huérfanos ----------

	@Test
	void eliminarBorraLaFotoYPublicaElEventoDeEliminacion() {
		mascota.setFotografia("mascotas/foto-ok");
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		mascotaService.eliminarMascota("mascota-1", "usuario-1");

		verify(fotografiaService).eliminar("mascotas/foto-ok");
		verify(snsEventPublisher).publicar(eq(MascotaService.TIPO_EVENTO_MASCOTA_ELIMINADA), any());
	}

	@Test
	void eliminarComoAdministradorTambienLimpiaLosDatosAsociados() {
		mascota.setFotografia("mascotas/foto-ok");
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		mascotaService.eliminarComoAdministrador("mascota-1");

		verify(mascotaRepository).delete(mascota);
		verify(fotografiaService).eliminar("mascotas/foto-ok");
		verify(snsEventPublisher).publicar(eq(MascotaService.TIPO_EVENTO_MASCOTA_ELIMINADA), any());
	}

	@Test
	void siSnsFallaAlEliminarElReporteIgualQuedaEliminado() {
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		doThrow(new RuntimeException("SNS caído")).when(snsEventPublisher).publicar(anyString(), any());

		mascotaService.eliminarMascota("mascota-1", "usuario-1");

		verify(mascotaRepository).deleteById("mascota-1");
	}

	// ---------- token de reunificación: se reutiliza el vigente ----------

	@Test
	void generarTokenReutilizaElVigenteEnVezDeInvalidarElDelCorreoYaEnviado() {
		mascota.setTipoReporte(Estado.EXTRAVIADO);
		mascota.setTokenReunificacion("token-ya-enviado");
		mascota.setTokenReunificacionExpira(LocalDateTime.now().plusDays(3));
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));

		Mascota resultado = mascotaService.generarTokenConfirmacion("mascota-1");

		assertThat(resultado.getTokenReunificacion()).isEqualTo("token-ya-enviado");
		verify(mascotaRepository, never()).save(any(Mascota.class));
	}

	@Test
	void generarTokenEmiteUnoNuevoCuandoElAnteriorYaVencio() {
		mascota.setTipoReporte(Estado.EXTRAVIADO);
		mascota.setTokenReunificacion("token-vencido");
		mascota.setTokenReunificacionExpira(LocalDateTime.now().minusDays(1));
		when(mascotaRepository.findById("mascota-1")).thenReturn(Optional.of(mascota));
		guardarDevuelveLoRecibido();

		Mascota resultado = mascotaService.generarTokenConfirmacion("mascota-1");

		assertThat(resultado.getTokenReunificacion()).isNotEqualTo("token-vencido").isNotBlank();
		assertThat(resultado.getTokenReunificacionExpira()).isAfter(LocalDateTime.now());
	}

	// ---------- listado público: orden y tamaño de página ----------

	private Query consultaDelListado(Pageable pageable) {
		when(mongoTemplate.count(any(Query.class), eq(Mascota.class))).thenReturn(0L);
		when(mongoTemplate.find(any(Query.class), eq(Mascota.class))).thenReturn(List.of());
		var pagina = mascotaService.listarConFiltros(null, null, null, null, pageable);
		assertThat(pagina).isEmpty();
		ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
		verify(mongoTemplate).find(captor.capture(), eq(Mascota.class));
		return captor.getValue();
	}

	@Test
	void elListadoPublicoNoSePuedeOrdenarPorCamposPrivados() {
		for (String campo : List.of("emailContacto", "usuarioId", "tokenReunificacion")) {
			Pageable pedida = PageRequest.of(0, 20, Sort.by(campo).ascending());

			assertThatThrownBy(() -> mascotaService.listarConFiltros(null, null, null, null, pedida))
					.as(campo).isInstanceOf(IllegalArgumentException.class);
		}
		verify(mongoTemplate, never()).find(any(Query.class), eq(Mascota.class));
	}

	@Test
	void elListadoPublicoSiSePuedeOrdenarPorFechaYOtrosCamposPublicos() {
		Query consulta = consultaDelListado(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fecha")));

		assertThat(consulta.getSortObject().get("fecha")).isEqualTo(-1);
	}

	@Test
	void elTamanoDePaginaSeAcotaA200() {
		Query consulta = consultaDelListado(PageRequest.of(0, 5000));

		assertThat(consulta.getLimit()).isEqualTo(200);
	}
}
