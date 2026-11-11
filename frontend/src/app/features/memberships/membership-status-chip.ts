import { Component, computed, input } from '@angular/core';

import { MEMBERSHIP_STATUS_LABELS, MembershipStatus } from '../../shared/models/membership.model';

/** Estado de una membresía. El color sale del estilo global .chip (src/styles/_components.scss). */
@Component({
  selector: 'app-membership-status-chip',
  template: `<span class="chip" [class]="'chip ' + status().toLowerCase()">{{ label() }}</span>`,
})
export class MembershipStatusChip {
  readonly status = input.required<MembershipStatus>();
  readonly label = computed(() => MEMBERSHIP_STATUS_LABELS[this.status()]);
}
