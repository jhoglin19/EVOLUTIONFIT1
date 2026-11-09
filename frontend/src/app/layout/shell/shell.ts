import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { Header } from '../header/header';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-shell',
  imports: [MatSidenavModule, RouterOutlet, Header, Sidebar],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  /** En pantallas angostas el menú lateral se oculta y se abre sobre el contenido. */
  readonly mobile = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 900px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  closeOnMobile(drawer: MatSidenav): void {
    if (this.mobile()) {
      void drawer.close();
    }
  }
}
