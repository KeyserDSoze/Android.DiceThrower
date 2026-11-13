import QRCode from 'qrcode';

const MARGIN = 4; // quiet zone, in modules
const MIN_SIZE = 260; // CSS px; short links keep the familiar size
const PADDING = 8; // white padding of .workshop-share img (border-box)
export const MIN_MODULE_PX = 3;

/**
 * Vector (SVG) QR code for a share link: crisp at any zoom or device pixel
 * ratio. Uses error correction M when the link fits, otherwise L (required
 * near the 2600-char link bound), and an integer scale of at least 3 CSS px
 * per module. `size` is the <img> width/height including the CSS padding.
 */
export async function renderShareQr(link) {
  let errorCorrectionLevel = 'M';
  let symbol;
  try { symbol = QRCode.create(link, { errorCorrectionLevel }); }
  catch { errorCorrectionLevel = 'L'; symbol = QRCode.create(link, { errorCorrectionLevel }); }
  const svg = await QRCode.toString(link, { type: 'svg', errorCorrectionLevel, margin: MARGIN });
  const modules = symbol.modules.size + MARGIN * 2;
  const scale = Math.max(MIN_MODULE_PX, Math.ceil((MIN_SIZE - PADDING * 2) / modules));
  return {
    src: 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg),
    size: modules * scale + PADDING * 2,
    errorCorrectionLevel, version: symbol.version, modules, scale,
  };
}
