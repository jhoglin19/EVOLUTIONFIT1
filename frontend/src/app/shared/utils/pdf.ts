/** Descarga un PDF recibido como blob (la descarga va con el token de sesión). */
export function downloadPdf(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Abre un PDF en otra pestaña (vista previa); si el navegador lo bloquea, lo descarga. */
export function openPdf(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const win = window.open(url, '_blank');
  if (!win) {
    downloadPdf(blob, fileName);
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
