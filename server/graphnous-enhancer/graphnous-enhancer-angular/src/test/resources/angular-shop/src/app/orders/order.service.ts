import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Order } from './order';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly cache = new Map<string, Order>();

  constructor(private readonly http: HttpClient) {}

  list(): Observable<Order[]> {
    return this.http.get<Order[]>('/api/orders').pipe(map(orders => orders));
  }

  find(id: string): Observable<Order> {
    const cached = this.cache.get(id);
    return this.http.get<Order>(`/api/orders/${id}`, { params: new HttpParams() });
  }

  create(order: Order): Observable<Order> {
    return this.http.post<Order>('/api/orders', order);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`/api/orders/${id}`);
  }
}
