import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { roleLabel } from '../../shared/utils/role-label';

@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatIconModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly auth = inject(AuthService);

  readonly user = this.auth.user;
  readonly isAdmin = this.auth.isAdmin;
  protected readonly roleLabel = roleLabel;
}
