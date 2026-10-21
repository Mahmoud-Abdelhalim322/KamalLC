import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ApiResponse, LoanMembersSummary, LoanPaymentRequest, LoanRequest, LoanResponse, MemberLoanSummaryResponse, SummaryPage
} from '../models/api.models';
import { PAGE_SIZE, toHttpParams, unwrap } from './api.util';

@Injectable({ providedIn: 'root' })
export class LoanService {

  private http = inject(HttpClient);
  private readonly base = '/api/loans';

  /** One server page of members with loan summaries, plus totals over every match. */
  getMembers(filter: { type?: string; search?: string }, page = 0, size = PAGE_SIZE)
      : Observable<SummaryPage<MemberLoanSummaryResponse, LoanMembersSummary>> {
    const params = toHttpParams({ ...filter, page, size });
    return this.http
      .get<ApiResponse<SummaryPage<MemberLoanSummaryResponse, LoanMembersSummary>>>(`${this.base}/members`, { params })
      .pipe(map(unwrap));
  }

  getMemberLoans(memberId: string, memberType: string): Observable<LoanResponse[]> {
    return this.http
      .get<ApiResponse<LoanResponse[]>>(`${this.base}/members/${memberId}`, { params: { memberType } })
      .pipe(map(res => res.data ?? []));
  }

  create(request: LoanRequest): Observable<LoanResponse> {
    return this.http
      .post<ApiResponse<LoanResponse>>(this.base, request)
      .pipe(map(unwrap));
  }

  addPayment(loanId: string, request: LoanPaymentRequest): Observable<LoanResponse> {
    return this.http
      .post<ApiResponse<LoanResponse>>(`${this.base}/${loanId}/payments`, request)
      .pipe(map(unwrap));
  }

  updateDeduction(loanId: string, monthlyDeduction: number): Observable<LoanResponse> {
    return this.http
      .patch<ApiResponse<LoanResponse>>(`${this.base}/${loanId}/deduction`, { monthlyDeduction })
      .pipe(map(unwrap));
  }

  cancel(loanId: string): Observable<LoanResponse> {
    return this.http
      .patch<ApiResponse<LoanResponse>>(`${this.base}/${loanId}/cancel`, {})
      .pipe(map(unwrap));
  }
}
