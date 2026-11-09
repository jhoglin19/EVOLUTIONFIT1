const LABELS: Record<string, string> = {
  ADMIN: 'Administrador',
  RECEPTIONIST: 'Recepcionista',
};

/** Nombre legible del rol para mostrar en pantalla. */
export function roleLabel(role: string | null | undefined): string {
  if (!role) {
    return '';
  }
  return LABELS[role.toUpperCase()] ?? role;
}
