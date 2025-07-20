package com.invoide.invoices3.service;


import java.io.IOException;
import java.util.List;

import com.invoide.invoices3.model.Invoice;

public interface IInvoiceService {
    
    Invoice createInvoice(String customerId, String content) throws IOException;
    
    void uploadInvoiceToS3(String invoiceId) throws IOException;
    
    byte[] downloadInvoice(String invoiceId);
    
    void deleteInvoice(String invoiceId);
    
    List<Invoice> getCustomerInvoiceHistory(String customerId);

    Invoice findInvoiceById(String invoiceId);

    Invoice updateInvoice(String invoiceId, String newCustomerId);
}


