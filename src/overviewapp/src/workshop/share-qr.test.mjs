import test from 'node:test';
import assert from 'node:assert/strict';
import { MAX_LINK_LENGTH, instantiateTemplate, shareUrl } from './character.js';
import { MIN_MODULE_PX, renderShareQr } from './share-qr.js';

const base = 'https://keyserdsoze.github.io/Android.DiceThrower/workshop/#character=v1.';
function link(length) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  let text = base;
  for (let i = 0; text.length < length; i++) text += chars[(i * 37) % chars.length];
  return text;
}
const svgOf = qr => decodeURIComponent(qr.src.slice('data:image/svg+xml;charset=utf-8,'.length));

test('share QR is a crisp SVG drawn at an integer scale of at least 3 CSS px per module', async () => {
  for (const length of [120, 700, 1500, 2331, 2400, MAX_LINK_LENGTH]) {
    const qr = await renderShareQr(link(length));
    assert.ok(qr.scale >= MIN_MODULE_PX && Number.isInteger(qr.scale), 'scale ' + qr.scale);
    assert.equal(qr.size, qr.modules * qr.scale + 16, 'size includes the 8px CSS padding');
    assert.ok(qr.size >= 260, 'never smaller than the previous 260px display');
    const svg = svgOf(qr);
    assert.match(svg, /^<svg xmlns="http:\/\/www.w3.org\/2000\/svg" viewBox="0 0 (\d+) \1" shape-rendering="crispEdges">/);
    assert.equal(Number(svg.match(/viewBox="0 0 (\d+)/)[1]), qr.modules);
    // Error correction M whenever the link fits it (2331 bytes), L for longer links.
    assert.equal(qr.errorCorrectionLevel, length <= 2331 ? 'M' : 'L', 'length ' + length);
  }
});

test('starter template links render as share QR codes', async () => {
  const fighter = instantiateTemplate(0);
  const url = shareUrl(fighter, 'https://keyserdsoze.github.io/Android.DiceThrower/workshop/');
  const qr = await renderShareQr(url);
  assert.equal(qr.errorCorrectionLevel, 'M');
  assert.ok(qr.scale >= 4, 'short template links get larger modules');
});
