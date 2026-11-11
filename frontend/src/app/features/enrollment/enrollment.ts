import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, catchError, debounceTime, finalize, forkJoin, merge, switchMap, tap } from 'rxjs';

import { BusinessInfoService } from '../../core/services/business-info.service';
import { AuthService } from '../../core/services/auth.service';
import { errorMessage } from '../../core/utils/error-message';
import { Client, DOCUMENT_TYPES } from '../../shared/models/client.model';
import { Membership, MembershipPreview, PaymentPreview } from '../../shared/models/membership.model';
import {
  PAYMENT_METHODS,
  Payment,
  PaymentMethod,
  PaymentRequest,
  REFERENCE_REQUIRED,
  methodLabel,
} from '../../shared/models/payment.model';
import { Plan } from '../../shared/models/plan.model';
import { planEndDate, todayInGym } from '../../shared/utils/dates';
import { downloadPdf } from '../../shared/utils/pdf';
import { receiptWhatsappUrl } from '../../shared/utils/whatsapp';
import { MOBILE_ERROR, colombianMobileValidator, normalizeColombianMobile } from '../../shared/utils/phone';
import { newRequestId } from '../../shared/utils/request-id';
import { ClientsService } from '../clients/clients.service';
import { MembershipsService } from '../memberships/memberships.service';
import { PaymentsService } from '../payments/payments.service';
import { PlansService } from '../plans/plans.service';

/** enroll: cliente nuevo o existente. renew: cliente fijo (desde la ficha). pay: cobrar una pendiente. */
export type EnrollmentMode = 'enroll' | 'renew' | 'pay';

const normalizeDocument = (value: string) => value.replace(/[\s.-]/g, '').toUpperCase();

/**
 * Asistente de inscripción: cliente, plan, pago y confirmación. El cobro se envía en una sola solicitud
 * con un identificador que se conserva en los reintentos: un doble clic o un reintento no cobra dos veces.
 */
@Component({
  selector: 'app-enrollment',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatStepperModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './enrollment.html',
  styleUrl: './enrollment.scss',
})
export class Enrollment {
  readonly mobileError = MOBILE_ERROR;
  private readonly route = inject(ActivatedRoute);
  private readonly clientsApi = inject(ClientsService);
  private readonly plansApi = inject(PlansService);
  private readonly membershipsApi = inject(MembershipsService);
  private readonly paymentsApi = inject(PaymentsService);
  private readonly auth = inject(AuthService);
  private readonly business = inject(BusinessInfoService);

  readonly documentTypes = DOCUMENT_TYPES;
  readonly methods = PAYMENT_METHODS;
  readonly today = todayInGym();
  /** Solo comodidad visual: el backend rechaza descuentos que no vengan de un ADMIN. */
  readonly isAdmin = this.auth.hasRole('ADMIN');

  readonly mode = signal<EnrollmentMode>('enroll');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  // Paso 1: cliente
  readonly client = signal<Client | null>(null);
  readonly searched = signal(false);
  readonly search = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  /** El formulario necesita su [formGroup]: sin él, Angular no maneja el envío y el navegador recarga la página. */
  readonly searchForm = new FormGroup({ document: this.search });
  readonly clientForm = new FormGroup({
    documentType: new FormControl('CC', { nonNullable: true, validators: [Validators.required] }),
    documentNumber: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(30)],
    }),
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, colombianMobileValidator],
    }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(150)] }),
  });

  // Paso 2: plan
  readonly plans = signal<Plan[]>([]);
  readonly planForm = new FormGroup({
    planId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    startDate: new FormControl('', { nonNullable: true }),
  });
  readonly salePreview = signal<MembershipPreview | null>(null);
  readonly salePreviewError = signal<string | null>(null);

  // Modo cobro de una pendiente
  readonly pending = signal<Membership | null>(null);
  readonly paymentPreview = signal<PaymentPreview | null>(null);

  // Paso 3: pago
  readonly paymentForm = new FormGroup({
    method: new FormControl<PaymentMethod>('CASH', { nonNullable: true, validators: [Validators.required] }),
    reference: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    notes: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(255)] }),
    discountAmount: new FormControl<number | null>(null, {
      validators: [Validators.min(0), Validators.pattern(/^\d{1,10}(\.\d{1,2})?$/)],
    }),
    discountReason: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(255)] }),
  });

  // Confirmación
  readonly saving = signal(false);
  readonly result = signal<Payment | null>(null);
  /** Se conserva en los reintentos y se renueva solo al empezar otra inscripción. */
  private requestId = newRequestId();

  private readonly method = toSignal(this.paymentForm.controls.method.valueChanges, {
    initialValue: this.paymentForm.controls.method.value,
  });
  private readonly discountValue = toSignal(this.paymentForm.controls.discountAmount.valueChanges, {
    initialValue: null,
  });
  private readonly planId = toSignal(this.planForm.controls.planId.valueChanges, { initialValue: null });
  private readonly startDate = toSignal(this.planForm.controls.startDate.valueChanges, { initialValue: '' });

  readonly referenceRequired = computed(() => REFERENCE_REQUIRED.includes(this.method()));
  readonly selectedPlan = computed(() => this.plans().find((p) => p.id === this.planId()) ?? null);

  readonly listPrice = computed<number | null>(() => {
    if (this.mode() === 'pay') {
      return this.paymentPreview()?.listPrice ?? null;
    }
    return this.salePreview()?.price ?? this.selectedPlan()?.price ?? null;
  });
  readonly discount = computed(() => (this.isAdmin ? Number(this.discountValue() || 0) : 0));
  readonly total = computed(() => {
    const price = this.listPrice();
    return price === null ? null : Math.round((price - this.discount()) * 100) / 100;
  });

  /** Fechas que tendrá la membresía. Para un cliente nuevo no hay vigente: se calculan aquí. */
  readonly dates = computed<{ start: string; end: string; scheduled: boolean } | null>(() => {
    if (this.mode() === 'pay') {
      const p = this.paymentPreview();
      return p ? { start: p.startDate, end: p.endDate, scheduled: p.scheduled } : null;
    }
    const sale = this.salePreview();
    if (sale) {
      return { start: sale.startDate, end: sale.endDate, scheduled: sale.scheduled };
    }
    const plan = this.selectedPlan();
    if (!plan || this.client()) {
      return null;
    }
    const start = this.startDate() || this.today;
    return { start, end: planEndDate(start, plan.durationDays), scheduled: start > this.today };
  });

  constructor() {
    this.business.load();
    this.plansApi.list('', true, 0, 100).subscribe({
      next: (page) => this.plans.set(page.content),
      error: (err) => this.error.set(errorMessage(err)),
    });

    this.paymentForm.controls.method.valueChanges.pipe(takeUntilDestroyed()).subscribe((method) => {
      const reference = this.paymentForm.controls.reference;
      reference.setValidators(
        REFERENCE_REQUIRED.includes(method)
          ? [Validators.required, Validators.maxLength(120), Validators.pattern(/\S/)]
          : [Validators.maxLength(120)],
      );
      reference.updateValueAndValidity();
    });

    // Vista previa del backend (solo con un cliente existente: conoce su membresía vigente).
    merge(this.planForm.controls.planId.valueChanges, this.planForm.controls.startDate.valueChanges)
      .pipe(
        debounceTime(250),
        tap(() => {
          this.salePreview.set(null);
          this.salePreviewError.set(null);
        }),
        switchMap(() => {
          const client = this.client();
          const planId = this.planForm.controls.planId.value;
          if (!client || !planId) {
            return EMPTY;
          }
          return this.membershipsApi.preview(client.id, planId, this.planForm.controls.startDate.value || null).pipe(
            catchError((err) => {
              this.salePreviewError.set(errorMessage(err));
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((preview) => this.salePreview.set(preview));

    this.init();
  }

  private init(): void {
    const params = this.route.snapshot.queryParamMap;
    const membershipId = Number(params.get('membershipId'));
    const clientId = Number(params.get('clientId'));
    if (membershipId) {
      this.mode.set('pay');
      this.loading.set(true);
      forkJoin({
        membership: this.membershipsApi.get(membershipId),
        preview: this.membershipsApi.paymentPreview(membershipId),
      })
        .pipe(
          switchMap(({ membership, preview }) => {
            this.pending.set(membership);
            this.paymentPreview.set(preview);
            return this.clientsApi.get(membership.clientId);
          }),
          finalize(() => this.loading.set(false)),
        )
        .subscribe({ next: (c) => this.client.set(c), error: (err) => this.error.set(errorMessage(err)) });
    } else if (clientId) {
      this.mode.set('renew');
      this.loading.set(true);
      this.clientsApi
        .get(clientId)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({ next: (c) => this.useClient(c), error: (err) => this.error.set(errorMessage(err)) });
    }
  }

  // ---- paso 1 ----

  /** Busca por documento para no registrar dos veces a la misma persona. */
  findClient(): void {
    const document = normalizeDocument(this.search.value);
    if (!document) {
      this.search.markAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.clientsApi
      .list(document, null, 0, 10)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => {
          const found = page.content.find((c) => normalizeDocument(c.documentNumber) === document) ?? null;
          this.searched.set(true);
          if (found) {
            this.useClient(found);
          } else {
            this.client.set(null);
            this.clientForm.reset({ documentType: 'CC', documentNumber: document });
          }
        },
        error: (err) => this.error.set(errorMessage(err)),
      });
  }

  /** Enter en el campo de documento busca (y no envía el formulario de forma nativa). */
  searchOnEnter(event: Event): void {
    event.preventDefault();
    this.findClient();
  }

  useClient(client: Client): void {
    this.client.set(client);
    this.error.set(client.active ? null : 'El cliente está inactivo. Actívalo en Clientes antes de inscribirlo.');
  }

  clearClient(): void {
    this.client.set(null);
    this.searched.set(false);
    this.search.reset('');
  }

  clientStepDone(): boolean {
    const c = this.client();
    return c ? c.active : this.searched() && this.clientForm.valid;
  }

  next(stepper: MatStepper, valid: boolean, form?: FormGroup): void {
    if (!valid) {
      form?.markAllAsTouched();
      return;
    }
    stepper.next();
  }

  // ---- paso 3 ----

  paymentStepDone(): boolean {
    return this.paymentForm.valid && this.discountProblem() === null;
  }

  /** Reglas del descuento que dependen del precio. El backend las vuelve a validar. */
  discountProblem(): string | null {
    const discount = this.discount();
    if (discount <= 0) {
      return null;
    }
    if (!this.paymentForm.controls.discountReason.value.trim()) {
      return 'El motivo del descuento es obligatorio';
    }
    const price = this.listPrice();
    if (price !== null && discount > price) {
      return 'El descuento no puede superar el valor del plan';
    }
    return null;
  }

  methodName(method: PaymentMethod): string {
    return methodLabel(method);
  }

  clientName(): string {
    const c = this.client();
    if (c) {
      return `${c.firstName} ${c.lastName} · ${c.documentType} ${c.documentNumber}`;
    }
    const f = this.clientForm.getRawValue();
    return `${f.firstName} ${f.lastName} · ${f.documentType} ${f.documentNumber} (nuevo)`;
  }

  planName(): string {
    return this.mode() === 'pay' ? (this.pending()?.planName ?? '') : (this.selectedPlan()?.name ?? '');
  }

  // ---- confirmar ----

  confirm(): void {
    if (this.saving() || this.result()) {
      return; // doble clic
    }
    const total = this.total();
    if (total === null || !this.paymentStepDone()) {
      this.paymentForm.markAllAsTouched();
      return;
    }
    const v = this.paymentForm.getRawValue();
    const discount = this.discount();
    const payment: PaymentRequest = {
      requestId: this.requestId,
      method: v.method,
      reference: v.reference.trim() || null,
      notes: v.notes.trim() || null,
      expectedAmount: total,
      discountAmount: discount > 0 ? discount : null,
      discountReason: discount > 0 ? v.discountReason.trim() : null,
    };

    this.saving.set(true);
    this.error.set(null);
    const request$ =
      this.mode() === 'pay'
        ? this.paymentsApi.pay(this.pending()!.id, payment)
        : this.paymentsApi.enroll({
            clientId: this.client()?.id ?? null,
            client: this.client() ? null : this.newClientRequest(),
            planId: this.planForm.controls.planId.value!,
            startDate: this.planForm.controls.startDate.value || null,
            notes: null,
            payment,
          });
    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (paid) => this.result.set(paid),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  /** Enlace wa.me al teléfono del cliente, o null si no tiene teléfono (el botón no se muestra). */
  whatsappUrl(payment: Payment): string | null {
    return receiptWhatsappUrl(payment, this.business.tradeName());
  }

  downloadReceipt(payment: Payment): void {
    this.paymentsApi.receipt(payment.id).subscribe({
      next: (blob) => downloadPdf(blob, `${payment.receiptNumber ?? 'comprobante'}.pdf`),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  /** Empieza otra inscripción desde cero, con un identificador de cobro nuevo. */
  restart(stepper: MatStepper): void {
    this.requestId = newRequestId();
    this.result.set(null);
    this.error.set(null);
    this.mode.set('enroll');
    this.pending.set(null);
    this.paymentPreview.set(null);
    this.clearClient();
    this.clientForm.reset({ documentType: 'CC' });
    this.planForm.reset({ planId: null, startDate: '' });
    this.paymentForm.reset({ method: 'CASH' });
    stepper.reset();
  }

  private newClientRequest() {
    const f = this.clientForm.getRawValue();
    return {
      documentType: f.documentType,
      documentNumber: f.documentNumber.trim(),
      firstName: f.firstName.trim(),
      lastName: f.lastName.trim(),
      email: f.email.trim() || null,
      phone: normalizeColombianMobile(f.phone) ?? f.phone.trim(),
      birthDate: null,
      address: null,
      notes: null,
    };
  }

  /** Para las pruebas: el identificador del cobro en curso. */
  currentRequestId(): string {
    return this.requestId;
  }
}
