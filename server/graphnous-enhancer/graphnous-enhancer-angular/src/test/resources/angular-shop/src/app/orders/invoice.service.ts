import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable()
export class InvoiceService {
  private http = inject(HttpClient);
  private client: HttpClient = inject(HttpClient);

  download(id: string) {
    return this.client.request('GET', `/api/invoices/${id}`);
  }

  refresh() {
    return this.http.put('/api/invoices/refresh', {});
  }
}
