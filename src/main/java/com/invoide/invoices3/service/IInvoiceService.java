package com.invoide.invoices3.service;


import java.io.IOException;


import com.invoide.invoices3.model.Invoice;

public interface IInvoiceService {
      
    
    void uploadInvoiceToS3(String invoiceId) throws IOException;
    
        Invoice findInvoiceById(String invoiceId);
    
}


