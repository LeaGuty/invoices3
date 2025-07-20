package com.invoide.invoide.service;


import com.invoide.invoide.model.Invoice;
import java.io.IOException;
import java.util.List;

public interface IInvoiceService {
    
    Invoice createInvoice(String customerId, String content) throws IOException;
    
    void uploadInvoiceToS3(String invoiceId) throws IOException;
    
    byte[] downloadInvoice(String invoiceId);
    
    void deleteInvoice(String invoiceId);
    
    List<Invoice> getCustomerInvoiceHistory(String customerId);

    Invoice findInvoiceById(String invoiceId);

    Invoice updateInvoice(String invoiceId, String newCustomerId);
}


