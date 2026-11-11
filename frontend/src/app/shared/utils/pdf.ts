/**
 * Muestra un PDF recibido como blob (la descarga va con el token de sesión, por eso no se abre la URL
 * directamente). Si el navegador bloquea la ventana nueva, lo descarga.
 */
export function openPdf(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const win = window.open(url, '_blank');
  if (!win) {
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
