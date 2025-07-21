package com.invoide.invoices3.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import com.invoide.invoices3.config.RabbitMQConfig;
import com.invoide.invoices3.model.Invoice;
import com.invoide.invoices3.repository.InvoiceRepository;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import java.io.FileOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implementación de la lógica de negocio para la gestión de facturas.
 */
@Service
public class InvoiceServiceImpl implements IInvoiceService {

    private final S3Client s3Client;
    private final InvoiceRepository invoiceRepository;
    private final String efsMountPath;
    private final String bucketName;
    private final RabbitTemplate rabbitTemplate;
    private final String invoiceUploadQueueName; // Nombre de la cola (ahora de la constante)
    private final String mainExchangeName; // AÑADIDO: Nombre del intercambio principal (de la constante)

    public InvoiceServiceImpl(S3Client s3Client, InvoiceRepository invoiceRepository,
                              @Value("${efs.mount.path}") String efsMountPath,
                              @Value("${s3.bucket.name}") String bucketName,
                              RabbitTemplate rabbitTemplate
                              /* ELIMINADO: @Value("${invoide.rabbitmq.queue-name}") String invoiceUploadQueueName */) { // MODIFICADO: Se elimina la inyección @Value para el nombre de la cola
        this.s3Client = s3Client;
        this.invoiceRepository = invoiceRepository;
        this.efsMountPath = efsMountPath;
        this.bucketName = bucketName;
        this.rabbitTemplate = rabbitTemplate;

        // MODIFICADO: Asignar nombres de cola y exchange desde las constantes de RabbitMQConfig
        this.invoiceUploadQueueName = RabbitMQConfig.MAIN_QUEUE;
        this.mainExchangeName = RabbitMQConfig.MAIN_EXCHANGE; // AÑADIDO
    }

    @Override
    @Transactional
    @RabbitListener(queues = RabbitMQConfig.MAIN_QUEUE) // MODIFICADO: Apuntar directamente a la constante de la cola principal
    public void uploadInvoiceToS3(String invoiceId) throws IOException {
        // Lógica para simular un error para pruebas de la Cola de Carta Muerta (DLQ)
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.getCustomerId().startsWith("error-dlq")) {
            System.out.println("Simulando error para ID: " + invoiceId + ". Este mensaje irá a la DLQ.");
            throw new RuntimeException("Error simulado para pruebas de DLQ.");
        }
        
        Path efsPath = Paths.get(efsMountPath, invoice.getId() + ".pdf");
        Files.createDirectories(efsPath.getParent());

        // --- INICIO DE LA MODIFICACIÓN CON ITEXT ---
        PdfWriter writer = new PdfWriter(new FileOutputStream(efsPath.toFile()));
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // Añade el contenido al PDF
        document.add(new Paragraph(invoice.getContent()));

        document.close();
        // --- FIN DE LA MODIFICACIÓN CON ITEXT ---

        // Verificación para evitar la subida duplicada a S3 si ya fue subida
        if (invoice.isUploadedToS3()) {
            System.out.println("Factura " + invoiceId + " ya ha sido subida a S3. Saltando procesamiento.");
            
        }

        String s3Key = String.format("%s/%s/%s",
                invoice.getCustomerId(),
                invoice.getCreationDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                invoice.getId() + ".pdf");

        PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(s3Key).build();
        s3Client.putObject(request, RequestBody.fromFile(Paths.get(invoice.getLocalEfsPath())));

        invoice.setS3Key(s3Key);
        invoice.setUploadedToS3(true);
        Files.delete(Paths.get(invoice.getLocalEfsPath()));
        invoice.setLocalEfsPath(null);
        invoiceRepository.save(invoice);
        System.out.println("Factura " + invoiceId + " subida a S3 correctamente.");
        
    }

    @Override
    public Invoice findInvoiceById(String invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Factura no encontrada con ID: " + invoiceId));
    }

}