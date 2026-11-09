import { Component, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthService } from '../../core/services/auth.service';
import { roleLabel } from '../../shared/utils/role-label';

@Component({
  selector: 'app-header',
  imports: [MatToolbarModule, MatButtonModule, MatIconModule, MatMenuModule, MatDividerModule],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  private readonly auth = inject(AuthService);

  readonly showMenuButton = input(false);
  readonly menuToggle = output<void>();

  readonly user = this.auth.user;
  protected readonly roleLabel = roleLabel;

  logout(): void {
    this.auth.logout();
  }
}
