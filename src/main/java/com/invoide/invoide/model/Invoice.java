package com.invoide.invoide.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * Entidad que representa una factura en la base de datos.
 */
@Data
@NoArgsConstructor
@Entity
public class Invoice {

    /**
     * Identificador único de la factura. Se genera automáticamente.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * Identificador del cliente al que pertenece la factura.
     */
    private String customerId;

    /**
     * Fecha de creación de la factura.
     */
    private LocalDate creationDate;

    /**
     * Clave (ruta) del objeto en el bucket de S3.
     * Ejemplo: cliente123/2024-11/factura-abc.pdf
     */
    private String s3Key;

    /**
     * Ruta temporal del archivo en el sistema de archivos EFS.
     */
    private String localEfsPath;

    /**
     * Indicador para saber si la factura ya fue subida a S3.
     */
    private boolean uploadedToS3;
}
