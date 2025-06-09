package com.invoide.invoide.controller;

import com.invoide.invoide.model.Invoice;
import com.invoide.invoide.service.IInvoiceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para gestionar las operaciones de las facturas.
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final IInvoiceService invoiceService;

    public InvoiceController(IInvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * Crea una nueva factura.
     * Payload esperado: {"customerId": "id_cliente", "content": "contenido_factura"}
     */
    @PostMapping
    public ResponseEntity<Invoice> createInvoice(@RequestBody Map<String, String> payload) throws IOException {
        String customerId = payload.get("customerId");
        String content = payload.getOrDefault("content", "Contenido de la factura por defecto.");
        Invoice invoice = invoiceService.createInvoice(customerId, content);
        return new ResponseEntity<>(invoice, HttpStatus.CREATED);
    }

    /**
     * Sube una factura generada desde EFS a S3.
     */
    @PostMapping("/{invoiceId}/upload")
    public ResponseEntity<Invoice> uploadInvoiceToS3(@PathVariable String invoiceId) throws IOException {
        Invoice invoice = invoiceService.uploadInvoiceToS3(invoiceId);
        return ResponseEntity.ok(invoice);
    }
    
    /**
     * Descarga una factura desde S3.
     */
    @GetMapping("/{invoiceId}/download")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable String invoiceId) {
        byte[] data = invoiceService.downloadInvoice(invoiceId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", invoiceId + ".pdf");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
    
    /**
     * Elimina una factura de la base de datos y de S3 si existe.
     */
    @DeleteMapping("/{invoiceId}")
    public ResponseEntity<Void> deleteInvoice(@PathVariable String invoiceId) {
        invoiceService.deleteInvoice(invoiceId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Consulta el historial de facturas para un cliente.
     */
    @GetMapping("/history/{customerId}")
    public ResponseEntity<List<Invoice>> getInvoiceHistory(@PathVariable String customerId) {
        List<Invoice> history = invoiceService.getCustomerInvoiceHistory(customerId);
        return ResponseEntity.ok(history);
    }
    
    /**
     * Modifica/Actualiza una factura.
     * Payload esperado: {"customerId": "nuevo_id_cliente"}
     */
    @PutMapping("/{invoiceId}")
    public ResponseEntity<Invoice> updateInvoice(@PathVariable String invoiceId, @RequestBody Map<String, String> payload) {
        String newCustomerId = payload.get("customerId");
        Invoice updatedInvoice = invoiceService.updateInvoice(invoiceId, newCustomerId);
        return ResponseEntity.ok(updatedInvoice);
    }
}