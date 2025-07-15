package com.invoide.invoide.service;

import com.invoide.invoide.model.Invoice;
import com.invoide.invoide.repository.InvoiceRepository;
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

import com.invoide.invoide.config.RabbitMQConfig; // AÑADIDO: Importar la clase de configuración de RabbitMQ

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
    public Invoice createInvoice(String customerId, String content) throws IOException {
        Invoice invoice = new Invoice();
        invoice.setCustomerId(customerId);
        invoice.setCreationDate(LocalDate.now());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        Path efsPath = Paths.get(efsMountPath, savedInvoice.getId() + ".pdf");
        Files.createDirectories(efsPath.getParent());

        // --- INICIO DE LA MODIFICACIÓN CON ITEXT ---
        PdfWriter writer = new PdfWriter(new FileOutputStream(efsPath.toFile()));
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // Añade el contenido al PDF
        document.add(new Paragraph(content));

        document.close();
        // --- FIN DE LA MODIFICACIÓN CON ITEXT ---

        savedInvoice.setLocalEfsPath(efsPath.toString());
        savedInvoice.setUploadedToS3(false);

        Invoice finalInvoice = invoiceRepository.save(savedInvoice);

        rabbitTemplate.convertAndSend(this.mainExchangeName, "", finalInvoice.getId());

        return finalInvoice;
    }

    @Override
    @Transactional
    @RabbitListener(queues = RabbitMQConfig.MAIN_QUEUE) // MODIFICADO: Apuntar directamente a la constante de la cola principal
    public Invoice uploadInvoiceToS3(String invoiceId) throws IOException {
        // Lógica para simular un error para pruebas de la Cola de Carta Muerta (DLQ)
        if (invoiceId.startsWith("test-dlq-")) {
            System.out.println("Simulando error para ID: " + invoiceId + ". Este mensaje irá a la DLQ.");
            throw new RuntimeException("Error simulado para pruebas de DLQ.");
        }

        Invoice invoice = findInvoiceById(invoiceId);

        // Verificación para evitar la subida duplicada a S3 si ya fue subida
        if (invoice.isUploadedToS3()) {
            System.out.println("Factura " + invoiceId + " ya ha sido subida a S3. Saltando procesamiento.");
            return invoice;
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

        System.out.println("Factura " + invoiceId + " subida a S3 correctamente.");
        return invoiceRepository.save(invoice);
    }

    @Override
    public byte[] downloadInvoice(String invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (!invoice.isUploadedToS3()) {
            throw new IllegalStateException("La factura debe ser subida a S3 antes de poder descargarla.");
        }
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(invoice.getS3Key()).build();
        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(request);
        return responseBytes.asByteArray();
    }

    @Override
    @Transactional
    public void deleteInvoice(String invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.isUploadedToS3()) {
            DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucketName).key(invoice.getS3Key()).build();
            s3Client.deleteObject(request);
        }
        invoiceRepository.delete(invoice);
    }

    @Override
    public List<Invoice> getCustomerInvoiceHistory(String customerId) {
        return invoiceRepository.findByCustomerId(customerId);
    }

    @Override
    public Invoice findInvoiceById(String invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Factura no encontrada con ID: " + invoiceId));
    }

    @Override
    @Transactional
    public Invoice updateInvoice(String invoiceId, String newCustomerId) {
        // 1. Validar que el nuevo ID de cliente no sea nulo o vacío
        if (newCustomerId == null || newCustomerId.isEmpty()) {
            throw new IllegalArgumentException("El nuevo ID de cliente no puede ser nulo o vacío.");
        }

        // 2. Encontrar la factura existente
        Invoice invoice = findInvoiceById(invoiceId);
        String oldCustomerId = invoice.getCustomerId();

        // 3. Si el ID del cliente no ha cambiado, no hacer nada
        if (newCustomerId.equals(oldCustomerId)) {
            return invoice;
        }

        // 4. Si la factura ya fue subida a S3, mover el archivo
        if (invoice.isUploadedToS3()) {
            // 4.1. Construir la nueva clave S3 con el nuevo ID de cliente
            String newS3Key = String.format("%s/%s/%s",
                    newCustomerId,
                    invoice.getCreationDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                    invoice.getId() + ".pdf");

            // 4.2. Copiar el objeto a la nueva ubicación
            CopyObjectRequest copyReq = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(invoice.getS3Key())
                    .destinationBucket(bucketName)
                    .destinationKey(newS3Key)
                    .build();
            s3Client.copyObject(copyReq);

            // 4.3. Eliminar el objeto de la ubicación antigua
            DeleteObjectRequest deleteReq = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(invoice.getS3Key())
                    .build();
            s3Client.deleteObject(deleteReq);

            // 4.4. Actualizar la clave S3 en el objeto de la factura
            invoice.setS3Key(newS3Key);
        }

        // 5. Actualizar el ID del cliente y guardar los cambios en la base de datos
        invoice.setCustomerId(newCustomerId);
        return invoiceRepository.save(invoice);
    }
}