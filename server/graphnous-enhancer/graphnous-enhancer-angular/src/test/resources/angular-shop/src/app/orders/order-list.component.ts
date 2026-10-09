import { Component, OnInit, inject } from '@angular/core';
import { OrderService } from './order.service';
import { InvoiceService } from './invoice.service';
import { HighlightDirective } from '../shared/highlight.directive';
import { Order } from './order';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [HighlightDirective],
  templateUrl: './order-list.component.html',
  styleUrls: ['./order-list.component.css']
})
export class OrderListComponent implements OnInit {
  orders: Order[] = [];
  private readonly invoices = inject(InvoiceService);

  constructor(private readonly orderService: OrderService) {}

  ngOnInit(): void {
    this.orderService.list().subscribe(orders => (this.orders = orders));
  }
}
