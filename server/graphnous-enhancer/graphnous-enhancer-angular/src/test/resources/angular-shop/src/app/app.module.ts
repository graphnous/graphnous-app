import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { AppComponent } from './app.component';
import { SharedModule } from './shared/shared.module';
import { OrderListComponent } from './orders/order-list.component';
import { OrderService } from './orders/order.service';
import { InvoiceService } from './orders/invoice.service';

@NgModule({
  declarations: [AppComponent],
  imports: [BrowserModule, HttpClientModule, RouterModule.forRoot([]), SharedModule, OrderListComponent],
  providers: [OrderService, { provide: InvoiceService, useClass: InvoiceService }],
  bootstrap: [AppComponent]
})
export class AppModule {}
