import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, SubscriptionResponse } from '../../../core/models/api.models';

@Component({
  selector: 'app-subscription-management',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './subscription-management.component.html',
  styleUrl: './subscription-management.component.scss'
})
export class SubscriptionManagementComponent implements OnInit {
  subscriptions: SubscriptionResponse[] = [];
  loading = true;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchSubscriptions();
  }

  fetchSubscriptions(): void {
    this.loading = true;
    this.http.get<ApiResponse<SubscriptionResponse[]>>('/api/platform/subscriptions')
      .subscribe({
        next: (res) => {
          if (res.success && res.data) this.subscriptions = res.data;
          this.loading = false;
        },
        error: () => this.loading = false
      });
  }
}
