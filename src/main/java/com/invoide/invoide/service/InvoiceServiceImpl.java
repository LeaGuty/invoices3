package com.invoide.invoide.service;

import com.invoide.invoide.model.Invoice;
import com.invoide.invoide.repository.InvoiceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

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
    private final String invoiceUploadQueueName;

    public InvoiceServiceImpl(S3Client s3Client, InvoiceRepository invoiceRepository,
                              @Value("${efs.mount.path}") String efsMountPath,
                              @Value("${s3.bucket.name}") String bucketName,
                              RabbitTemplate rabbitTemplate,
                              @Value("${invoide.rabbitmq.queue-name}") String invoiceUploadQueueName) {
        this.s3Client = s3Client;
        this.invoiceRepository = invoiceRepository;
        this.efsMountPath = efsMountPath;
        this.bucketName = bucketName;
        this.rabbitTemplate = rabbitTemplate;
        this.invoiceUploadQueueName = invoiceUploadQueueName;
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
        Files.write(efsPath, content.getBytes());

        savedInvoice.setLocalEfsPath(efsPath.toString());
        savedInvoice.setUploadedToS3(false);

        Invoice finalInvoice = invoiceRepository.save(savedInvoice);

        rabbitTemplate.convertAndSend(invoiceUploadQueueName, finalInvoice.getId());

        return finalInvoice;
    }
    
    @Override
    @Transactional
    @RabbitListener(queues = "${invoide.rabbitmq.queue-name}")
    public Invoice uploadInvoiceToS3(String invoiceId) throws IOException {
        if (invoiceId.startsWith("test-dlq-")) {
            System.out.println("Simulando error para ID: " + invoiceId + ". Este mensaje irá a la DLQ.");
            throw new RuntimeException("Error simulado para pruebas de DLQ.");
        }
        Invoice invoice = findInvoiceById(invoiceId);
        
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
        Invoice invoice = findInvoiceById(invoiceId);
        if (newCustomerId != null && !newCustomerId.isEmpty()) {
            invoice.setCustomerId(newCustomerId);
        }
        return invoiceRepository.save(invoice);
    }
}
