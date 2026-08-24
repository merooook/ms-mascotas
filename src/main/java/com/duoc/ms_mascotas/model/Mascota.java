package com.duoc.ms_mascotas.model;

import java.sql.Date;

import org.springframework.data.geo.Point;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "Mascota")
@Data
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class Mascota {

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //no sé si esto va aquí ya que será el factory de la mascota, pero por ahora lo dejo comentado
    //@Enumerated(EnumType.STRING)
    //@Column(length = 20)
    //private TipoMascota tipoMascota;

    @Column(nullable = false, length = 50)
    @NotBlank(message = "El nombre de la mascota no puede estar vacío")
    private String nombre;

    @Column(nullable = false)
    private String duenoId;

    @Enumerated(EnumType.STRING)
    @Column(length = 28)
    private Raza raza;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Patron patron;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Color color;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sexo sexo;

    @Column(length = 255)
    private String fotografia;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Estado estado;

    @Column(columnDefinition = "geography(POINT,4326)", nullable = false)
    private Point ubicacion;

    @Column(nullable = false)
    private Date fecha;

    @Column(nullable = false, length = 200)
    private String descripcion;

}
