package com.invoide.invoices3.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.invoide.invoices3.model.Invoice;

import java.util.List;

/**
 * Repositorio para acceder a los datos de las facturas.
 * Extiende JpaRepository para obtener las operaciones CRUD básicas.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    /**
     * Busca todas las facturas de un cliente específico.
     * Spring Data JPA genera la consulta automáticamente a partir del nombre del método.
     * @param customerId El ID del cliente.
     * @return Una lista de facturas para el cliente.
     */
    List<Invoice> findByCustomerId(String customerId);
}
