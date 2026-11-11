import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '../../core/config/api.config';
import { ReceiptSettings, ReceiptSettingsRequest } from '../../shared/models/receipt-settings.model';

@Injectable({ providedIn: 'root' })
export class ReceiptSettingsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/settings/receipt`;

  get(): Observable<ReceiptSettings> {
    return this.http.get<ReceiptSettings>(this.url);
  }

  update(request: ReceiptSettingsRequest): Observable<ReceiptSettings> {
    return this.http.put<ReceiptSettings>(this.url, request);
  }

  uploadLogo(file: File): Observable<ReceiptSettings> {
    const body = new FormData();
    body.append('file', file);
    return this.http.put<ReceiptSettings>(`${this.url}/logo`, body);
  }

  deleteLogo(): Observable<ReceiptSettings> {
    return this.http.delete<ReceiptSettings>(`${this.url}/logo`);
  }

  logo(): Observable<Blob> {
    return this.http.get(`${this.url}/logo`, { responseType: 'blob' });
  }

  preview(): Observable<Blob> {
    return this.http.get(`${this.url}/preview.pdf`, { responseType: 'blob' });
  }
}
