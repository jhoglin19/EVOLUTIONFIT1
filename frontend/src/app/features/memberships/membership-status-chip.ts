import { Component, computed, input } from '@angular/core';

import { MEMBERSHIP_STATUS_LABELS, MembershipStatus } from '../../shared/models/membership.model';

@Component({
  selector: 'app-membership-status-chip',
  template: `<span class="chip" [class]="'chip ' + status().toLowerCase()">{{ label() }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 0.78rem;
      white-space: nowrap;
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
    }
    .active {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .frozen,
    .scheduled {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .pending {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    .expired,
    .cancelled {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
  `,
})
export class MembershipStatusChip {
  readonly status = input.required<MembershipStatus>();
  readonly label = computed(() => MEMBERSHIP_STATUS_LABELS[this.status()]);
}
